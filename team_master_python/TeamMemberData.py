# -*- coding: utf-8 -*-
"""
TeamMemberExcel_writer_queue.py
- teams_by_league/*.xlsx を全読み
- 6〜10チーム並列処理（TEAM_CONCURRENCY）
- 各チームは PagePool(PLAYER_PER_TEAM) で選手詳細を並列取得
- page.goto の実同時数と発火間隔を制御（GLOBAL_NAV_CONCURRENCY / NAV_GAP_MS）
- 不要リソース/広告/同意ドメインは遮断
- Excel 書込は専用 Writer タスクに集約（バッファ追記＋デバウンス保存）
"""
import json
from pathlib import Path
import os
import re
import glob
import asyncio
import datetime
from typing import List, Tuple, Dict, Set, Optional
import csv

from openpyxl import Workbook, load_workbook
from playwright.async_api import async_playwright

# =========================
# 設定
# =========================
BASE_DIR = "/Users/shiraishitoshio/bookmaker"
TEAMS_BY_LEAGUE_DIR = os.path.join(BASE_DIR, "teams_by_league")
B001_JSON_PATH = "/Users/shiraishitoshio/bookmaker/json/b001/b001_country_league.json"

EXCEL_BASE_PREFIX = "team_member_"
EXCEL_MAX_RECORDS = 50

# タイムアウト(ms)
OP_TIMEOUT = 5000
NAV_TIMEOUT = 12000
SEL_TIMEOUT = 20000

# 並列度
TEAM_CONCURRENCY       = 10     # 同時チーム
PLAYER_PER_TEAM        = 1      # チーム内の同時タブ（詳細用 Page プール）
GLOBAL_NAV_CONCURRENCY = 3      # 全体の同時 goto 上限
NAV_GAP_MS             = 150    # goto の最小発火間隔

# Writer の保存ポリシー（状況に合わせて調整）
SAVE_INTERVAL_SEC      = 2.0    # これ秒ごとにバッファがあれば保存
SAVE_EVERY_N_ROWS      = 80     # これ行以上たまったら即保存（50刻み回避のためやや大きめ）
LOG_EVERY_ENQUEUE      = True   # True: チームごとにENQUEUEログを出す

# ブロックする外部ドメイン
BLOCKED_HOST_KEYWORDS = [
    "googletagmanager", "google-analytics", "doubleclick", "googlesyndication",
    "scorecardresearch", "criteo", "adsystem", "mathtag", "quantserve",
    "taboola", "facebook", "twitter", "hotjar", "onetrust", "cookiebot",
    "trustarc", "ioam.de", "amazon-adsystem", "adservice"
]

# =========================
# ユーティリティ
# =========================
def ensure_dirs():
    os.makedirs(BASE_DIR, exist_ok=True)
    os.makedirs(TEAMS_BY_LEAGUE_DIR, exist_ok=True)

def load_existing_player_keys_from_excels() -> Set[Tuple[str, str, str, str]]:
    """既存の team_member_*.xlsx から (国,リーグ,チーム,選手名) を集合化（重複除去用）"""
    s: Set[Tuple[str, str, str, str]] = set()
    for path in sorted(glob.glob(os.path.join(BASE_DIR, f"{EXCEL_BASE_PREFIX}*.xlsx"))):
        try:
            wb = load_workbook(path, data_only=True, read_only=True)
            ws = wb.active
            for row in ws.iter_rows(min_row=2, values_only=True):
                if not row or row[0] is None:
                    continue
                s.add((str(row[0]), str(row[1]), str(row[2]), str(row[3])))
        except Exception as e:
            print(f"[WARN] 既存Excel読込失敗: {path} / {e}")
    return s

def read_teams_from_file(path: str) -> List[Tuple[str, str, str, str]]:
    """
    teams_by_league/*.csv を読み込み、(国, リーグ, チーム, チームリンク) のリストを返す。
    期待ヘッダー: 国, リーグ, チーム, チームリンク
    ※ 多少ヘッダー揺れがあっても拾えるようにする
    """
    out: List[Tuple[str, str, str, str]] = []

    # ヘッダー揺れに対応（必要なら増やしてOK）
    KEYMAP = {
        "国": ["国", "country", "Country"],
        "リーグ": ["リーグ", "league", "League"],
        "チーム": ["チーム", "team", "Team"],
        "チームリンク": ["チームリンク", "チームURL", "チームurl", "href", "link", "url", "チームリンクURL"],
    }

    def norm(s: str) -> str:
        return (s or "").strip()

    try:
        # Excel で作ったCSVは utf-8-sig のことが多い
        with open(path, "r", encoding="utf-8-sig", newline="") as f:
            reader = csv.DictReader(f)
            if not reader.fieldnames:
                return out

            # 実際のヘッダー名 → 正規キー へ解決
            resolved: Dict[str, str] = {}  # 正規キー -> 実ヘッダー
            fields = [h.strip() for h in reader.fieldnames if h]
            for canonical, aliases in KEYMAP.items():
                for a in aliases:
                    if a in fields:
                        resolved[canonical] = a
                        break

            # どうしても見つからない場合は先頭から fallback（旧実装互換）
            # 国=0, リーグ=1, チーム=2, チームリンク=3 を想定
            use_fallback = any(k not in resolved for k in ("国", "リーグ", "チーム", "チームリンク"))

            if use_fallback:
                # DictReader をやめて通常 reader で読み直す
                f.seek(0)
                r = csv.reader(f)
                header = next(r, [])
                for row in r:
                    if not row:
                        continue
                    c = norm(row[0] if len(row) > 0 else "")
                    l = norm(row[1] if len(row) > 1 else "")
                    t = norm(row[2] if len(row) > 2 else "")
                    h = norm(row[3] if len(row) > 3 else "")
                    if c and l and t and h:
                        out.append((c, l, t, h))
                return out

            for row in reader:
                c = norm(row.get(resolved["国"], ""))
                l = norm(row.get(resolved["リーグ"], ""))
                t = norm(row.get(resolved["チーム"], ""))
                h = norm(row.get(resolved["チームリンク"], ""))
                if c and l and t and h:
                    out.append((c, l, t, h))

    except Exception as e:
        print(f"[WARN] チームCSV読込失敗: {path} / {e}")

    return out


# =========================
# Playwright ヘルパ
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
        user_agent=("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
                    "AppleWebKit/537.36 (KHTML, like Gecko) "
                    "Chrome/124.0.0.0 Safari/537.36"),
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
                try:    await p.close()
                except: pass

# =========================
# Excel Writer（専用タスク）
# =========================
class ExcelWriter:
    """
    - Queue に [row, row, ...] の「チーム単位バッチ」を入れる
    - 受け取り次第、現在開いているワークブックに append
    - 50行超えたら自動ローテーション
    - SAVE_INTERVAL_SEC ごと or SAVE_EVERY_N_ROWS ごとに保存
    - 既存/実行中重複はメモリ集合（existing_keys / claimed_keys）で吸収
    """
    def __init__(self, base_dir: str, prefix: str, max_records: int):
        self.base_dir = base_dir
        self.prefix = prefix
        self.max_records = max_records
        self.q: asyncio.Queue = asyncio.Queue()
        self._task: Optional[asyncio.Task] = None

        self.wb = None
        self.ws = None
        self.seq = -1
        self.rows_in_current = 0
        self._dirty_since_last_save = 0

    def _scan_next_seq(self) -> int:
        existing = sorted(glob.glob(os.path.join(self.base_dir, f"{self.prefix}[0-9]*.xlsx")))
        if not existing:
            return 1
        # 最大番号を拾う
        def num(p):
            m = re.search(rf"{re.escape(self.prefix)}(\d+)\.xlsx$", os.path.basename(p))
            return int(m.group(1)) if m else 0
        last = max(existing, key=num)
        return num(last) if num(last) > 0 else 1

    def _open_initial(self):
        start_seq = self._scan_next_seq()
        # 既存ファイルの末尾に続きを書く（空きがあれば）
        for seq in range(start_seq, start_seq + 2):
            path = os.path.join(self.base_dir, f"{self.prefix}{seq}.xlsx")
            if os.path.exists(path):
                wb = load_workbook(path)
                ws = wb.active
                used = max(ws.max_row - 1, 0)
                if used < self.max_records:
                    self.wb, self.ws, self.seq = wb, ws, seq
                    self.rows_in_current = used
                    return
        # 空きが無ければ新規を開く
        self._rotate_new(start_seq if not os.path.exists(os.path.join(self.base_dir, f"{self.prefix}{start_seq}.xlsx")) else start_seq + 1)

    def _rotate_new(self, seq_new: int):
        path = os.path.join(self.base_dir, f"{self.prefix}{seq_new}.xlsx")
        wb = Workbook()
        ws = wb.active
        ws.append([
            "国","リーグ","所属チーム","選手名","ポジション","背番号",
            "得点数","年齢","誕生日","市場価値","ローン保有元",
            "契約期限","顔写真","故障情報","データ取得時間"
        ])
        self.wb, self.ws, self.seq = wb, ws, seq_new
        self.rows_in_current = 0
        self._save_now()
        print(f"[WRITER] Rotate -> {os.path.basename(path)}")

    def _save_now(self):
        path = os.path.join(self.base_dir, f"{self.prefix}{self.seq}.xlsx")
        self.wb.save(path)
        self._dirty_since_last_save = 0
        # 一応 flush ログ
        # print(f"[WRITER] Saved: {os.path.basename(path)} ({self.rows_in_current} rows)")

    def enqueue_team_rows(self, rows: List[List[object]], label: str):
        # すぐ返す（非同期書込）
        self.q.put_nowait((rows, label))

    async def run(self):
        # 初期オープン
        self._open_initial()
        last_save = asyncio.get_running_loop().time()

        while True:
            timeout = SAVE_INTERVAL_SEC
            try:
                rows, label = await asyncio.wait_for(self.q.get(), timeout=timeout)
            except asyncio.TimeoutError:
                # 期限で保存
                if self._dirty_since_last_save > 0:
                    self._save_now()
                last_save = asyncio.get_running_loop().time()
                continue

            if rows is None:   # 終了シグナル
                if self._dirty_since_last_save > 0:
                    self._save_now()
                break

            # 50件単位ローテーションに合わせて分割 append
            offset = 0
            while offset < len(rows):
                remain = self.max_records - self.rows_in_current
                chunk = rows[offset: offset + remain]
                for r in chunk:
                    self.ws.append(r)
                self.rows_in_current += len(chunk)
                self._dirty_since_last_save += len(chunk)
                offset += len(chunk)

                if self.rows_in_current >= self.max_records:
                    self._save_now()
                    self._rotate_new(self.seq + 1)

            if LOG_EVERY_ENQUEUE:
                print(f"[WRITER] APPEND {label}: +{len(rows)} rows -> file #{self.seq} (cur={self.rows_in_current})")

            # 閾値超えたら即保存
            if self._dirty_since_last_save >= SAVE_EVERY_N_ROWS:
                self._save_now()
                last_save = asyncio.get_running_loop().time()

    async def start(self):
        if self._task is None:
            self._task = asyncio.create_task(self.run())

    async def stop(self):
        # 終了シグナル
        await self.q.put((None, "STOP"))
        if self._task:
            await self._task

# =========================
# スクレイピング本体
# =========================
async def scrape_player_detail_with_page(page, full_url: str) -> Dict[str, str]:
    age = birth = mv = contract = loan = img = inj = "N/A"
    ok = await safe_goto(page, full_url, "div.playerInfoItem")
    if not ok:
        return {
            "age_text": age, "birth_date": birth, "market_value": mv,
            "loan_info": loan, "contract_date": contract,
            "img_url": img, "injury_text": inj
        }
    try:
        el = await page.query_selector('div[data-testid="wcl-assetContainerBoxed-XL"] img')
        if el:
            img = await el.get_attribute("src") or "N/A"
    except Exception:
        pass

    try:
        info_items = await page.query_selector_all("div.playerInfoItem")
        for it in info_items:
            lab_el = await it.query_selector("strong")
            lab = (await lab_el.text_content()).strip() if lab_el else ""
            vals = await it.query_selector_all("span") or []
            if "年齢" in lab:
                if len(vals) >= 1: age = (await vals[0].text_content()).strip()
                if len(vals) >= 2: birth = (await vals[1].text_content()).strip().strip("()")
            elif "契約期限" in lab and vals:
                contract = (await vals[0].text_content()).strip()
            elif "市場価値" in lab and vals:
                mv = (await vals[0].text_content()).strip()
            elif "ローン元" in lab:
                if len(vals) >= 1: loan = (await vals[0].text_content()).strip()
                if len(vals) >= 2:
                    contract = (await vals[1].text_content()).strip().replace("期限:", "").strip("() ").strip()
    except Exception as e:
        print(f"[PLAYER DETAIL] parse失敗: {full_url} / {e}")

    return {
        "age_text": age, "birth_date": birth, "market_value": mv,
        "loan_info": loan, "contract_date": contract,
        "img_url": img, "injury_text": inj
    }

async def process_team(ctx,
                       existing_keys: Set[Tuple[str, str, str, str]],
                       claimed_keys: Set[Tuple[str, str, str, str]],
                       claim_lock: asyncio.Lock,
                       team_sema: asyncio.Semaphore,
                       writer: ExcelWriter,
                       country: str, league: str, team: str, href: str):
    """チーム1件（選手はPagePoolで並列）。完了後 Writer に一括投入。"""
    async with team_sema:
        page = await ctx.new_page()
        pool = PagePool(ctx, size=max(PLAYER_PER_TEAM, 1))
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

            rows_to_write: List[List[object]] = []

            # チーム内はフェアに小分け
            BATCH = max(PLAYER_PER_TEAM * 4, 4)

            async def handle(p):
                position, jersey, name, href2, goals = p
                key = (country, league, team, name)
                # 既存/実行中重複
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

                rows_to_write.append([
                    country, league, team, name, position, jersey, goals,
                    detail.get("age_text","N/A"), detail.get("birth_date","N/A"),
                    detail.get("market_value","N/A"), detail.get("loan_info","N/A"),
                    detail.get("contract_date","N/A"), detail.get("img_url","N/A"),
                    detail.get("injury_text","N/A"), datetime.datetime.now()
                ])

            for i in range(0, len(players), BATCH):
                await asyncio.gather(*(handle(p) for p in players[i:i+BATCH]))

            # チーム単位で一括投入（Writer が保存を制御）
            if rows_to_write:
                if LOG_EVERY_ENQUEUE:
                    print(f"[ENQUEUE] {country} / {league} / {team}: {len(rows_to_write)} 件をWriterへ")
                writer.enqueue_team_rows(rows_to_write, f"{country}/{league}/{team}")

            print(f"[TEAM-DONE] {country} / {league} / {team}")

        except Exception as e:
            print(f"[{country}:{league}:{team}] 処理中エラー: {e}")
        finally:
            try:    await pool.close()
            except: pass
            try:    await page.close()
            except: pass

def extract_countries(json_path: str) -> list[str]:
    """
    JSONから country を広めに抽出して返す（ユニーク＆ソート）
    """
    p = Path(json_path)
    if not p.exists():
        return []

    data = json.loads(p.read_text(encoding="utf-8"))
    countries: set[str] = set()

    def norm(s) -> str:
        return str(s).strip()

    def walk(obj):
        if isinstance(obj, dict):
            for k in ("items", "country_league", "data", "results"):
                if k in obj:
                    walk(obj[k])

            if "country" in obj:
                c = norm(obj["country"])
                if c:
                    countries.add(c)

            # country -> leagues のマップ形式も拾う
            for ck, cv in obj.items():
                if isinstance(cv, list):
                    c = norm(ck)
                    if c:
                        countries.add(c)
                    walk(cv)
                else:
                    walk(cv)

        elif isinstance(obj, list):
            for x in obj:
                walk(x)

    walk(data)
    return sorted(countries)

# =========================
# エントリポイント
# =========================
async def main():
    ensure_dirs()

    now = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    print(f"{now}  TeamMemberData: 開始（Writer/Queue 方式）")

    existing_keys = load_existing_player_keys_from_excels()
    print(f"[INIT] 既取得キー: {len(existing_keys)} 件")

    team_rows: List[Tuple[str, str, str, str]] = []
    paths = sorted(glob.glob(os.path.join(TEAMS_BY_LEAGUE_DIR, "*.csv")))
    if not paths:
        print(f"[EXIT] チームExcelが見つかりません: {TEAMS_BY_LEAGUE_DIR}")
        return

    for p in paths:
        rows = read_teams_from_file(p)
        print(f"[LOAD] {os.path.basename(p)}: {len(rows)} 行")
        team_rows.extend(rows)

    # 完全重複除去
    seen: Set[Tuple[str, str, str, str]] = set()
    uniq_rows: List[Tuple[str, str, str, str]] = []
    for row in team_rows:
        if row not in seen:
            seen.add(row)
            uniq_rows.append(row)

    print(f"[PLAN] 処理チーム数（重複除去後）: {len(uniq_rows)}")

    # =========================
    # JSONがある場合は country で絞る（なければ全件）
    # =========================
    allowed_countries: set[str] | None = None
    countries = extract_countries(B001_JSON_PATH)

    if countries:
        allowed_countries = set(countries)
        before = len(uniq_rows)
        uniq_rows = [r for r in uniq_rows if r[0] in allowed_countries]  # r[0] = country
        print(f"[FILTER] JSONあり: country {len(allowed_countries)}件 / チーム {before} -> {len(uniq_rows)}")
    else:
        print("[FILTER] JSONなし/空: 絞り込みなし（全チーム対象）")

    if not uniq_rows:
        print("[EXIT] フィルタ後の対象チームが0件のため終了します")
        return

    # ライター起動
    writer = ExcelWriter(BASE_DIR, EXCEL_BASE_PREFIX, EXCEL_MAX_RECORDS)
    await writer.start()

    claim_lock = asyncio.Lock()
    team_sema = asyncio.Semaphore(TEAM_CONCURRENCY)
    claimed_keys: Set[Tuple[str, str, str, str]] = set()

    async with async_playwright() as p:
        browser = await p.chromium.launch(
            headless=True,
            args=[
                "--disable-gpu",
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

        tasks = [
            process_team(ctx, existing_keys, claimed_keys, claim_lock,
                         team_sema, writer, country, league, team, href)
            for (country, league, team, href) in uniq_rows
        ]
        results = await asyncio.gather(*tasks, return_exceptions=True)
        for r in results:
            if isinstance(r, Exception):
                print(f"[WARN] タスク例外: {r}")

        await ctx.close()
        await browser.close()

    # ライター停止（残りを保存して終了）
    await writer.stop()

    print("TeamMemberData: 完了（Writer/Queue 方式）")

if __name__ == "__main__":
    asyncio.run(main())
