#!/usr/bin/env bash
# =============================================================================
# B095（不要データ削除）を毎日自動で動かす  AWS セットアップ（2026-11-24。何度流しても同じ結果になる）
#
# 使い方（Mac のターミナル。aws CLI のログイン済みが前提）
#   cd <bookmakers-batch>/aws
#   CHECK=1 bash setup_b095_schedule.sh   # 確認だけ（何も変えない）
#   bash setup_b095_schedule.sh           # 本番
#   SCHEDULE_CRON='cron(40 2 * * ? *)' bash setup_b095_schedule.sh   # 時刻を変える（JST）
#
# 作る / 変えるもの
#   1) Lambda ecs-precheck-runner-batch のコードに B095 を足す（デプロイ済みのコードを落として patch_precheck_b095.py を当てる。
#      当ててあれば何もしない。元のコードは backup_ecs-precheck-runner-batch_<日時>.py に残す）
#   2) Lambda の実行ロールに bookmakers-batch の ecs:RunTask・ecs:DescribeTasks/ListTasks・iam:PassRole
#      （ほかの B0xx で付いているはずだが念のため。インラインの上限を超えたら管理ポリシー）
#   3) EventBridge Scheduler deleteDataRunEcsTask（毎日 SCHEDULE_CRON、JST）→ Lambda に {"BM_JOB":"B095"}
#      （実行ロールは、同じ Lambda を呼んでいる既存のスケジュールのものを使う）
# 二重起動: B095 のタスク（BM_JOB=B095）が動いていれば Lambda が起動しない。
# =============================================================================
set -euo pipefail
REGION="${REGION:-ap-northeast-1}"
CHECK="${CHECK:-0}"
LAMBDA="${LAMBDA:-ecs-precheck-runner-batch}"
SCHEDULE_NAME="${SCHEDULE_NAME:-deleteDataRunEcsTask}"
SCHEDULE_CRON="${SCHEDULE_CRON:-cron(20 3 * * ? *)}"
TASK_FAMILY="${TASK_FAMILY:-bookmakers-batch}"
POLICY_NAME="BmPrecheckRunB095"
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

# -----------------------------------------------------------------------------
say "1) Lambda ${LAMBDA}: B095 を足す"
URL="$(aws_ lambda get-function --function-name "${LAMBDA}" --query 'Code.Location' --output text)"
curl -sSf -o "${WORK}/fn.zip" "${URL}"
mkdir -p "${WORK}/fn" && (cd "${WORK}/fn" && unzip -q "${WORK}/fn.zip")
cp "${WORK}/fn/lambda_function.py" "${HERE}/backup_${LAMBDA}_$(date +%Y%m%d%H%M%S).py"
RESULT="$(python3 "${HERE}/patch_precheck_b095.py" "${WORK}/fn/lambda_function.py")"
echo "   ${RESULT}"
if [ "${RESULT}" = "PATCHED" ]; then
  python3 -m py_compile "${WORK}/fn/lambda_function.py"
  (cd "${WORK}/fn" && rm -f ../fn-new.zip && zip -q -r ../fn-new.zip . -x '__pycache__/*')
  doit aws_ lambda update-function-code --function-name "${LAMBDA}" --zip-file "fileb://${WORK}/fn-new.zip" >/dev/null
  [ "${CHECK}" = "1" ] || aws_ lambda wait function-updated --function-name "${LAMBDA}"
  ok "デプロイしました"
fi

# -----------------------------------------------------------------------------
say "2) Lambda の実行ロールに ${TASK_FAMILY} を起動する権限"
ROLE="$(aws_ lambda get-function-configuration --function-name "${LAMBDA}" --query 'Role' --output text)"
ROLE="${ROLE##*/}"
TD="$(aws_ ecs describe-task-definition --task-definition "${TASK_FAMILY}" --query 'taskDefinition.[taskRoleArn,executionRoleArn]' --output text)"
TASK_ROLE="$(echo "${TD}" | awk '{print $1}')"
EXEC_ROLE="$(echo "${TD}" | awk '{print $2}')"
python3 - "${WORK}/policy.json" "${REGION}" "${ACCOUNT}" "${TASK_FAMILY}" "${TASK_ROLE}" "${EXEC_ROLE}" <<'PY'
import json, sys
out, region, account, family, task_role, exec_role = sys.argv[1:]
roles = [r for r in (task_role, exec_role) if r and r != "None"]
json.dump({"Version": "2012-10-17", "Statement": [
    {"Effect": "Allow", "Action": "ecs:RunTask",
     "Resource": [f"arn:aws:ecs:{region}:{account}:task-definition/{family}", f"arn:aws:ecs:{region}:{account}:task-definition/{family}:*"]},
    {"Effect": "Allow", "Action": ["ecs:ListTasks", "ecs:DescribeTasks"], "Resource": "*"},
    {"Effect": "Allow", "Action": "iam:PassRole", "Resource": roles,
     "Condition": {"StringEquals": {"iam:PassedToService": "ecs-tasks.amazonaws.com"}}}]}, open(out, "w"))
PY
if [ "${CHECK}" = "1" ]; then
  printf '   (CHECK) aws iam put-role-policy --role-name %s --policy-name %s\n' "${ROLE}" "${POLICY_NAME}"
elif aws iam put-role-policy --role-name "${ROLE}" --policy-name "${POLICY_NAME}" --policy-document "file://${WORK}/policy.json" 2>"${WORK}/err"; then
  ok "${ROLE} に ${POLICY_NAME}"
elif grep -q LimitExceeded "${WORK}/err"; then
  PARN="arn:aws:iam::${ACCOUNT}:policy/${POLICY_NAME}"
  if aws iam get-policy --policy-arn "${PARN}" >/dev/null 2>&1; then
    for v in $(aws iam list-policy-versions --policy-arn "${PARN}" --query 'Versions[?IsDefaultVersion==`false`].VersionId' --output text); do
      aws iam delete-policy-version --policy-arn "${PARN}" --version-id "${v}"
    done
    aws iam create-policy-version --policy-arn "${PARN}" --policy-document "file://${WORK}/policy.json" --set-as-default >/dev/null
  else
    aws iam create-policy --policy-name "${POLICY_NAME}" --policy-document "file://${WORK}/policy.json" >/dev/null
  fi
  aws iam attach-role-policy --role-name "${ROLE}" --policy-arn "${PARN}"
  ok "${ROLE} に管理ポリシー ${POLICY_NAME}（インラインの上限のため）"
else
  cat "${WORK}/err" >&2; exit 1
fi

# -----------------------------------------------------------------------------
say "3) EventBridge Scheduler ${SCHEDULE_NAME}（${SCHEDULE_CRON} JST）"
LAMBDA_ARN="arn:aws:lambda:${REGION}:${ACCOUNT}:function:${LAMBDA}"
SCHED_ROLE=""
for n in $(aws_ scheduler list-schedules --query 'Schedules[].Name' --output text); do
  [ "${n}" = "${SCHEDULE_NAME}" ] && continue
  r="$(aws_ scheduler get-schedule --name "${n}" --query "[Target.Arn,Target.RoleArn]" --output text 2>/dev/null || true)"
  if [ "$(echo "${r}" | awk '{print $1}')" = "${LAMBDA_ARN}" ]; then
    SCHED_ROLE="$(echo "${r}" | awk '{print $2}')"
    ok "実行ロールは既存のスケジュール ${n} と同じ ${SCHED_ROLE##*/}"
    break
  fi
done
[ -n "${SCHED_ROLE}" ] || { echo "❌ ${LAMBDA} を呼んでいるスケジュールが見つからず、実行ロールを決められません"; exit 1; }
python3 - "${WORK}/target.json" "${LAMBDA_ARN}" "${SCHED_ROLE}" <<'PY'
import json, sys
json.dump({"Arn": sys.argv[2], "RoleArn": sys.argv[3], "Input": json.dumps({"BM_JOB": "B095"}),
           "RetryPolicy": {"MaximumRetryAttempts": 0}}, open(sys.argv[1], "w"))
PY
if aws_ scheduler get-schedule --name "${SCHEDULE_NAME}" >/dev/null 2>&1; then
  doit aws_ scheduler update-schedule --name "${SCHEDULE_NAME}" --schedule-expression "${SCHEDULE_CRON}" \
    --schedule-expression-timezone "Asia/Tokyo" --flexible-time-window Mode=OFF --target "file://${WORK}/target.json" --state ENABLED >/dev/null
  ok "更新しました"
else
  doit aws_ scheduler create-schedule --name "${SCHEDULE_NAME}" --schedule-expression "${SCHEDULE_CRON}" \
    --schedule-expression-timezone "Asia/Tokyo" --flexible-time-window Mode=OFF --target "file://${WORK}/target.json" --state ENABLED >/dev/null
  ok "作りました"
fi

say "完了。すぐ 1 回動かして確かめるとき:"
echo "   aws lambda invoke --region ${REGION} --function-name ${LAMBDA} --payload '{\"BM_JOB\":\"B095\"}' --cli-binary-format raw-in-base64-out /tmp/b095.json && cat /tmp/b095.json"
