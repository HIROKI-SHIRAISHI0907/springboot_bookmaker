# -*- coding: utf-8 -*-
"""
ecs-precheck-runner-batch（Lambda）の修正（2026-11-24）
  1) B016: B016 が実際に読むもの（manifest_*.json のあるフォルダ）があるときだけ起動する。
     以前は「除外以外のファイルが 1 件でもあれば」起動していたため、直下の不要ファイルやアップロード途中の
     フォルダだけでも ECS が起動し、B016 は keys.size=3 folders=0 で何もせず終わっていた。
     大会の試合詳細（aws-s3-cup-schedule-csv/detail/）も B016 が読むので、そちらの manifest でも起動する。
  2) 診断用: event に {"DRY_RUN": true} を付けると、起動判定の結果だけ返して ECS は起動しない（全 BM_JOB 共通）。
使い方: python3 patch_precheck_b016_manifest.py <lambda_function.py>   出力: PATCHED / ALREADY
"""
import sys

path = sys.argv[1]
s = open(path, encoding="utf-8").read()
if "require_pattern" in s and "DRY_RUN" in s:
    print("ALREADY")
    sys.exit(0)

def rep(a, b):
    global s
    if s.count(a) != 1:
        sys.stderr.write("想定と違うコードのため当てられません: " + a[:60] + "\n")
        sys.exit(1)
    s = s.replace(a, b)

# --- B016 の rule
rep('''        "exclude_pattern": get_env_or_default("JOB_B016_EXCLUDE_PATTERN",
                                              r"^b016_game_detail_path(?:_(?:\\d+|cup))?\\.json$"),
''', '''        "exclude_pattern": get_env_or_default("JOB_B016_EXCLUDE_PATTERN",
                                              r"^b016_game_detail_path(?:_(?:\\d+|cup))?\\.json$"),
        # 2026-11-24: manifest_*.json（スクレイパーが最後に置く）があるフォルダだけを数える（B016 と同じ）
        "require_pattern": get_env_or_default("JOB_B016_REQUIRE_PATTERN", r"(?:^|/)manifest_[^/]*\\.json$"),
        # 2026-11-24: 大会の試合詳細も B016 が読む
        "extra_sources": [{
            "bucket": get_env_or_default("JOB_B016_CUP_BUCKET", "aws-s3-cup-schedule-csv"),
            "prefix": get_env_or_default("JOB_B016_CUP_PREFIX", "detail/"),
        }],
''')

# --- objects_exist_except に require_pattern / prefix
rep('''def objects_exist_except(bucket: str, exclude_prefixes: list, exclude_keys: list, exclude_pattern: str = ""):''',
    '''def objects_exist_except(bucket: str, exclude_prefixes: list, exclude_keys: list, exclude_pattern: str = "",
                         require_pattern: str = "", root_prefix: str = ""):''')
rep('''    exclude_re = re.compile(exclude_pattern) if exclude_pattern else None
''', '''    exclude_re = re.compile(exclude_pattern) if exclude_pattern else None
    # 2026-11-24 追加: この形のキーだけを数える（B016 は manifest_*.json）
    require_re = re.compile(require_pattern) if require_pattern else None
''')
rep('''        if exclude_re is not None and exclude_re.match(key):
            return False
        return True
''', '''        if exclude_re is not None and exclude_re.match(key):
            return False
        if require_re is not None and not require_re.search(key):
            return False
        return True
''')
rep('''    for page in paginator.paginate(Bucket=bucket, Delimiter="/"):''',
    '''    for page in paginator.paginate(Bucket=bucket, Delimiter="/", Prefix=root_prefix or ""):''')

# --- 判定で require_pattern と extra_sources を使う
rep('''        found, key = objects_exist_except(
            bucket,
            rule.get("exclude_prefixes", []),
            rule.get("exclude_keys", []),
            rule.get("exclude_pattern", ""),
        )
''', '''        found, key = objects_exist_except(
            bucket,
            rule.get("exclude_prefixes", []),
            rule.get("exclude_keys", []),
            rule.get("exclude_pattern", ""),
            rule.get("require_pattern", ""),
        )
        # 2026-11-24: ほかのバケット（大会の試合詳細など）も見る
        for src in rule.get("extra_sources", []) or []:
            if found:
                break
            found, key = objects_exist_except(
                src.get("bucket", ""), rule.get("exclude_prefixes", []), [], "",
                rule.get("require_pattern", ""), src.get("prefix", ""),
            )
            if found:
                bucket = src.get("bucket", "")
''')

# --- DRY_RUN
rep('''    # 実行しない
    if not can_run:''', '''    # 2026-11-24: 診断用。判定だけ返して起動しない
    if str(event.get("DRY_RUN", "")).lower() in ("1", "true", "yes"):
        print(f"DRY_RUN. bm_job={bm_job}, can_run={can_run}, reason={reason}")
        return {"executed": False, "dry_run": True, "bm_job": bm_job, "would_run": can_run, "reason": reason,
                "cluster": cluster_name, "task_definition": task_definition}

    # 実行しない
    if not can_run:''')

open(path, "w", encoding="utf-8").write(s)
print("PATCHED")
