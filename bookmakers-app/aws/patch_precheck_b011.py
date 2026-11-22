# -*- coding: utf-8 -*-
"""
ecs-precheck-runner-batch（Lambda）の lambda_function.py に B011（統計CSVデータ生成）を足す（2026-11-22）。
  - JOB_RULES に "B011"（クラスター stats-backend・タスク定義 bookmakers-app。同じ BM_JOB=B011 が実行中なら起動しない）
  - should_run_job に check_type "always_unless_running"（S3 の確認なし。実行中でなければ起動）
使い方: python3 patch_precheck_b011.py <lambda_function.py>
出力: PATCHED（直した）/ ALREADY（直してある）。当てられないときは終了コード 1
"""
import re
import sys

path = sys.argv[1]
s = open(path, encoding="utf-8").read()
if '"B011": {' in s and 'always_unless_running' in s:
    print("ALREADY")
    sys.exit(0)

RULE = '''    # =====================================================
    # B011（統計CSVデータ生成） 2026-11-22 追加
    # 統計用の bookmakers-app（クラスター stats-backend）で動かす。EventBridge Scheduler statCsvRunEcsTask
    # （毎日・{"BM_JOB":"B011"}）から呼ばれ、同じ BM_JOB=B011 のタスクが実行中でなければ起動する
    # （新しく CSV にする試合が無ければ B011 はすぐ終わる）。
    # =====================================================
    "B011": {
        "cluster": get_env_or_default("JOB_B011_CLUSTER", "arn:aws:ecs:ap-northeast-1:287190274071:cluster/stats-backend"),
        "check_type": "always_unless_running",
        "default_task_definition": "bookmakers-app",
        "default_container_name": "bookmakers-app",
        "skip_if_same_job_running": True,
    },

'''
CHECK = '''    # 2026-11-22: S3 は見ず、同じ BM_JOB のタスクが実行中でなければ起動する（B011）
    if check_type == "always_unless_running":
        if rule.get("skip_if_same_job_running"):
            running, task_arn = check_same_job_running(bm_job, rule)
            if running:
                return False, f"SAME_JOB_RUNNING:{bm_job}:{task_arn}", rule
        return True, "ALWAYS_UNLESS_RUNNING", rule

'''
m = re.search(r'^    "B0\d\d": \{', s, re.M)
anchor = '    if check_type == "csv_exists_outside_json":'
if not m or s.count(anchor) != 1 or '"B011": {' in s:
    sys.stderr.write("想定と違うコードのため当てられません（JOB_RULES の B0xx か should_run_job が見つからない）\n")
    sys.exit(1)
s = s[:m.start()] + RULE + s[m.start():]
s = s.replace(anchor, CHECK + anchor)
open(path, "w", encoding="utf-8").write(s)
print("PATCHED")
