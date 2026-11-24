#!/usr/bin/env bash
# ecs-precheck-runner-batch のデプロイ済みコードを落として、パッチ（python）を当ててデプロイする（2026-11-24）
#   bash deploy_precheck_patch.sh patch_precheck_b016_manifest.py        # 本番
#   CHECK=1 bash deploy_precheck_patch.sh patch_precheck_b016_manifest.py # 当てるだけ（デプロイしない）
set -euo pipefail
REGION="${REGION:-ap-northeast-1}"; LAMBDA="${LAMBDA:-ecs-precheck-runner-batch}"; CHECK="${CHECK:-0}"
PATCH="$1"; HERE="$(cd "$(dirname "$0")" && pwd)"; WORK="$(mktemp -d)"; trap 'rm -rf "${WORK}"' EXIT
URL="$(aws --region "${REGION}" lambda get-function --function-name "${LAMBDA}" --query 'Code.Location' --output text)"
curl -sSf -o "${WORK}/fn.zip" "${URL}" && mkdir -p "${WORK}/fn" && (cd "${WORK}/fn" && unzip -q "${WORK}/fn.zip")
cp "${WORK}/fn/lambda_function.py" "${HERE}/backup_${LAMBDA}_$(date +%Y%m%d%H%M%S).py"
RESULT="$(python3 "${HERE}/${PATCH}" "${WORK}/fn/lambda_function.py")"; echo "${RESULT}"
python3 -m py_compile "${WORK}/fn/lambda_function.py"
if [ "${RESULT}" = "PATCHED" ] && [ "${CHECK}" != "1" ]; then
  (cd "${WORK}/fn" && zip -q -r ../fn-new.zip . -x '__pycache__/*')
  aws --region "${REGION}" lambda update-function-code --function-name "${LAMBDA}" --zip-file "fileb://${WORK}/fn-new.zip" >/dev/null
  aws --region "${REGION}" lambda wait function-updated --function-name "${LAMBDA}"
  echo "デプロイしました"
fi
