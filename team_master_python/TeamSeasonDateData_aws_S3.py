# -*- coding: utf-8 -*-
"""
AWS(Lambda) 用：Flashscore から season_data.csv を更新して S3 に保存

✅ 先ほど（ローカル版）で入っていた改善点を AWS 版にも適用：
- 左メニュー「国」を “もっと表示” で押し切る
- 国ページでリーグリンク抽出
- 許可リスト：JSON(b001_country_league.json) 優先、無ければ CONTAINS_LIST
- 除外：Uxx / 女子 / EXP_LIST
- ✅ CONTAINS運用でマッチ0件の国は、フォールバックで「最初のまともなリーグ1件」を採用
- ✅ リーグページから timeline / icon / season_year / league_name を取得
- ✅ timeline が無ければ fixtures から開始/終了日を推定
- ✅ fixtures から「ラウンド数（最大ラウンド）」を取得
- ✅ 省メモリ：route で image/font/media をブロック
- CSV は DictReader/Writer で安全に上書き、ヘッダー保証
- S3：既存 CSV を復元→追記→更新→アップロード（2回）
"""

import os
import re
import json
import csv
import asyncio
import datetime
from pathlib import Path
from typing import List, Tuple, Set

import boto3
from botocore.exceptions import ClientError
from playwright.async_api import async_playwright, TimeoutError as PwTimeoutError

# =========================
# 設定
# =========================
BASE_OUTPUT_URL = "/tmp/bookmaker"
os.makedirs(BASE_OUTPUT_URL, exist_ok=True)

SEASON_CSV = str(Path(BASE_OUTPUT_URL) / "season_data.csv")

BASE_URL = "https://www.flashscore.co.jp"
SEASON_YEAR_HEADER = "シーズン年"
ROUND_HEADER = "ラウンド数"
ICON_HEADER = "リーグアイコン"

# 入力JSON（/tmpに置く前提なので、S3から落とす）
B001_JSON_PATH = str(Path(BASE_OUTPUT_URL) / "json/b001/b001_country_league.json")

# S3（固定バケット）
S3_BUCKET = "aws-s3-team-season-date-data"
S3_PREFIX = os.getenv("S3_PREFIX", "").strip()  # 例: "season/"

# 入力JSONをS3から落とす場合（任意）
# 例: season/json/b001_country_league.json
S3_INPUT_JSON_KEY = os.getenv("S3_INPUT_JSON_KEY", "").strip()  # 空なら「S3から落とさない」

# Playwright timeouts
OP_TIMEOUT = 5000
NAV_TIMEOUT = 15000

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

CSV_HEADER = ["国", "リーグ", SEASON_YEAR_HEADER, "シーズン開始", "シーズン終了", ROUND_HEADER, "パス", ICON_HEADER]

# =========================
# S3 helpers
# =========================
def _norm_prefix(prefix: str) -> str:
    prefix = (prefix or "").strip()
    return (prefix.strip("/") + "/") if prefix else ""

def s3_key_for_season_csv(local_path: str) -> str:
    return _norm_prefix(S3_PREFIX) + os.path.basename(local_path)

def s3_download_if_exists(key: str, local_path: str) -> bool:
    s3 = boto3.client("s3")
    try:
        s3.head_object(Bucket=S3_BUCKET, Key=key)
    except ClientError:
        return False

    os.makedirs(os.path.dirname(local_path), exist_ok=True)
    s3.download_file(S3_BUCKET, key, local_path)
    print(f"[S3 DOWNLOAD] s3://{S3_BUCKET}/{key} -> {local_path}")
    return True

def s3_upload_file(local_path: str, key: str) -> bool:
    s3 = boto3.client("s3")
    try:
        s3.upload_file(local_path, S3_BUCKET, key)
        print(f"[S3 UPLOAD] s3://{S3_BUCKET}/{key}")
        return True
    except ClientError as e:
        print(f"[S3 ERROR] upload failed: {local_path} -> s3://{S3_BUCKET}/{key} ({e})")
        return False

# =========================
# Normalize / filter helpers
# =========================
def normalize_country_name(s: str) -> str:
    s = (s or "").strip().replace("　", " ")
    return re.sub(r"\s+", " ", s)

def norm(s: str) -> str:
    if s is None:
        return ""
    s = str(s).replace("\u3000", " ")
    return re.sub(r"\s+", " ", s).strip()

def norm_comp(s: str) -> str:
    return norm(s).replace(" ", "")

def norm_name(s: str) -> str:
    if s is None:
        return ""
    s = str(s).replace("\u3000", " ")
    return re.sub(r"\s+", " ", s).strip()

def is_excluded(category: str) -> bool:
    cat = norm(category)
    for w in UNDER_LIST + GENDER_LIST:
        if w and w in cat:
            return True
    for w in EXP_LIST:
        if w and w in cat:
            return True
    return False

def build_allowed_set_from_contains() -> set[str]:
    return {norm_comp(x) for x in CONTAINS_LIST}

def build_allowed_set_from_json(pairs: list[tuple[str, str]]) -> set[str]:
    return {norm_comp(f"{c}: {l}") for c, l in pairs}

def is_allowed_category(category: str, allowed_set: set[str]) -> bool:
    if is_excluded(category):
        return False
    return norm_comp(category) in allowed_set

def pick_first_league_fallback(country: str, leagues_raw: list[tuple[str, str]]) -> list[tuple[str, str]]:
    """
    CONTAINS で1件も取れない国の救済:
    leagues_raw から最初の「まともな」リーグを1件だけ返す
    """
    for league_name, path in leagues_raw:
        if is_excluded(f"{country}: {league_name}"):
            continue
        if re.search(r"(概要|順位表|結果|日程|ニュース|統計|選手|チーム)", league_name or ""):
            continue
        if path and league_name:
            return [(league_name, path)]
    return []

# =========================
# CSV storage
# =========================
def ensure_csv_header(csv_path: str) -> None:
    if not os.path.exists(csv_path) or os.path.getsize(csv_path) == 0:
        os.makedirs(os.path.dirname(csv_path), exist_ok=True)
        with open(csv_path, "w", newline="", encoding="utf-8-sig") as f:
            w = csv.writer(f)
            w.writerow(CSV_HEADER)

def load_rows(csv_path: str) -> list[dict]:
    ensure_csv_header(csv_path)
    rows: list[dict] = []
    with open(csv_path, "r", newline="", encoding="utf-8-sig") as f:
        r = csv.DictReader(f)
        for row in r:
            for k in CSV_HEADER:
                row.setdefault(k, "")
            rows.append(row)
    return rows

def save_rows(rows: list[dict], csv_path: str) -> None:
    ensure_csv_header(csv_path)
    tmp = csv_path + ".tmp"
    with open(tmp, "w", newline="", encoding="utf-8-sig") as f:
        w = csv.DictWriter(f, fieldnames=CSV_HEADER)
        w.writeheader()
        for row in rows:
            w.writerow({k: row.get(k, "") for k in CSV_HEADER})
    os.replace(tmp, csv_path)

def existing_paths_set(rows: list[dict]) -> Set[str]:
    return { (row.get("パス") or "").strip() for row in rows if (row.get("パス") or "").strip() }

# =========================
# JSON parse
# =========================
def extract_countries_and_leagues(json_path: str) -> tuple[list[str], list[tuple[str, str]]]:
    p = Path(json_path).expanduser()
    if not p.exists():
        return [], []

    data = json.loads(p.read_text(encoding="utf-8"))
    pairs: list[tuple[str, str]] = []

    if isinstance(data, dict):
        for c, v in data.items():
            country = str(c).strip()
            if not country:
                continue
            if isinstance(v, list):
                for lv in v:
                    league = str(lv).strip()
                    if league:
                        pairs.append((country, league))
            else:
                league = str(v).strip()
                if league:
                    pairs.append((country, league))

    countries = sorted({c for c, _ in pairs})
    return countries, pairs

# =========================
# Playwright helpers
# =========================
async def kill_onetrust(page):
    # 出る時だけ消す（クリック）
    try:
        btn = page.locator("#onetrust-accept-btn-handler").first
        if await btn.count() and await btn.is_visible():
            await btn.click(timeout=1500, force=True)
    except:
        pass
    # DOM側も消す（overlay保険）
    try:
        await page.evaluate("""
        () => {
          const ids = ["onetrust-consent-sdk", "onetrust-banner-sdk"];
          ids.forEach(id => document.getElementById(id)?.remove());
          document.querySelectorAll(".ot-sdk-container, .ot-sdk-row, .otOverlay, .ot-pc-footer, .ot-pc-header")
            .forEach(el => el.remove());
          const overlay = document.querySelector("#onetrust-consent-sdk, .otOverlay");
          if (overlay) overlay.style.pointerEvents = "none";
        }
        """)
    except:
        pass

async def expand_leftmenu_countries(page, max_clicks: int = 200) -> int:
    clicks = 0
    menu = page.locator("#category-left-menu")
    await menu.wait_for(state="visible", timeout=15000)

    while clicks < max_clicks:
        more = menu.locator("span.lmc__itemMore").first
        if await more.count() == 0 or not await more.is_visible():
            break

        before = await menu.locator("a.lmc__element.lmc__item[href^='/soccer/']").count()
        await more.scroll_into_view_if_needed()
        try:
            await more.click(timeout=5000)
        except:
            await page.wait_for_timeout(300)
            break

        clicks += 1

        try:
            await page.wait_for_function(
                """
                ({rootSel, aSel, before}) => {
                  const root = document.querySelector(rootSel);
                  if (!root) return true;
                  const now = root.querySelectorAll(aSel).length;
                  const more = root.querySelector("span.lmc__itemMore");
                  const moreVisible = more && more.offsetParent !== null;
                  return (now > before) || !moreVisible;
                }
                """,
                arg={"rootSel": "#category-left-menu",
                     "aSel": "a.lmc__element.lmc__item[href^='/soccer/']",
                     "before": before},
                timeout=15000
            )
        except PwTimeoutError:
            break

    return clicks

async def get_all_country_links_from_leftmenu(page) -> List[Tuple[str, str]]:
    await page.goto(BASE_URL, wait_until="domcontentloaded")
    await kill_onetrust(page)

    clicks = await expand_leftmenu_countries(page)
    print(f"[LEFTMENU] more clicked: {clicks}")

    menu = page.locator("#category-left-menu")
    items = menu.locator("a.lmc__element.lmc__item[href^='/soccer/']")
    n = await items.count()

    out: List[Tuple[str, str]] = []
    seen = set()
    for i in range(n):
        a = items.nth(i)
        href = (await a.get_attribute("href")) or ""
        name = (await a.locator("span.lmc__elementName").first.text_content()) or ""
        name = re.sub(r"\s+", " ", name).strip()
        if not re.match(r"^/soccer/[^/]+/?$", href):
            continue
        if not name:
            continue
        key = (name, href)
        if key in seen:
            continue
        seen.add(key)
        out.append((name, href))

    print(f"[LEFTMENU] countries: {len(out)}")
    return out

async def open_country_via_leftmenu(page, country: str, country_map: dict) -> bool:
    target = normalize_country_name(country)

    if target in country_map:
        href = country_map[target]
        await page.goto(BASE_URL + href, wait_until="domcontentloaded")
        await kill_onetrust(page)
        return True

    for name, href in country_map.items():
        if target in normalize_country_name(name) or normalize_country_name(name) in target:
            await page.goto(BASE_URL + href, wait_until="domcontentloaded")
            await kill_onetrust(page)
            return True

    print(f"  ❌ country href not found in leftmenu: {country}")
    return False

async def scrape_league_links_on_country_page(page) -> List[Tuple[str, str]]:
    await page.wait_for_timeout(800)

    anchors = page.locator("a[href]")
    n = await anchors.count()

    leagues: List[Tuple[str, str]] = []
    seen = set()

    for i in range(n):
        a = anchors.nth(i)
        try:
            href = (await a.get_attribute("href")) or ""
            txt = (await a.text_content()) or ""
            txt = re.sub(r"\s+", " ", txt).strip()

            if not href or not txt:
                continue

            path = href.replace(BASE_URL, "")
            if not path.startswith("/"):
                continue

            # /soccer/<country>/<league>/ を基本として拾う
            if not re.match(r"^/soccer/[^/]+/[^/]+/?$", path):
                continue

            # 明らかなナビゲーションは除外
            if re.search(r"(概要|順位表|結果|日程|ニュース|統計|選手|チーム)", txt):
                continue

            key = (txt, path)
            if key in seen:
                continue
            seen.add(key)
            leagues.append((txt, path))
        except:
            pass

    return leagues

async def fetch_league_name_from_page(page) -> str:
    """
    リーグ名をヘッダーから取得（ローカル版の改善を反映）
    優先: div.heading__title div.heading__name
    fallback: span.headerLeague__title-text
    """
    try:
        loc = page.locator("div.heading__title div.heading__name").first
        if await loc.count():
            txt = (await loc.text_content() or "").strip()
            if txt:
                return txt
    except:
        pass

    try:
        loc = page.locator("span.headerLeague__title-text").first
        if await loc.count():
            txt = (await loc.text_content() or "").strip()
            if txt:
                return txt
    except:
        pass

    return ""

async def click_show_more_fixtures_until_end(
    page,
    button_text="もっと試合を表示する",
    max_clicks=120,
    wait_timeout=15000
) -> int:
    clicks = 0
    while clicks < max_clicks:
        link = page.locator("a[data-testid='wcl-buttonLink']").filter(has_text=button_text).first
        if await link.count() == 0 or not await link.is_visible():
            break

        before = await page.locator(".event__match").count()
        await link.scroll_into_view_if_needed()
        try:
            await link.click(timeout=5000)
        except:
            break

        clicks += 1
        try:
            await page.wait_for_function(
                """
                ({before}) => {
                  const now = document.querySelectorAll(".event__match").length;
                  const btn = document.querySelector("a[data-testid='wcl-buttonLink']");
                  const visible = btn && btn.offsetParent !== null;
                  return (now > before) || !visible;
                }
                """,
                arg={"before": before},
                timeout=wait_timeout,
            )
        except PwTimeoutError:
            break
    return clicks

async def fetch_start_end_and_round_from_fixtures(page, league_path: str) -> tuple[str, str, str]:
    """
    fixtures から開始/終了日（dd.mm.）とラウンド最大値を取得
    """
    league_path = (league_path or "").strip()
    if not league_path:
        return "", "", ""

    if not league_path.endswith("/"):
        league_path += "/"

    url = BASE_URL + league_path + "fixtures/"
    await page.goto(url, wait_until="domcontentloaded")
    await kill_onetrust(page)

    try:
        await page.wait_for_selector(".event__match", timeout=12000)
    except PwTimeoutError:
        return "", "", ""

    try:
        await page.wait_for_load_state("networkidle", timeout=12000)
    except:
        pass

    await click_show_more_fixtures_until_end(page)

    # 開始/終了
    pat = re.compile(r"(\d{1,2})\.(\d{1,2})\.")
    texts = await page.locator(".event__match .event__time").all_text_contents()

    ddmms: list[str] = []
    for t in texts:
        m = pat.search(t or "")
        if not m:
            continue
        ddmms.append(f"{int(m.group(1)):02d}.{int(m.group(2)):02d}.")

    start_ddmm = ddmms[0] if ddmms else ""
    end_ddmm = ddmms[-1] if ddmms else ""

    # ラウンド数（最大ラウンド）
    round_num = ""
    try:
        rounds = await page.locator("div.event__round.event__round--static").all_text_contents()
        nums = []
        for txt in rounds:
            m = re.search(r"ラウンド\s*(\d+)", txt or "")
            if m:
                nums.append(int(m.group(1)))
        if nums:
            round_num = str(max(nums))
    except:
        pass

    return start_ddmm, end_ddmm, round_num

async def fetch_timeline_icon_season_leaguename_round(page, league_path: str) -> tuple[str, str, str, str, str, str]:
    """
    return: (start_ddmm, end_ddmm, icon_url, season_year, league_name_page, round_num)
    timeline が無い場合は fixtures へフォールバック（開始/終了/round）
    """
    league_path = (league_path or "").strip()
    if not league_path:
        return "", "", "", "", "", ""

    start_ddmm, end_ddmm, icon_url, season_year = "", "", "", ""
    round_num = ""

    if league_path.startswith("http"):
        url = league_path
    else:
        if not league_path.startswith("/"):
            league_path = "/" + league_path
        url = BASE_URL + league_path

    await page.goto(url, wait_until="domcontentloaded")
    await kill_onetrust(page)

    if "#" in page.url:
        await page.goto(url.split("#", 1)[0], wait_until="domcontentloaded")
        await kill_onetrust(page)

    try:
        await page.wait_for_load_state("networkidle", timeout=12000)
    except:
        pass

    # season year
    try:
        info = page.locator("div.heading__info").first
        if await info.count():
            season_year = (await info.text_content() or "").strip()
    except:
        pass

    league_name_page = await fetch_league_name_from_page(page)

    # timeline
    try:
        await page.wait_for_selector("#timeline span", timeout=8000)
        spans = page.locator("#timeline span")
        cnt = await spans.count()
        if cnt >= 2:
            start_ddmm = (await spans.nth(0).text_content() or "").strip()
            end_ddmm   = (await spans.nth(1).text_content() or "").strip()
        else:
            s = page.locator("#timeline span[class*='wcl-start']").first
            e = page.locator("#timeline span[class*='wcl-end']").first
            if await s.count():
                start_ddmm = (await s.text_content() or "").strip()
            if await e.count():
                end_ddmm = (await e.text_content() or "").strip()
    except:
        pass

    # icon
    try:
        img = page.locator("img.heading__logo.heading__logo--1").first
        if await img.count():
            src = await img.get_attribute("src")
            if src:
                icon_url = src.strip()

        if not icon_url:
            img2 = page.locator("img.heading__logo").first
            if await img2.count():
                src2 = await img2.get_attribute("src")
                if src2:
                    icon_url = src2.strip()
    except:
        pass

    start_ddmm = (start_ddmm or "").replace(" ", "")
    end_ddmm   = (end_ddmm or "").replace(" ", "")

    # fixtures fallback（開始/終了/round）
    if not start_ddmm or not end_ddmm or not round_num:
        fs, fe, rn = await fetch_start_end_and_round_from_fixtures(page, league_path)
        if not start_ddmm:
            start_ddmm = fs
        if not end_ddmm:
            end_ddmm = fe
        if rn:
            round_num = rn

        # season_year保険
        if not season_year:
            try:
                info = page.locator("div.heading__info").first
                if await info.count():
                    season_year = (await info.text_content() or "").strip()
            except:
                pass

        # icon保険
        if not icon_url:
            try:
                img = page.locator("img.heading__logo.heading__logo--1").first
                if await img.count():
                    src = await img.get_attribute("src")
                    if src:
                        icon_url = src.strip()
            except:
                pass

    return start_ddmm, end_ddmm, icon_url, season_year, league_name_page, round_num

# 省メモリ：ルーティングで画像等を止める
async def install_block_routes(context):
    async def _route(route):
        rtype = route.request.resource_type
        if rtype in {"image", "font", "media"}:
            await route.abort()
        else:
            await route.continue_()
    await context.route("**/*", _route)

# =========================
# Main
# =========================
async def main():
    print(datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S"), "season scrape start")

    # 1) 既存CSVをS3から復元（あれば）
    season_key = s3_key_for_season_csv(SEASON_CSV)
    s3_download_if_exists(season_key, SEASON_CSV)

    # 2) 入力JSONをS3から復元（任意）
    if S3_INPUT_JSON_KEY:
        s3_download_if_exists(S3_INPUT_JSON_KEY, B001_JSON_PATH)

    # 3) 既存CSV読み込み
    rows = load_rows(SEASON_CSV)
    existed_paths = existing_paths_set(rows)

    # 4) JSON優先・なければCONTAINS
    _, country_league_pairs = extract_countries_and_leagues(B001_JSON_PATH)
    json_has_pairs = bool(country_league_pairs)

    if json_has_pairs:
        allowed_set = build_allowed_set_from_json(country_league_pairs)
        allowed_leagues_by_country: dict[str, set[str]] = {}
        for c, l in country_league_pairs:
            c2 = norm_name(c)
            l2 = norm_name(l)
            if c2 and l2:
                allowed_leagues_by_country.setdefault(c2, set()).add(l2)
        allowed_countries = set(allowed_leagues_by_country.keys())
        print(f"[FILTER] JSONあり: countries={len(allowed_countries)} pairs={len(country_league_pairs)}")
    else:
        allowed_set = build_allowed_set_from_contains()
        allowed_countries = {norm(x.split(":", 1)[0]) for x in CONTAINS_LIST if ":" in x}
        allowed_leagues_by_country = {}
        print(f"[FILTER] JSONなし: countries={len(allowed_countries)} contains={len(CONTAINS_LIST)}")

    appended = 0
    updated = 0

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
                "--disable-features=TranslateUI,BackForwardCache,MediaRouter",
                "--blink-settings=imagesEnabled=false",
            ],
        )
        context = await browser.new_context(locale="ja-JP", timezone_id="Asia/Tokyo")
        context.set_default_timeout(OP_TIMEOUT)
        context.set_default_navigation_timeout(NAV_TIMEOUT)
        await install_block_routes(context)
        page = await context.new_page()

        # 左メニュー全国リンク
        all_countries = await get_all_country_links_from_leftmenu(page)
        country_map = {name: href for name, href in all_countries}

        # =========================
        # A) 国→リーグ抽出→追記（path）
        # =========================
        for country in sorted(allowed_countries):
            print(f"\n=== {country} ===")
            ok = await open_country_via_leftmenu(page, country, country_map)
            if not ok:
                continue

            leagues_raw = await scrape_league_links_on_country_page(page)

            # category で allowed_set を通す（JSON/contains共通）
            filtered_1: list[tuple[str, str]] = []
            for league_name, path in leagues_raw:
                category = f"{country}: {league_name}"
                if is_allowed_category(category, allowed_set):
                    filtered_1.append((league_name, path))

            # ✅ フォールバック：CONTAINS運用で0件なら先頭リーグ1件だけ採用
            if (not json_has_pairs) and (len(filtered_1) == 0):
                fb = pick_first_league_fallback(country, leagues_raw)
                if fb:
                    filtered_1 = fb
                    print(f"  [FALLBACK] pick first league: {filtered_1[0][0]}")
                else:
                    print(f"  [FALLBACK] no usable league found")

            # JSONありなら、その国で指定されたリーグ名だけにさらに絞る
            if json_has_pairs:
                allowed_leagues = allowed_leagues_by_country.get(norm_name(country), set())

                def ok_name(league_name: str) -> bool:
                    ln = norm_name(league_name)
                    if ln in allowed_leagues:
                        return True
                    ln_comp = ln.replace(" ", "")
                    return any(ln_comp == a.replace(" ", "") for a in allowed_leagues)

                leagues_final = [(name, path) for (name, path) in filtered_1 if ok_name(name)]
                print(f"  leagues matched(JSON): {len(leagues_final)} / allowed={len(allowed_leagues)}")
            else:
                leagues_final = filtered_1
                print(f"  leagues matched(CONTAINS): {len(leagues_final)} / scraped={len(leagues_raw)}")

            # 追記（重複は弾く）
            for league_name, path in leagues_final:
                if path in existed_paths:
                    continue
                rows.append({
                    "国": country,
                    "リーグ": league_name,
                    SEASON_YEAR_HEADER: "",
                    "シーズン開始": "",
                    "シーズン終了": "",
                    ROUND_HEADER: "",
                    "パス": path,
                    ICON_HEADER: "",
                })
                existed_paths.add(path)
                appended += 1

        # 追記後保存＆S3
        save_rows(rows, SEASON_CSV)
        s3_upload_file(SEASON_CSV, season_key)

        # =========================
        # B) timeline/icon/season_year/round/league_name を埋める
        # =========================
        for row in rows:
            path = (row.get("パス") or "").strip()
            if not path:
                continue

            cur_year  = (row.get(SEASON_YEAR_HEADER) or "").strip()
            cur_start = (row.get("シーズン開始") or "").strip()
            cur_end   = (row.get("シーズン終了") or "").strip()
            cur_round = (row.get(ROUND_HEADER) or "").strip()
            cur_icon  = (row.get(ICON_HEADER) or "").strip()

            # 全部埋まっていればスキップ
            if cur_year and cur_start and cur_end and cur_round and cur_icon:
                continue

            start_ddmm, end_ddmm, icon_url, season_year, league_name_page, round_num = \
                await fetch_timeline_icon_season_leaguename_round(page, path)

            # リーグ名（ページの値で上書き）
            if league_name_page:
                row["リーグ"] = league_name_page

            if season_year and not cur_year:
                row[SEASON_YEAR_HEADER] = season_year
            if start_ddmm and not cur_start:
                row["シーズン開始"] = start_ddmm
            if end_ddmm and not cur_end:
                row["シーズン終了"] = end_ddmm
            if round_num and not cur_round:
                row[ROUND_HEADER] = round_num
            if icon_url and not cur_icon:
                row[ICON_HEADER] = icon_url

            if start_ddmm or end_ddmm or icon_url or season_year or league_name_page or round_num:
                updated += 1

            await page.wait_for_timeout(120)

        # 更新後保存＆S3
        save_rows(rows, SEASON_CSV)
        s3_upload_file(SEASON_CSV, season_key)

        await context.close()
        await browser.close()

    print(f"appended={appended} updated={updated} csv={SEASON_CSV}")

def handler(event, context):
    asyncio.run(main())
    return {"statusCode": 200, "body": "ok"}
