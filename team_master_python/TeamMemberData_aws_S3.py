# -*- coding: utf-8 -*-
"""
TeamMemberData_lambda_s3_full.py  （Lambda向け・全処理入り 完全版）

✅ 目的
- S3 から teams_by_league/teamData_*.csv（入力）を /tmp に取得（任意）
- S3 から json/b001/b001_country_league.json（フィルタJSON）を /tmp に取得（任意）
- S3 から 既存出力 teamMemberData_*.csv を /tmp に復元（任意：重複排除のため）
- 6〜10チーム並列処理（TEAM_CONCURRENCY）
- 各チームは PagePool(PLAYER_PER_TEAM) で選手詳細を並列取得
- page.goto の実同時数と発火間隔を制御（GLOBAL_NAV_CONCURRENCY / NAV_GAP_MS）
- 不要リソース/広告/同意ドメインは遮断
- 書込は CSV Writer タスクに集約（バッファ追記＋デバウンスflush）
- CSV を max_records でローテーション
- ローテーション/flush ごとに S3 へアップロード（落ちても成果が残る）
- 可能なら RUN_ID を付けて、同時実行衝突を避けられる（推奨）

------------------------------------------------------------
【推奨 Lambda 設定】
- Timeout: 900 sec
- Memory: 3072MB 以上推奨
- Ephemeral storage: 2048MB 推奨
- Reserved concurrency: 1（最初は必須レベル）
------------------------------------------------------------

【環境変数（重要）】
- S3_BUCKET               : 入出力のバケット名（デフォルト teamData）
- S3_INPUT_PREFIX         : 入力 teamData_*.csv の S3プレフィクス（例: "teams_by_league"）
- S3_OUTPUT_PREFIX        : 出力 teamMemberData_*.csv の S3プレフィクス（例: "team_members"）
- S3_JSON_KEY             : フィルタJSONのS3キー（例: "json/b001/b001_country_league.json"）
- RESTORE_OUTPUT          : "1" で既存出力をS3から復元（重複排除を効かせる）(default "1")
- RUN_ID                  : 任意。指定すると OUT_PREFIX に埋め込める
- USE_RUN_ID_PREFIX       : "1" で OUT_PREFIX に RUN_ID を付与して衝突回避（default "0"）
- TEAM_CONCURRENCY        : default 6
- PLAYER_PER_TEAM         : default 1
- GLOBAL_NAV_CONCURRENCY  : default 3
- NAV_GAP_MS              : default 150
- OUT_MAX_RECORDS         : default 5000
- OP_TIMEOUT              : default 5000
- NAV_TIMEOUT             : default 12000
- SEL_TIMEOUT             : default 20000
- SAVE_INTERVAL_SEC       : default 2.0
- SAVE_EVERY_N_ROWS       : default 200
- LOG_EVERY_ENQUEUE       : "1" or "0" (default "1")
"""

import os
import re
import glob
import json
import csv
import asyncio
import datetime
from pathlib import Path
from typing import List, Tuple, Dict, Set, Optional

import boto3
from botocore.exceptions import ClientError
from playwright.async_api import async_playwright

# =========================
# 設定（Lambda向け）
# =========================
BASE_DIR = "/tmp/bookmaker"
TEAMS_BY_LEAGUE_DIR = os.path.join(BASE_DIR, "teams_by_league")
os.makedirs(TEAMS_BY_LEAGUE_DIR, exist_ok=True)

# ✅ Lambdaでは /tmp を正にする（ローカル固定パス禁止）
B001_JSON_PATH = str(Path(BASE_DIR) / "json/b001/b001_country_league.json")

TEAMDATA_PREFIX = "teamData_"
TEAMDATA_GLOB = os.path.join(TEAMS_BY_LEAGUE_DIR, f"{TEAMDATA_PREFIX}*.csv")

# 出力prefix（RUN_ID付与は任意で後段で上書きする）
OUT_PREFIX_BASE = "teamMemberData_"
OUT_MAX_RECORDS = int(os.getenv("OUT_MAX_RECORDS", "5000"))

# 並列度（環境変数で調整）
TEAM_CONCURRENCY       = int(os.getenv("TEAM_CONCURRENCY", "6"))
PLAYER_PER_TEAM        = int(os.getenv("PLAYER_PER_TEAM", "1"))
GLOBAL_NAV_CONCURRENCY = int(os.getenv("GLOBAL_NAV_CONCURRENCY", "3"))
NAV_GAP_MS             = int(os.getenv("NAV_GAP_MS", "150"))

# タイムアウト(ms)
OP_TIMEOUT  = int(os.getenv("OP_TIMEOUT", "5000"))
NAV_TIMEOUT = int(os.getenv("NAV_TIMEOUT", "12000"))
SEL_TIMEOUT = int(os.getenv("SEL_TIMEOUT", "20000"))

# Writer flush
SAVE_INTERVAL_SEC = float(os.getenv("SAVE_INTERVAL_SEC", "2.0"))
SAVE_EVERY_N_ROWS = int(os.getenv("SAVE_EVERY_N_ROWS", "200"))
LOG_EVERY_ENQUEUE = os.getenv("LOG_EVERY_ENQUEUE", "1") == "1"

# S3
S3_BUCKET = os.getenv("S3_BUCKET", "teamData")
S3_INPUT_PREFIX  = (os.getenv("S3_INPUT_PREFIX", "").strip() or "").strip("/")
S3_OUTPUT_PREFIX = (os.getenv("S3_OUTPUT_PREFIX", "").strip() or "").strip("/")
S3_JSON_KEY      = os.getenv("S3_JSON_KEY", "").strip()

RESTORE_OUTPUT = os.getenv("RESTORE_OUTPUT", "1") == "1"

# RUN_ID（衝突回避用）
RUN_ID = (os.getenv("RUN_ID", "").strip() or datetime.datetime.now().strftime("%Y%m%d_%H%M%S"))
USE_RUN_ID_PREFIX = os.getenv("USE_RUN_ID_PREFIX", "0") == "1"

OUT_PREFIX = f"{OUT_PREFIX_BASE}{RUN_ID}_" if USE_RUN_ID_PREFIX else OUT_PREFIX_BASE

def _prefix(p: str) -> str:
    return (p.strip("/") + "/") if p else ""

def s3_input_key(name: str) -> str:
    return _prefix(S3_INPUT_PREFIX) + name

def s3_output_key(name: str) -> str:
    return _prefix(S3_OUTPUT_PREFIX) + name

# ブロックする外部ドメイン
BLOCKED_HOST_KEYWORDS = [
    "googletagmanager", "google-analytics", "doubleclick", "googlesyndication",
    "scorecardresearch", "criteo", "adsystem", "mathtag", "quantserve",
    "taboola", "facebook", "twitter", "hotjar", "onetrust", "cookiebot",
    "trustarc", "ioam.de", "amazon-adsystem", "adservice"
]

# =========================
# フィルタ用（あなたの元コード）
# =========================
CONTAINS_LIST = [
    "ケニア: プレミアリーグ", "コロンビア: プリメーラ A", "タンザニア: プレミアリーグ", "イングランド: プレミアリーグ",
    "イングランド: EFL チャンピオンシップ", "イングランド: EFL リーグ 1", "エチオピア: プレミアリーグ", "コスタリカ: リーガ FPD",
    "ジャマイカ: プレミアリーグ", "スペイン: ラ・リーガ", "ブラジル: セリエ A ベターノ", "ブラジル: セリエ B", "ドイツ: ブンデスリーガ",
    "韓国: K リーグ 1", "中国: 中国スーパーリーグ", "日本: J1 リーグ", "日本: J2 リーグ", "日本: J3 リーグ", "インドネシア: スーパーリーグ",
    "オーストラリア: A リーグ・メン", "チュニジア: チュニジア･プロリーグ", "ウガンダ: プレミアリーグ", "メキシコ: リーガ MX",
    "フランス: リーグ・アン", "スコットランド: プレミアシップ", "オランダ: エールディビジ", "アルゼンチン: トルネオ・ベターノ",
    "イタリア: セリエ A", "イタリア: セリエ B", "ポルトガル: リーガ・ポルトガル", "トルコ: スュペル・リグ", "セルビア: スーペルリーガ",
    "日本: WEリーグ", "ボリビア: LFPB", "ブルガリア: パルヴァ・リーガ", "カメルーン: エリート 1", "ペルー: リーガ 1",
    "エストニア: メスタリリーガ", "ウクライナ: プレミアリーグ", "ベルギー: ジュピラー･プロリーグ", "エクアドル: リーガ・プロ",
    "日本: YBC ルヴァンカップ", "日本: 天皇杯"
]
UNDER_LIST  = ["U17", "U18", "U19", "U20", "U21", "U22", "U23", "U24", "U25"]
GENDER_LIST = ["女子"]
EXP_LIST    = ["ポルトガル: リーガ・ポルトガル 2", "イングランド: プレミアリーグ 2", "イングランド: プレミアリーグ U18"]

def build_targets_from_contains() -> Dict[str, Set[str]]:
    out: Dict[str, Set[str]] = {}
    for ent in CONTAINS_LIST:
        c, l = [x.strip() for x in ent.split(":", 1)]
        out.setdefault(c, set()).add(l)
    return out

def extract_country_leagues_map(json_path: str) -> Dict[str, Set[str]]:
    p = Path(json_path).expanduser()
    if not p.exists():
        return {}
    data = json.loads(p.read_text(encoding="utf-8"))
    out: Dict[str, Set[str]] = {}

    def norm(s) -> str:
        return re.sub(r"\s+", " ", str(s or "")).strip()

    def add(country: str, league: str):
        c = norm(country); l = norm(league)
        if c and l:
            out.setdefault(c, set()).add(l)

    def walk(obj):
        if isinstance(obj, dict):
            for k, v in obj.items():
                if isinstance(v, list) and all(isinstance(x, (str, int, float)) for x in v):
                    for lv in v:
                        add(k, lv)
            if "country" in obj and "league" in obj:
                add(obj.get("country"), obj.get("league"))
            for kk in ("items", "data", "results", "country_league"):
                if kk in obj:
                    walk(obj[kk])
            for v in obj.values():
                if isinstance(v, (dict, list)):
                    walk(v)
        elif isinstance(obj, list):
            for x in obj:
                walk(x)
    walk(data)
    return out

# =========================
# S3 IO
# =========================
def s3_upload_file(local_path: str, key: str) -> bool:
    s3 = boto3.client("s3")
    try:
        s3.upload_file(local_path, S3_BUCKET, key)
        print(f"[S3 UPLOAD] s3://{S3_BUCKET}/{key}")
        return True
    except ClientError as e:
        print(f"[S3 ERROR] upload failed: {local_path} -> s3://{S3_BUCKET}/{key} ({e})")
        return False

def s3_download_teamdata_if_needed() -> int:
    """
    S3_INPUT_PREFIX が指定されている場合のみ、
    teamData_*.csv を S3 から /tmp/teams_by_league に落とす。
    """
    if not S3_INPUT_PREFIX:
        print("[S3] S3_INPUT_PREFIX not set -> skip download teamData")
        return 0

    s3 = boto3.client("s3")
    prefix = _prefix(S3_INPUT_PREFIX)
    paginator = s3.get_paginator("list_objects_v2")

    found = 0
    for page in paginator.paginate(Bucket=S3_BUCKET, Prefix=prefix):
        for obj in page.get("Contents", []) or []:
            key = obj.get("Key", "")
            base = os.path.basename(key)
            if not re.match(rf"^{re.escape(TEAMDATA_PREFIX)}\d+\.csv$", base):
                continue
            local = os.path.join(TEAMS_BY_LEAGUE_DIR, base)
            try:
                s3.download_file(S3_BUCKET, key, local)
                found += 1
                print(f"[S3 DOWNLOAD] {key} -> {local}")
            except ClientError as e:
                print(f"[S3 ERROR] download failed: {key} ({e})")

    print(f"[S3] downloaded teamData csv files: {found}")
    return found

def s3_download_json_if_needed() -> bool:
    """
    S3_JSON_KEY が指定されている場合のみ、
    b001_country_league.json を /tmp に落とす。
    """
    if not S3_JSON_KEY:
        print("[S3] S3_JSON_KEY not set -> skip download json")
        return False
    s3 = boto3.client("s3")
    local = B001_JSON_PATH
    os.makedirs(os.path.dirname(local), exist_ok=True)
    try:
        s3.download_file(S3_BUCKET, S3_JSON_KEY, local)
        print(f"[S3 DOWNLOAD] {S3_JSON_KEY} -> {local}")
        return True
    except ClientError as e:
        print(f"[S3 ERROR] json download failed: {S3_JSON_KEY} ({e})")
        return False

def s3_restore_existing_outputs() -> int:
    """
    RESTORE_OUTPUT=1 のとき、S3_OUTPUT_PREFIX 配下から
    OUT_PREFIX に合致する CSV を /tmp に復元して重複排除に使う。
    """
    if not RESTORE_OUTPUT:
        print("[S3] RESTORE_OUTPUT=0 -> skip restore outputs")
        return 0

    prefix = _prefix(S3_OUTPUT_PREFIX)
    if not prefix:
        print("[S3] S3_OUTPUT_PREFIX empty -> skip restore outputs")
        return 0

    s3 = boto3.client("s3")
    paginator = s3.get_paginator("list_objects_v2")

    restored = 0
    # OUT_PREFIX が RUN_ID つきの場合、「同じRUN_IDの既存成果」は通常無い想定。
    # それでも復元したい場合は、このmatch条件を OUT_PREFIX_BASE に寄せてください。
    pat = re.compile(rf"^{re.escape(OUT_PREFIX)}\d+\.csv$")

    for page in paginator.paginate(Bucket=S3_BUCKET, Prefix=prefix):
        for obj in page.get("Contents", []) or []:
            key = obj.get("Key", "")
            base = os.path.basename(key)
            if not pat.match(base):
                continue
            local = os.path.join(TEAMS_BY_LEAGUE_DIR, base)
            try:
                s3.download_file(S3_BUCKET, key, local)
                restored += 1
                print(f"[S3 RESTORE] {key} -> {local}")
            except ClientError as e:
                print(f"[S3 ERROR] restore failed: {key} ({e})")

    print(f"[S3] restored output csv files: {restored}")
    return restored

# =========================
# 入力CSV読込
# =========================
def list_teamdata_csv_paths() -> List[str]:
    paths = glob.glob(TEAMDATA_GLOB)
    if not paths:
        return []

    def extract_num(p: str) -> int:
        base = os.path.basename(p)
        m = re.search(rf"^{re.escape(TEAMDATA_PREFIX)}(\d+)\.csv$", base)
        if m:
            return int(m.group(1))
        return 10**18

    return sorted(paths, key=lambda p: (extract_num(p), os.path.basename(p)))

def read_teams_from_file(path: str) -> List[Tuple[str, str, str, str]]:
    out: List[Tuple[str, str, str, str]] = []
    KEYMAP = {
        "国": ["国", "country", "Country"],
        "リーグ": ["リーグ", "league", "League"],
        "チーム": ["チーム", "team", "Team"],
        "チームリンク": ["チームリンク", "チームURL", "href", "link", "url"],
    }

    def normv(s: str) -> str:
        return (s or "").strip()

    try:
        with open(path, "r", encoding="utf-8-sig", newline="") as f:
            reader = csv.DictReader(f)
            if not reader.fieldnames:
                return out

            resolved: Dict[str, str] = {}
            fields = [h.strip() for h in reader.fieldnames if h]
            for canonical, aliases in KEYMAP.items():
                for a in aliases:
                    if a in fields:
                        resolved[canonical] = a
                        break

            use_fallback = any(k not in resolved for k in ("国", "リーグ", "チーム", "チームリンク"))
            if use_fallback:
                f.seek(0)
                r = csv.reader(f)
                _header = next(r, [])
                for row in r:
                    if not row:
                        continue
                    c = normv(row[0] if len(row) > 0 else "")
                    l = normv(row[1] if len(row) > 1 else "")
                    t = normv(row[2] if len(row) > 2 else "")
                    h = normv(row[3] if len(row) > 3 else "")
                    if c and l and t and h:
                        out.append((c, l, t, h))
                return out

            for row in reader:
                c = normv(row.get(resolved["国"], ""))
                l = normv(row.get(resolved["リーグ"], ""))
                t = normv(row.get(resolved["チーム"], ""))
                h = normv(row.get(resolved["チームリンク"], ""))
                if c and l and t and h:
                    out.append((c, l, t, h))
    except Exception as e:
        print(f"[WARN] チームCSV読込失敗: {path} / {e}")
    return out

# =========================
# 既取得キー（既存CSVのみを見る：xlsx廃止）
# =========================
def load_existing_player_keys() -> Set[Tuple[str, str, str, str]]:
    s: Set[Tuple[str, str, str, str]] = set()
    for path in sorted(glob.glob(os.path.join(TEAMS_BY_LEAGUE_DIR, f"{OUT_PREFIX}[0-9]*.csv"))):
        try:
            with open(path, "r", encoding="utf-8-sig", newline="") as f:
                r = csv.DictReader(f)
                for row in r:
                    c = str(row.get("国", "")).strip()
                    l = str(row.get("リーグ", "")).strip()
                    t = str(row.get("所属チーム", "")).strip()
                    n = str(row.get("選手名", "")).strip()
                    if c and l and t and n:
                        s.add((c, l, t, n))
        except Exception as e:
            print(f"[WARN] 既存CSV読込失敗: {path} / {e}")
    return s

# =========================
# Playwright（ナビ制御）
# =========================
global_nav_sema = asyncio.Semaphore(GLOBAL_NAV_CONCURRENCY)
_nav_gap_lock = asyncio.Lock()
_last_nav_ts = 0.0

async def _respect_nav_gap():
    global _last_nav_ts
    async with _nav_gap_lock:
        loop = asyncio.get_running_loop()
        now = loop.time()
        due = _last_nav_ts + (NAV_GAP_MS / 1000.0)
        if now < due:
            await asyncio.sleep(due - now)
        _last_nav_ts = loop.time()

async def make_context(browser):
    ctx = await browser.new_context(
        locale="ja-JP",
        timezone_id="Asia/Tokyo",
        viewport={"width": 1280, "height": 800},
        ignore_https_errors=True,
        java_script_enabled=True,
    )
    ctx.set_default_timeout(OP_TIMEOUT)
    ctx.set_default_navigation_timeout(NAV_TIMEOUT)

    async def _route(route):
        req = route.request
        rtype = req.resource_type
        url = req.url.lower()

        if rtype in {"image", "stylesheet", "font", "media", "beacon"}:
            return await route.abort()
        if any(k in url for k in BLOCKED_HOST_KEYWORDS):
            return await route.abort()
        return await route.continue_()

    await ctx.route("**/*", _route)
    return ctx

async def safe_goto(page, url: str, selector_to_wait: Optional[str],
                    nav_timeout=NAV_TIMEOUT, sel_timeout=SEL_TIMEOUT, tries=3) -> bool:
    last = None
    for i in range(tries):
        try:
            async with global_nav_sema:
                await _respect_nav_gap()
                await page.goto(url, wait_until="domcontentloaded", timeout=nav_timeout)
            if selector_to_wait:
                await page.wait_for_selector(selector_to_wait, timeout=sel_timeout, state="attached")
            return True
        except Exception as e:
            last = e
            await asyncio.sleep(0.5 * (i + 1))
    print(f"Page.goto 最終失敗: {url} / {last}")
    return False

# =========================
# Page プール（チーム内）
# =========================
class PagePool:
    def __init__(self, ctx, size: int):
        self.ctx = ctx
        self.sema = asyncio.Semaphore(size)
        self.cache: List = []
        self.lock = asyncio.Lock()

    async def acquire(self):
        await self.sema.acquire()
        async with self.lock:
            if self.cache:
                return self.cache.pop()
        return await self.ctx.new_page()

    async def release(self, page):
        async with self.lock:
            self.cache.append(page)
        self.sema.release()

    async def close(self):
        async with self.lock:
            while self.cache:
                p = self.cache.pop()
                try:
                    await p.close()
                except Exception:
                    pass

# =========================
# CSV Writer（xlsx廃止）
# =========================
class CsvWriter:
    """
    - Queue に [row, row, ...] のチーム単位バッチを入れる
    - {OUT_PREFIX}{seq}.csv に追記
    - max_records でローテーション
    - flush/rotate ごとに S3 にアップロード
    """
    HEADER = [
        "国","リーグ","所属チーム","選手名","ポジション","背番号",
        "得点数","年齢","誕生日","市場価値","ローン保有元",
        "契約期限","顔写真","故障情報","データ取得時間"
    ]

    def __init__(self, out_dir: str, prefix: str, max_records: int):
        self.out_dir = out_dir
        self.prefix = prefix
        self.max_records = max_records
        self.q: asyncio.Queue = asyncio.Queue()
        self._task: Optional[asyncio.Task] = None

        self.seq = 1
        self.rows_in_current = 0
        self._dirty = 0
        self._fp = None
        self._writer = None

    def _scan_next_seq(self) -> int:
        existing = sorted(glob.glob(os.path.join(self.out_dir, f"{self.prefix}[0-9]*.csv")))
        if not existing:
            return 1
        def num(p):
            m = re.search(rf"^{re.escape(self.prefix)}(\d+)\.csv$", os.path.basename(p))
            return int(m.group(1)) if m else 0
        return max(num(p) for p in existing) or 1

    def _open_file(self, seq: int, append: bool = True):
        path = os.path.join(self.out_dir, f"{self.prefix}{seq}.csv")
        exists = os.path.exists(path)
        mode = "a" if append else "w"
        self._fp = open(path, mode, encoding="utf-8-sig", newline="")
        self._writer = csv.writer(self._fp)
        if (not exists) or os.path.getsize(path) == 0:
            self._writer.writerow(self.HEADER)

        # 既存行数（ヘッダ除く）をざっくり数える（大きい場合は重い）
        if exists and append:
            try:
                with open(path, "r", encoding="utf-8-sig", newline="") as f:
                    n = sum(1 for _ in f) - 1
                self.rows_in_current = max(n, 0)
            except:
                self.rows_in_current = 0
        else:
            self.rows_in_current = 0

    def _close_file(self):
        if self._fp:
            try:
                self._fp.flush()
                os.fsync(self._fp.fileno())
            except:
                pass
            try:
                self._fp.close()
            except:
                pass
        self._fp = None
        self._writer = None

    def _flush_and_upload(self):
        if not self._fp:
            return
        try:
            self._fp.flush()
            os.fsync(self._fp.fileno())
        except:
            pass

        local = os.path.join(self.out_dir, f"{self.prefix}{self.seq}.csv")
        key = s3_output_key(os.path.basename(local))
        s3_upload_file(local, key)

        self._dirty = 0

    def enqueue_team_rows(self, rows: List[List[object]], label: str):
        self.q.put_nowait((rows, label))

    async def run(self):
        self.seq = self._scan_next_seq()
        self._open_file(self.seq, append=True)

        while True:
            try:
                rows, label = await asyncio.wait_for(self.q.get(), timeout=SAVE_INTERVAL_SEC)
            except asyncio.TimeoutError:
                if self._dirty > 0:
                    self._flush_and_upload()
                continue

            if rows is None:
                if self._dirty > 0:
                    self._flush_and_upload()
                self._close_file()
                break

            offset = 0
            while offset < len(rows):
                remain = self.max_records - self.rows_in_current
                chunk = rows[offset: offset + remain]

                for r in chunk:
                    self._writer.writerow([("" if v is None else v) for v in r])

                self.rows_in_current += len(chunk)
                self._dirty += len(chunk)
                offset += len(chunk)

                if self.rows_in_current >= self.max_records:
                    self._flush_and_upload()
                    self._close_file()
                    self.seq += 1
                    self._open_file(self.seq, append=False)
                    print(f"[WRITER] Rotate -> {self.prefix}{self.seq}.csv")

            if LOG_EVERY_ENQUEUE:
                print(f"[WRITER] APPEND {label}: +{len(rows)} rows -> file #{self.seq} (cur={self.rows_in_current})")

            if self._dirty >= SAVE_EVERY_N_ROWS:
                self._flush_and_upload()

    async def start(self):
        if self._task is None:
            self._task = asyncio.create_task(self.run())

    async def stop(self):
        await self.q.put((None, "STOP"))
        if self._task:
            await self._task

# =========================
# 選手詳細
# =========================
async def scrape_player_detail_with_page(page, full_url: str) -> Dict[str, str]:
    age = birth = mv = contract = loan = img = inj = "N/A"

    ok = await safe_goto(page, full_url, "div.playerHeader__wrapper div.playerInfoItem")
    if not ok:
        return {
            "age_text": age, "birth_date": birth, "market_value": mv,
            "loan_info": loan, "contract_date": contract,
            "img_url": img, "injury_text": inj
        }

    try:
        el = await page.query_selector(
            'div[data-testid="wcl-assetContainerBoxed-xl"] img, div[data-testid="wcl-assetContainerBoxed-XL"] img'
        )
        if el:
            img = await el.get_attribute("src") or "N/A"
    except Exception:
        pass

    try:
        items = await page.query_selector_all("div.playerHeader__wrapper div.playerInfoItem")
        for it in items:
            spans = await it.query_selector_all("span")
            if not spans:
                continue

            label = (await spans[0].text_content() or "").strip()
            label = re.sub(r"\s+", " ", label).replace(":", "").strip()

            values = []
            for sp in spans[1:]:
                tx = (await sp.text_content() or "").strip()
                tx = re.sub(r"\s+", " ", tx)
                if tx:
                    values.append(tx)

            if "年齢" in label:
                if len(values) >= 1:
                    age = values[0]
                if len(values) >= 2:
                    birth = values[1].strip().strip("()")
            elif "市場価値" in label:
                if values:
                    mv = values[0]
            elif "契約期限" in label:
                if values:
                    contract = values[0]
            elif "ローン" in label or "ローン元" in label:
                if values:
                    loan = values[0]
                if len(values) >= 2 and contract == "N/A":
                    contract = values[1].replace("期限", "").replace(":", "").strip().strip("()")

    except Exception as e:
        print(f"[PLAYER DETAIL] parse失敗: {full_url} / {e}")

    try:
        inj_svg = await page.query_selector('svg[data-testid="wcl-icon-incidents-injury"]')
        if inj_svg:
            title_el = await inj_svg.query_selector("title")
            if title_el:
                t = (await title_el.text_content() or "").strip()
                if t:
                    inj = re.sub(r"\s+", " ", t)
    except Exception:
        pass

    return {
        "age_text": age, "birth_date": birth, "market_value": mv,
        "loan_info": loan, "contract_date": contract,
        "img_url": img, "injury_text": inj
    }

# =========================
# チーム処理
# =========================
async def process_team(ctx,
                       existing_keys: Set[Tuple[str, str, str, str]],
                       claimed_keys: Set[Tuple[str, str, str, str]],
                       claim_lock: asyncio.Lock,
                       team_sema: asyncio.Semaphore,
                       writer: CsvWriter,
                       country: str, league: str, team: str, href: str):

    async with team_sema:
        page = await ctx.new_page()
        pool = PagePool(ctx, size=max(PLAYER_PER_TEAM, 1))

        rows_to_write: List[List[object]] = []
        rows_lock = asyncio.Lock()

        try:
            team_url  = href if href.startswith("http") else f"https://flashscore.co.jp{href}"
            squad_url = team_url.rstrip("/") + "/squad/"

            ok = await safe_goto(page, squad_url, "div.lineupTable.lineupTable--soccer")
            if not ok:
                print(f"[{country}:{league}:{team}] スカッド取得失敗: goto failed")
                return

            players: List[Tuple[str, str, str, str, str]] = []
            tables = await page.query_selector_all("div.lineupTable.lineupTable--soccer")
            for t in tables or []:
                pos_el = await t.query_selector("div.lineupTable__title")
                position = (await pos_el.text_content()).strip() if pos_el else "N/A"
                rows = await t.query_selector_all("div.lineupTable__row")
                for r in rows:
                    jersey_el = await r.query_selector(".lineupTable__cell--jersey")
                    jersey = (await jersey_el.text_content()).strip() if jersey_el else "N/A"

                    name_el = await r.query_selector(".lineupTable__cell--player .lineupTable__cell--name")
                    if not name_el:
                        continue
                    name = (await name_el.text_content()).strip()
                    href2 = await name_el.get_attribute("href")
                    if not href2:
                        continue

                    goal_el = await r.query_selector(".lineupTable__cell--goal")
                    goals = (await goal_el.text_content()).strip() if goal_el else "N/A"

                    players.append((position, jersey, name, href2, goals))

            if not players:
                print(f"[{country}:{league}:{team}] 選手0件")
                return

            BATCH = max(PLAYER_PER_TEAM * 4, 4)

            async def handle(p):
                position, jersey, name, href2, goals = p
                key = (country, league, team, name)

                async with claim_lock:
                    if key in existing_keys or key in claimed_keys:
                        return
                    claimed_keys.add(key)

                purl = href2 if href2.startswith("http") else f"https://flashscore.co.jp{href2}"
                tab = await pool.acquire()
                try:
                    detail = await scrape_player_detail_with_page(tab, purl)
                finally:
                    await pool.release(tab)

                row = [
                    country, league, team, name, position, jersey, goals,
                    detail.get("age_text","N/A"), detail.get("birth_date","N/A"),
                    detail.get("market_value","N/A"), detail.get("loan_info","N/A"),
                    detail.get("contract_date","N/A"), detail.get("img_url","N/A"),
                    detail.get("injury_text","N/A"),
                    datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
                ]
                async with rows_lock:
                    rows_to_write.append(row)

            for i in range(0, len(players), BATCH):
                await asyncio.gather(*(handle(p) for p in players[i:i+BATCH]))

            if rows_to_write:
                if LOG_EVERY_ENQUEUE:
                    print(f"[ENQUEUE] {country} / {league} / {team}: {len(rows_to_write)}")
                writer.enqueue_team_rows(rows_to_write, f"{country}/{league}/{team}")

            print(f"[TEAM-DONE] {country} / {league} / {team}")

        except Exception as e:
            print(f"[{country}:{league}:{team}] 処理中エラー: {e}")
        finally:
            try:
                await pool.close()
            except Exception:
                pass
            try:
                await page.close()
            except Exception:
                pass

# =========================
# main / handler
# =========================
async def main():
    os.makedirs(TEAMS_BY_LEAGUE_DIR, exist_ok=True)

    print(datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S"), "TeamMemberData start")
    print(f"[CONF] S3_BUCKET={S3_BUCKET} IN={S3_INPUT_PREFIX or '(none)'} OUT={S3_OUTPUT_PREFIX or '(none)'} "
          f"JSON_KEY={S3_JSON_KEY or '(none)'} RESTORE_OUTPUT={int(RESTORE_OUTPUT)} "
          f"OUT_PREFIX={OUT_PREFIX} RUN_ID={RUN_ID} USE_RUN_ID_PREFIX={int(USE_RUN_ID_PREFIX)}")

    # 1) 入力CSVをS3から取得（必要なら）
    s3_download_teamdata_if_needed()

    # 2) JSONをS3から取得（必要なら）
    s3_download_json_if_needed()

    # 3) 既存出力をS3から復元（重複排除を効かせたいなら）
    s3_restore_existing_outputs()

    # 4) 既取得キー（既存出力CSVを読む）
    existing_keys = load_existing_player_keys()
    print(f"[INIT] 既取得キー: {len(existing_keys)} 件")

    # 5) teamData_*.csv を全部読む
    team_rows: List[Tuple[str, str, str, str]] = []
    paths = list_teamdata_csv_paths()
    if not paths:
        print(f"[EXIT] teamData_*.csv が見つかりません: {TEAMDATA_GLOB}")
        return

    for p in paths:
        rows = read_teams_from_file(p)
        print(f"[LOAD] {os.path.basename(p)}: {len(rows)} 行")
        team_rows.extend(rows)

    # 重複除去
    seen: Set[Tuple[str, str, str, str]] = set()
    uniq_rows: List[Tuple[str, str, str, str]] = []
    for row in team_rows:
        if row not in seen:
            seen.add(row)
            uniq_rows.append(row)

    print(f"[PLAN] 処理チーム数（重複除去後）: {len(uniq_rows)}")

    # 6) フィルタ（JSON優先 / ないならCONTAINS）
    json_targets = extract_country_leagues_map(B001_JSON_PATH)

    p_json = Path(B001_JSON_PATH).expanduser()
    if p_json.exists():
        json_targets = extract_country_leagues_map(B001_JSON_PATH)
        before = len(uniq_rows)
        uniq_rows = [r for r in uniq_rows if (r[0] in json_targets and r[1] in json_targets[r[0]])]
        print(f"[FILTER] JSON(存在) / チーム {before} -> {len(uniq_rows)} (targets={sum(len(v) for v in json_targets.values())})")

        # JSONが存在するのに0件になったら、JSONの中身/形式が想定と違う可能性が高いので明示終了
        if not uniq_rows:
            print("[EXIT] b001_country_league.json は存在しますが、対象チームが0件です。JSONの国名/リーグ名の表記ゆれ、またはJSON形式を確認してください。")
            return
    else:
        contains_targets = build_targets_from_contains()
        before = len(uniq_rows)
        uniq_rows = [r for r in uniq_rows if (r[0] in contains_targets and r[1] in contains_targets[r[0]])]
        print(f"[FILTER] JSON(なし) / チーム {before} -> {len(uniq_rows)}")

    if not uniq_rows:
        print("[EXIT] フィルタ後の対象チームが0件")
        return

    # 7) Writer 起動
    writer = CsvWriter(TEAMS_BY_LEAGUE_DIR, OUT_PREFIX, OUT_MAX_RECORDS)
    await writer.start()

    claim_lock = asyncio.Lock()
    team_sema = asyncio.Semaphore(TEAM_CONCURRENCY)
    claimed_keys: Set[Tuple[str, str, str, str]] = set()

    async with async_playwright() as p:
        browser = await p.chromium.launch(
            headless=True,
            args=[
                "--disable-gpu",
                "--no-sandbox",
                "--disable-setuid-sandbox",
                "--no-zygote",
                "--disable-dev-shm-usage",
                "--disable-background-timer-throttling",
                "--disable-renderer-backgrounding",
                "--disable-backgrounding-occluded-windows",
                "--disable-breakpad",
                "--disable-features=TranslateUI,BackForwardCache,AcceptCHFrame,MediaRouter",
                "--blink-settings=imagesEnabled=false",
            ],
        )
        ctx = await make_context(browser)

        try:
            tasks = [
                process_team(ctx, existing_keys, claimed_keys, claim_lock,
                             team_sema, writer, country, league, team, href)
                for (country, league, team, href) in uniq_rows
            ]
            results = await asyncio.gather(*tasks, return_exceptions=True)
            for r in results:
                if isinstance(r, Exception):
                    print(f"[WARN] タスク例外: {r}")
        finally:
            await ctx.close()
            await browser.close()

    # 8) Writer 停止（flush+S3 upload）
    await writer.stop()

    # 9) 念のため、出力CSVを全部S3へ再アップロード（保険）
    out_paths = sorted(glob.glob(os.path.join(TEAMS_BY_LEAGUE_DIR, f"{OUT_PREFIX}[0-9]*.csv")))
    print(f"[UPLOAD-ALL] outputs: {len(out_paths)} files")
    for lp in out_paths:
        key = s3_output_key(os.path.basename(lp))
        s3_upload_file(lp, key)

    print("TeamMemberData done")

def handler(event, context):
    try:
        asyncio.run(main())
    except RuntimeError:
        loop = asyncio.get_event_loop()
        loop.run_until_complete(main())
    return {"statusCode": 200, "body": "ok"}

if __name__ == "__main__":
    asyncio.run(main())
