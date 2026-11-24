#!/usr/bin/env bash
# ecs-precheck-runner-batch の起動判定を全 BM_JOB で見る（DRY_RUN。ECS は起動しない。2026-11-24）
#   bash check_precheck_dryrun.sh
REGION="${REGION:-ap-northeast-1}"; LAMBDA="${LAMBDA:-ecs-precheck-runner-batch}"
for j in B002 B003 B004 B005 B006 B008 B010 B011 B014 B015 B016 B017 B095; do
  aws --region "${REGION}" lambda invoke --function-name "${LAMBDA}" --cli-binary-format raw-in-base64-out \
    --payload "{\"BM_JOB\":\"${j}\",\"DRY_RUN\":true}" /tmp/precheck_${j}.json >/dev/null 2>&1
  python3 -c "import json,sys;d=json.load(open('/tmp/precheck_${j}.json'));print(f\"${j}\t{'起動する' if d.get('would_run') else '起動しない'}\t{d.get('reason')}\")" 2>/dev/null || echo "${j}\t(読めません: $(cat /tmp/precheck_${j}.json))"
done
