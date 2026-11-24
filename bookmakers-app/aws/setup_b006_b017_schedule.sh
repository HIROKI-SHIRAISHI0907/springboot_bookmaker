#!/usr/bin/env bash
# =============================================================================
# 2026-11-24 スケジュールの追加・名前の変更（何度流しても同じ結果になる）
#   1) B006（統計CSV取り込み）を毎日 5:00 に自動実行（B011 4:00 のあと）
#   2) B017（取るべきリーグ候補のメール）を毎日 21:00 に自動実行（no-game スクレイパー 19:00 のあと。
#      候補 JSON（league_candidates/pending/*.json）が無ければ Lambda が起動しない）
#   3) noScoreRunEcsTask2200 を noScoreRunEcsTask に名前変更（中身・時刻 19:00 はそのまま）
#
# 使い方（Mac のターミナル。aws CLI のログイン済みが前提）
#   cd <bookmakers-app>/aws
#   CHECK=1 bash setup_b006_b017_schedule.sh   # 確認だけ（何も変えない）
#   bash setup_b006_b017_schedule.sh           # 本番
#   SKIP_B017=1 / SKIP_RENAME=1 で 2) / 3) を飛ばせる。B006_CRON / B017_CRON で時刻を変えられる（JST）
# 前提: setup_b011_schedule.sh を流し済み（Lambda に always_unless_running と bookmakers-app の起動権限がある）
# =============================================================================
set -euo pipefail
REGION="${REGION:-ap-northeast-1}"
CHECK="${CHECK:-0}"
LAMBDA="${LAMBDA:-ecs-precheck-runner-batch}"
B006_SCHEDULE="${B006_SCHEDULE:-statImportRunEcsTask}"
B006_CRON="${B006_CRON:-cron(0 5 * * ? *)}"
B017_SCHEDULE="${B017_SCHEDULE:-leagueCandidateNoticeRunEcsTask}"
B017_CRON="${B017_CRON:-cron(0 21 * * ? *)}"
OLD_NOSCORE="noScoreRunEcsTask2200"
NEW_NOSCORE="noScoreRunEcsTask"
HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "${WORK}"' EXIT
aws_() { aws --region "${REGION}" "$@"; }
say()  { printf '\n\033[1m▶ %s\033[0m\n' "$*"; }
ok()   { printf '   ✅ %s\n' "$*"; }
doit() { if [ "${CHECK}" = "1" ]; then printf '   (CHECK) %s\n' "$*"; else "$@"; fi; }

say "0) ログイン確認"
ACCOUNT="$(aws sts get-caller-identity --query Account --output text)"
ok "アカウント ${ACCOUNT} / リージョン ${REGION} / CHECK=${CHECK}"
LAMBDA_ARN="arn:aws:lambda:${REGION}:${ACCOUNT}:function:${LAMBDA}"

# -----------------------------------------------------------------------------
say "1) Lambda ${LAMBDA}: B006 を足す"
URL="$(aws_ lambda get-function --function-name "${LAMBDA}" --query 'Code.Location' --output text)"
curl -sSf -o "${WORK}/fn.zip" "${URL}"
mkdir -p "${WORK}/fn" && (cd "${WORK}/fn" && unzip -q "${WORK}/fn.zip")
cp "${WORK}/fn/lambda_function.py" "${HERE}/backup_${LAMBDA}_$(date +%Y%m%d%H%M%S).py"
RESULT="$(python3 "${HERE}/patch_precheck_b006.py" "${WORK}/fn/lambda_function.py")"
echo "   ${RESULT}"
if [ "${RESULT}" = "PATCHED" ]; then
  python3 -m py_compile "${WORK}/fn/lambda_function.py"
  (cd "${WORK}/fn" && rm -f ../fn-new.zip && zip -q -r ../fn-new.zip . -x '__pycache__/*')
  doit aws_ lambda update-function-code --function-name "${LAMBDA}" --zip-file "fileb://${WORK}/fn-new.zip" >/dev/null
  [ "${CHECK}" = "1" ] || aws_ lambda wait function-updated --function-name "${LAMBDA}"
  ok "デプロイしました"
fi
if grep -q '"B017": {' "${WORK}/fn/lambda_function.py"; then ok "B017 の起動条件はデプロイ済み"; else echo "   ⚠️ デプロイ済みの Lambda に B017 がありません（B017 のスケジュールを作っても起動しません）"; fi

# -----------------------------------------------------------------------------
say "2) Lambda ${LAMBDA}: 環境変数 JOB_B006_*"
ENV_JSON="$(aws_ lambda get-function-configuration --function-name "${LAMBDA}" --query 'Environment.Variables' --output json)"
NEED="$(python3 - "${ENV_JSON}" "${WORK}/env.json" <<'PY'
import json, sys
env = json.loads(sys.argv[1]) if sys.argv[1] not in ("", "null") else {}
want = {"JOB_B006_TASK_DEFINITION": "bookmakers-app", "JOB_B006_CONTAINER_NAME": "bookmakers-app"}
if all(env.get(k) == v for k, v in want.items()):
    print("NO"); sys.exit()
env.update(want)
json.dump({"Variables": env}, open(sys.argv[2], "w"))
print("YES")
PY
)"
if [ "${NEED}" = "YES" ]; then
  doit aws_ lambda update-function-configuration --function-name "${LAMBDA}" --environment "file://${WORK}/env.json" >/dev/null
  [ "${CHECK}" = "1" ] || aws_ lambda wait function-updated --function-name "${LAMBDA}"
  ok "足しました（他の環境変数はそのまま）"
else
  ok "足してあります"
fi

# -----------------------------------------------------------------------------
# 既存スケジュールから実行ロールを借りる
SCHED_ROLE=""
for n in $(aws_ scheduler list-schedules --query 'Schedules[].Name' --output text); do
  r="$(aws_ scheduler get-schedule --name "${n}" --query "[Target.Arn,Target.RoleArn]" --output text 2>/dev/null || true)"
  if [ "$(echo "${r}" | awk '{print $1}')" = "${LAMBDA_ARN}" ]; then
    SCHED_ROLE="$(echo "${r}" | awk '{print $2}')"; break
  fi
done
[ -n "${SCHED_ROLE}" ] || { echo "❌ ${LAMBDA} を呼んでいるスケジュールが見つからず、実行ロールを決められません"; exit 1; }

put_schedule() {  # 名前 cron BM_JOB
  local name="$1" cron="$2" job="$3"
  python3 - "${WORK}/t-${job}.json" "${LAMBDA_ARN}" "${SCHED_ROLE}" "${job}" <<'PY'
import json, sys
json.dump({"Arn": sys.argv[2], "RoleArn": sys.argv[3], "Input": json.dumps({"BM_JOB": sys.argv[4]}),
           "RetryPolicy": {"MaximumRetryAttempts": 0}}, open(sys.argv[1], "w"))
PY
  local verb=create-schedule
  aws_ scheduler get-schedule --name "${name}" >/dev/null 2>&1 && verb=update-schedule
  doit aws_ scheduler "${verb}" --name "${name}" --schedule-expression "${cron}" \
    --schedule-expression-timezone "Asia/Tokyo" --flexible-time-window Mode=OFF \
    --target "file://${WORK}/t-${job}.json" --state ENABLED >/dev/null
  ok "${name}（${cron} JST・${job}）: ${verb}"
}

say "3) スケジュール ${B006_SCHEDULE}（B006）"
put_schedule "${B006_SCHEDULE}" "${B006_CRON}" "B006"

if [ "${SKIP_B017:-0}" != "1" ]; then
  say "4) スケジュール ${B017_SCHEDULE}（B017）"
  put_schedule "${B017_SCHEDULE}" "${B017_CRON}" "B017"
fi

# -----------------------------------------------------------------------------
if [ "${SKIP_RENAME:-0}" != "1" ]; then
  say "5) ${OLD_NOSCORE} → ${NEW_NOSCORE}（作り直して古いものを消す）"
  if aws_ scheduler get-schedule --name "${NEW_NOSCORE}" >/dev/null 2>&1; then
    ok "${NEW_NOSCORE} はあります"
  elif aws_ scheduler get-schedule --name "${OLD_NOSCORE}" --output json > "${WORK}/old.json" 2>/dev/null; then
    python3 - "${WORK}/old.json" "${WORK}/new-args.json" "${NEW_NOSCORE}" <<'PY'
import json, sys
o = json.load(open(sys.argv[1]))
keep = ["ScheduleExpression", "ScheduleExpressionTimezone", "FlexibleTimeWindow", "Target", "State", "Description", "GroupName"]
n = {k: o[k] for k in keep if k in o and o[k] not in (None, "")}
n["Name"] = sys.argv[3]
json.dump(n, open(sys.argv[2], "w"))
print("   時刻:", o.get("ScheduleExpression"), o.get("ScheduleExpressionTimezone"), "/ 状態:", o.get("State"))
PY
    doit aws_ scheduler create-schedule --cli-input-json "file://${WORK}/new-args.json" >/dev/null
    ok "${NEW_NOSCORE} を作りました（中身は ${OLD_NOSCORE} と同じ）"
    doit aws_ scheduler delete-schedule --name "${OLD_NOSCORE}"
    ok "${OLD_NOSCORE} を消しました"
  else
    echo "   ⚠️ ${OLD_NOSCORE} が見つかりません（名前変更は飛ばします）"
  fi
fi

say "完了。すぐ 1 回動かして確かめるとき:"
echo "   aws lambda invoke --region ${REGION} --function-name ${LAMBDA} --payload '{\"BM_JOB\":\"B006\"}' --cli-binary-format raw-in-base64-out /tmp/b006.json && cat /tmp/b006.json"
