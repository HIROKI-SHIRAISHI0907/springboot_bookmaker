# -*- coding: utf-8 -*-
"""
ecs-precheck-runner-batch（Lambda）の lambda_function.py に B006（統計CSV取り込み）を足す（2026-11-24）。
  - JOB_RULES に "B006"（クラスター stats-backend・タスク定義 bookmakers-app）
  - check_type は B011 と同じ "always_unless_running"。同じ B006 に加えて、B011（統計CSV作成）が実行中でも起動しない
    （rule の "skip_if_jobs_running": ["B011"]。B011 が作り終わる前に取り込むと、その日の新しい CSV が翌日回しになるだけだが念のため）
使い方: python3 patch_precheck_b006.py <lambda_function.py>
出力: PATCHED / ALREADY。当てられないときは終了コード 1（先に patch_precheck_b011.py が必要）
"""
import sys

path = sys.argv[1]
s = open(path, encoding="utf-8").read()
if '"B006": {' in s:
    print("ALREADY")
    sys.exit(0)
OLD = '''    if check_type == "always_unless_running":
        if rule.get("skip_if_same_job_running"):
            running, task_arn = check_same_job_running(bm_job, rule)
            if running:
                return False, f"SAME_JOB_RUNNING:{bm_job}:{task_arn}", rule
        return True, "ALWAYS_UNLESS_RUNNING", rule
'''
NEW = '''    if check_type == "always_unless_running":
        if rule.get("skip_if_same_job_running"):
            running, task_arn = check_same_job_running(bm_job, rule)
            if running:
                return False, f"SAME_JOB_RUNNING:{bm_job}:{task_arn}", rule
        # 2026-11-24: ほかの BM_JOB（例: B006 は B011）が同じタスク定義で実行中なら起動しない
        for other in rule.get("skip_if_jobs_running", []) or []:
            running, task_arn = check_same_job_running(other, rule)
            if running:
                return False, f"OTHER_JOB_RUNNING:{other}:{task_arn}", rule
        return True, "ALWAYS_UNLESS_RUNNING", rule
'''
if s.count(OLD) != 1 or s.count("JOB_RULES = {\n") != 1:
    sys.stderr.write("想定と違うコードのため当てられません（先に patch_precheck_b011.py を当ててください）\n")
    sys.exit(1)
RULE = '''    # =====================================================
    # B006（統計CSV取り込み・MainStat） 2026-11-24 追加
    # EventBridge Scheduler statImportRunEcsTask（毎日 5:00・{"BM_JOB":"B006"}）から呼ばれ、
    # B006・B011 のタスクが実行中でなければ起動する（取り込む CSV が無ければ B006 はすぐ終わる）。
    # =====================================================
    "B006": {
        "cluster": get_env_or_default("JOB_B006_CLUSTER", "arn:aws:ecs:ap-northeast-1:287190274071:cluster/stats-backend"),
        "check_type": "always_unless_running",
        "default_task_definition": "bookmakers-app",
        "default_container_name": "bookmakers-app",
        "skip_if_same_job_running": True,
        "skip_if_jobs_running": ["B011"],
    },

'''
s = s.replace(OLD, NEW).replace("JOB_RULES = {\n", "JOB_RULES = {\n" + RULE)
open(path, "w", encoding="utf-8").write(s)
print("PATCHED")
