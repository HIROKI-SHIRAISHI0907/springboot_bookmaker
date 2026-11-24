# -*- coding: utf-8 -*-
"""
ecs-precheck-runner-batch（Lambda）の B016 の追加修正（2026-11-24）
  1) _ng/（B016 が読めなかったフォルダの置き場）を数えない（既定の除外に _ng/ を足す）。
     以前は _ng/ の中のファイルを「登録するもの」と数え、10 分ごとに B016 を起動していた（B016 は _ng/ を読まない）。
  2) 大会のバケット（aws-s3-cup-schedule-csv/detail/）を見られなかったとき（権限など）は、
     例外で Lambda ごと落とさず、ログを出して本体のバケットの結果だけで判定する。
使い方: python3 patch_precheck_b016_ng.py <lambda_function.py>   出力: PATCHED / ALREADY
前提: patch_precheck_b016_manifest.py を当て済み
"""
import sys

path = sys.argv[1]
s = open(path, encoding="utf-8").read()
if '"_done/,_skipped/,_ng/"' in s and "extra source error" in s:
    print("ALREADY")
    sys.exit(0)

def rep(a, b):
    global s
    if s.count(a) != 1:
        sys.stderr.write("想定と違うコードのため当てられません: " + a[:70] + "\n")
        sys.exit(1)
    s = s.replace(a, b)

rep('''"exclude_prefixes": get_env_list("JOB_B016_EXCLUDE_PREFIXES", "_done/,_skipped/"),''',
    '''"exclude_prefixes": get_env_list("JOB_B016_EXCLUDE_PREFIXES", "_done/,_skipped/,_ng/"),''')
rep('''            found, key = objects_exist_except(
                src.get("bucket", ""), rule.get("exclude_prefixes", []), [], "",
                rule.get("require_pattern", ""), src.get("prefix", ""),
            )''', '''            try:
                found, key = objects_exist_except(
                    src.get("bucket", ""), rule.get("exclude_prefixes", []), [], "",
                    rule.get("require_pattern", ""), src.get("prefix", ""),
                )
            except Exception as e:
                print(f"extra source error. bucket={src.get('bucket')}, prefix={src.get('prefix')}, error={e}")
                found, key = False, None''')
open(path, "w", encoding="utf-8").write(s)
print("PATCHED")
