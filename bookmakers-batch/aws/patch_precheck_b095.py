# -*- coding: utf-8 -*-
"""
ecs-precheck-runner-batch（Lambda）の lambda_function.py に B095（不要データ削除）を足す（2026-11-24）。
  - JOB_RULES に "B095"（クラスター batch-backend・タスク定義 bookmakers-batch。同じ BM_JOB=B095 が実行中なら起動しない）
  - check_type は B011 と同じ "always_unless_running"（S3 の確認なし）。B011 のパッチ（patch_precheck_b011.py）が先に当たっていること
使い方: python3 patch_precheck_b095.py <lambda_function.py>
出力: PATCHED（直した）/ ALREADY（直してある）。当てられないときは終了コード 1
"""
import re
import sys

path = sys.argv[1]
s = open(path, encoding="utf-8").read()
if '"B095": {' in s:
    print("ALREADY")
    sys.exit(0)
if 'if check_type == "always_unless_running":' not in s:
    sys.stderr.write("check_type always_unless_running がありません。先に patch_precheck_b011.py を当ててください\n")
    sys.exit(1)

RULE = '''    # =====================================================
    # B095（不要データ削除） 2026-11-24 追加
    # EventBridge Scheduler deleteDataRunEcsTask（毎月1日・{"BM_JOB":"B095"}）から呼ばれ、
    # 同じ BM_JOB=B095 のタスクが実行中でなければ起動する。
    # 削除するもの: mail_send_manage（1日経過）・batch_job_exec（status 10/99）・
    # file_upload_status_manage（1か月経過。PENDING 除く）・aws-s3-delay-postpone-csv の延期ファイル（3か月経過）
    # =====================================================
    "B095": {
        "cluster": get_env_or_default("JOB_B095_CLUSTER", "arn:aws:ecs:ap-northeast-1:287190274071:cluster/batch-backend"),
        "check_type": "always_unless_running",
        "default_task_definition": "bookmakers-batch",
        "default_container_name": "bookmakers-batch",
        "skip_if_same_job_running": True,
    },

'''
m = re.search(r'^    "B0\d\d": \{', s, re.M)
if not m:
    sys.stderr.write("JOB_RULES の B0xx が見つかりません\n")
    sys.exit(1)
# 直前のコメントブロックの頭に入れる
head = s.rfind("    # =====", 0, m.start())
pos = head if head != -1 and s.count("\n", head, m.start()) <= 8 else m.start()
s = s[:pos] + RULE + s[pos:]
open(path, "w", encoding="utf-8").write(s)
print("PATCHED")
