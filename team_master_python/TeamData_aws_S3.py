# -*- coding: utf-8 -*-
"""
TeamData_aws_S3.py (Lambda向け軽量版)
- Page 1枚使い回し
- xlsx廃止 → CSVへ直接追記（重複排除しつつ）
- リーグごとに即S3アップロード
- Chromium省メモリ起動オプション
"""

import json
from pathlib import Path
import os
import re
import time
import asyncio
import datetime
from typing import List, Tuple, Dict, Set, Optional

import boto3
from botocore.exceptions import ClientError
from playwright.async_api import async_playwright

import csv  # ✅ 追加（既存CSVの安全読込に必須）

# =========================
# 設定
# =========================
EXCEL_HEADER = "teamData_"  # ファイルprefixとして維持
BASE_OUTPUT_URL = "/tmp/bookmaker"

B001_JSON_PATH = str(Path(BASE_OUTPUT_URL) / "json/b001/b001_country_league.json")
os.makedirs(BASE_OUTPUT_URL, exist_ok=True)

TEAMS_BY_LEAGUE_DIR = os.path.join(BASE_OUTPUT_URL, "teams_by_league")
os.makedirs(TEAMS_BY_LEAGUE_DIR, exist_ok=True)

# タイムアウト(ms)
OP_TIMEOUT = 5000
NAV_TIMEOUT = 10000
SEL_TIMEOUT = 20000

# S3（Lambda環境変数推奨）
S3_BUCKET = os.getenv("S3_BUCKET", "").strip()      # 例: teamData
S3_PREFIX = os.getenv("S3_PREFIX", "").strip()      # 例: teams_by_league/ など

def _norm_prefix(prefix: str) -> str:
    prefix = (prefix or "").strip()
    return (prefix.strip("/") + "/") if prefix else ""

def s3_key_for_path(local_path: str, base_dir: str) -> str:
    rel = os.path.relpath(local_path, base_dir).replace(os.sep, "/")
    return _norm_prefix(S3_PREFIX) + rel

def s3_upload_file(local_path: str, s3_key: str) -> bool:
    if not S3_BUCKET:
        print("[S3] S3_BUCKET未設定のためアップロードをスキップします。")
        return False
    s3 = boto3.client("s3")
    try:
        s3.upload_file(local_path, S3_BUCKET, s3_key)
        print(f"[S3 UPLOAD] s3://{S3_BUCKET}/{s3_key}")
        return True
    except ClientError as e:
        print(f"[S3 ERROR] upload failed: {local_path} -> s3://{S3_BUCKET}/{s3_key} ({e})")
        return False

# =========================
# 対象（国: リーグ）
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
GENDER_LIST = ["女子"]
EXP_LIST = ["ポルトガル: リーガ・ポルトガル 2", "イングランド: プレミアリーグ 2", "イングランド: プレミアリーグ U18"]

TARGETS: Optional[Dict[str, Set[str]]] = {}
for ent in CONTAINS_LIST:
    country, league = [x.strip() for x in ent.split(":", 1)]
    TARGETS.setdefault(country, set()).add(league)

# =========================
# 共通ユーティリティ
# =========================
def sanitize_filename(name: str) -> str:
    s = re.sub(r"[\\/:*?\"<>|]", "_", name)
    s = re.sub(r"\s+", "_", s.strip())
    return s

def teams_csv_path(country: str, league: str) -> str:
    filename = f"{EXCEL_HEADER}{sanitize_filename(country)}__{sanitize_filename(league)}.csv"
    return os.path.join(TEAMS_BY_LEAGUE_DIR, filename)

def _read_existing_pairs_from_csv(path: str) -> Set[Tuple[str, str]]:
    """
    ✅ 既存CSVから (team, href) の集合を読む（csv.readerで安全に）
    """
    existing: Set[Tuple[str, str]] = set()
    if not os.path.exists(path):
        return existing

    try:
        with open(path, "r", encoding="utf-8-sig", newline="") as f:
            r = csv.reader(f)
            header = next(r, None)
            if not header:
                return existing
            for row in r:
                if not row or len(row) < 4:
                    continue
                team = (row[2] or "").strip()
                href = (row[3] or "").strip()
                if team and href:
                    existing.add((team, href))
    except Exception as e:
        print(f"[WARN] 既存CSV読み込み失敗: {path} ({e})")
    return existing

def write_teams_csv(country: str, league: str, pairs: List[Tuple[str, str]]) -> Tuple[str, int]:
    """
    CSVへ追記（重複排除）。(path, added件数) を返す。
    """
    path = teams_csv_path(country, league)

    # ヘッダ保証
    if not os.path.exists(path) or os.path.getsize(path) == 0:
        with open(path, "w", encoding="utf-8-sig", newline="") as f:
            w = csv.writer(f)
            w.writerow(["国", "リーグ", "チーム", "チームリンク"])

    existing = _read_existing_pairs_from_csv(path)

    added = 0
    with open(path, "a", encoding="utf-8-sig", newline="") as f:
        w = csv.writer(f)
        for team, href in pairs:
            key = (team, href)
            if key in existing:
                continue
            w.writerow([country, league, team, href])
            existing.add(key)
            added += 1

    print(f"[WRITE] {path} に {added} 件追加（合計 {len(existing)} 件）")
    return path, added

# =========================
# Playwright ヘルパ
# =========================
async def make_context(browser, block_css: bool):
    ctx = await browser.new_context(
        locale="ja-JP", timezone_id="Asia/Tokyo",
        user_agent=("Mozilla/5.0 (X11; Linux x86_64) "
                    "AppleWebKit/537.36 (KHTML, like Gecko) "
                    "Chrome/124.0.0.0 Safari/537.36"),
        viewport={"width": 1440, "height": 900},
        ignore_https_errors=True,
    )
    ctx.set_default_timeout(OP_TIMEOUT)
    ctx.set_default_navigation_timeout(NAV_TIMEOUT)

    async def _route(route):
        rtype = route.request.resource_type
        block = {"image", "font", "media"} | ({"stylesheet"} if block_css else set())
        if rtype in block:
            await route.abort()
        else:
            await route.continue_()
    await ctx.route("**/*", _route)
    return ctx

# =========================
# メニュー → 全ブロックロード & Open
# =========================
async def load_all_country_blocks(page) -> int:
    for _ in range(60):
        try:
            if await page.is_visible("span.lmc__itemMore"):
                await page.click("span.lmc__itemMore")
                await asyncio.sleep(0.1)
            else:
                break
        except Exception:
            break

    prev = -1
    stagnant = 0
    while True:
        blocks = await page.query_selector_all("div.lmc__block")
        count = len(blocks)
        if count == prev:
            stagnant += 1
            if stagnant >= 6:
                break
        else:
            stagnant = 0
            prev = count
        try:
            await page.evaluate("window.scrollBy(0, document.body.scrollHeight)")
        except Exception:
            pass
        await asyncio.sleep(0.2)

    return max(prev, 0)

async def open_all_blocks_fast(page, overall_ms: int = 180000, batch_size: int = 120, settle_ms: int = 120) -> int:
    total = await load_all_country_blocks(page)
    print(f"country_blocks(before open): {total}")

    for sel in [
        "#onetrust-accept-btn-handler",
        "button#CybotCookiebotDialogBodyLevelButtonLevelOptinAllowAll",
        "button[aria-label='同意']",
        ".fc-consent-root button:has-text('同意')",
    ]:
        try:
            if await page.is_visible(sel):
                await page.click(sel, timeout=1000)
        except Exception:
            pass

    start = time.monotonic()
    opened_prev = -1
    no_progress = 0

    while True:
        opened = await page.locator("div.lmc__block.lmc__blockOpened").count()
        if opened >= total:
            break
        if (time.monotonic() - start) * 1000 > overall_ms:
            print(f"[WARN] open_all_blocks timeout: opened {opened}/{total}")
            break
        if opened == opened_prev:
            no_progress += 1
            if no_progress >= 12:
                print(f"[WARN] open_all_blocks no progress: {opened}/{total}")
                break
        else:
            no_progress = 0
            opened_prev = opened

        clicked = await page.evaluate(
            """
            (batch) => {
              const blocks = Array.from(document.querySelectorAll('div.lmc__block:not(.lmc__blockOpened)'));
              let n = 0;
              for (const b of blocks) {
                const a = b.querySelector('a.lmc__element.lmc__item');
                if (!a) continue;
                try {
                  a.scrollIntoView({block: 'center'});
                  a.click();
                  n++;
                  if (n >= batch) break;
                } catch(e) {}
              }
              return n;
            }
            """,
            batch_size
        )
        await page.wait_for_timeout(settle_ms)

        try:
            await page.evaluate("window.scrollBy(0, document.body.scrollHeight)")
        except Exception:
            pass

        new_opened = await page.locator("div.lmc__block.lmc__blockOpened").count()
        print(f"open progress: {new_opened}/{total} (+{new_opened - opened}, clicked {clicked})")
        if clicked == 0 and new_opened == opened:
            no_progress += 3
            if no_progress >= 12:
                print(f"[WARN] open_all_blocks stuck: {new_opened}/{total}")
                break

    opened_final = await page.locator("div.lmc__block.lmc__blockOpened").count()
    print(f"opened_blocks: {opened_final}/{total}")
    return opened_final

# =========================
# standings 到達＆待機
# =========================
STANDINGS_WAIT_SELECTORS = [
    # “ページに着いた”判定（これが重要）
    "div.tableWrapper",
    "div[data-testid='wcl-tabs']",
    "div#tournamentTable",

    # 順位表（従来）
    "a.tableCellParticipant__image",
    "a.tableCellParticipant__name",
    ".ui-table__body .ui-table__row",
    ".table__cell--participant a",
    ".tableWrapper .ui-table",
]

async def wait_any_selector(page, selectors, timeout_ms=SEL_TIMEOUT) -> str:
    deadline = time.time() + (timeout_ms / 1000)
    for sel in selectors:
        remaining = int(max(0.0, deadline - time.time()) * 1000)
        if remaining <= 0:
            break
        try:
            await page.wait_for_selector(sel, state="attached", timeout=remaining)
            return sel
        except Exception:
            continue
    raise TimeoutError("wait_any_selector: timeout")

def extract_comp_id(s: str) -> Optional[str]:
    if not s:
        return None
    token = s.split("_")[-1].strip()
    if re.fullmatch(r"[A-Za-z0-9]{6,}", token):
        return token
    m = re.search(r"([A-Za-z0-9]{6,})", s)
    return m.group(1) if m else None

async def goto_standings_with_redirect(page, base_href: str, comp_id: Optional[str]) -> bool:
    base_href = (base_href or "").rstrip("/") + "/"
    base = "https://flashscore.co.jp" + base_href + "standings/"

    async def _goto(url, tries=2) -> bool:
        for i in range(tries):
            try:
                await page.goto(url, wait_until="domcontentloaded", timeout=NAV_TIMEOUT)
                hit = await wait_any_selector(page, STANDINGS_WAIT_SELECTORS, timeout_ms=SEL_TIMEOUT)
                print(f"[NAV OK] {url} (waited: {hit})")
                return True
            except Exception as e:
                print(f"[NAV RETRY {i+1}/{tries}] {url} ({e.__class__.__name__})")
                await asyncio.sleep(0.8*(i+1))
        return False

    if comp_id:
        url = f"{base}#/{comp_id}/table/overall"
        if await _goto(url, tries=2):
            return True

    if await _goto(base, tries=2):
        return True

    try:
        await page.goto(base, wait_until="commit", timeout=NAV_TIMEOUT)
        loc = page.locator("a[href*='/standings/#/']")
        if await loc.count() > 0:
            href2 = await loc.first.get_attribute("href")
            if href2:
                abs_url = href2 if href2.startswith("http") else "https://flashscore.co.jp" + href2
                if await _goto(abs_url, tries=2):
                    return True
    except Exception:
        pass

    hack = base + "#/table/overall"
    if await _goto(hack, tries=1):
        return True

    try:
        await page.click('a[data-analytics-alias="stats-detail_table"]', timeout=2000)
        hit = await wait_any_selector(page, STANDINGS_WAIT_SELECTORS, timeout_ms=SEL_TIMEOUT)
        print(f"[NAV OK] via stats-detail_table (waited: {hit})")
        return True
    except Exception:
        pass

    return False

async def fully_render_standings(page, max_cycles: int = 40, pause_ms: int = 150):
    try:
        await page.click('a[data-analytics-alias="stats-detail_table"]', timeout=1200)
    except Exception:
        pass

    prev_rows = -1
    stable = 0
    for _ in range(max_cycles):
        try:
            rows = await page.locator(".ui-table__body .ui-table__row").count()
        except Exception:
            rows = 0
        if rows == prev_rows:
            stable += 1
            if stable >= 3:
                break
        else:
            stable = 0
        prev_rows = rows

        try:
            await page.evaluate("""
                () => {
                  window.scrollBy(0, document.body.scrollHeight);
                  const tw = document.querySelector('.tableWrapper');
                  if (tw) tw.scrollTop = tw.scrollHeight;
                }
            """)
        except Exception:
            pass
        await asyncio.sleep(pause_ms/1000)

async def goto_first_wcl_tab(page, base_href: str) -> bool:
    """
    standings 画面で wcl-tabs が出たら、先頭タブへ強制遷移する。
    先頭タブの href="#/XXXX/" を拾って、
      1) #/XXXX/standings/overall/
      2) #/XXXX/table/overall
      3) #/XXXX/
    の順で当てにいく（draw/プレーオフに飛ばされる対策）。
    """
    try:
        tabs = page.locator("div[data-testid='wcl-tabs'][role='tablist'] a[href^='#/']")
        if await tabs.count() == 0:
            return False

        first_a = tabs.first
        href = (await first_a.get_attribute("href")) or ""
        m = re.match(r"^#\/([A-Za-z0-9]+)\/?$", href.strip())
        if not m:
            return False
        sid = m.group(1)

        async def _try_hash(hash_value: str) -> bool:
            try:
                await page.evaluate("(h) => { location.hash = h; }", hash_value)
                await page.wait_for_timeout(250)
                await wait_any_selector(page, [
                    ".ui-table__body a[href^='/team/']",
                    "a.tableCellParticipant__name[href^='/team/']",
                    "a.tableCellParticipant__image[href^='/team/']",
                    ".ui-table__body .ui-table__row",
                    "div.tableWrapper",
                    "div[data-testid='wcl-tabs']",
                ], timeout_ms=SEL_TIMEOUT)
                return True
            except Exception:
                return False

        if await _try_hash(f"#/{sid}/standings/overall/"):
            print(f"[WCL] switched to first tab: #/{sid}/standings/overall/")
            return True

        if await _try_hash(f"#/{sid}/table/overall"):
            print(f"[WCL] switched to first tab: #/{sid}/table/overall")
            return True

        if await _try_hash(f"#/{sid}/"):
            print(f"[WCL] switched to first tab: #/{sid}/")
            return True

        # 最後の保険：クリック
        try:
            await first_a.click(timeout=1200)
            await page.wait_for_timeout(300)
            await wait_any_selector(page, [
                ".ui-table__body a[href^='/team/']",
                ".ui-table__body .ui-table__row",
                "div.tableWrapper",
            ], timeout_ms=SEL_TIMEOUT)
            print("[WCL] clicked first tab anchor")
            return True
        except Exception:
            pass

        return False

    except Exception as e:
        print(f"[WCL WARN] goto_first_wcl_tab failed: {e.__class__.__name__}")
        return False

# =========================
# 国×リーグの抽出
# =========================
async def collect_target_leagues(page, allowed_countries: Optional[Set[str]] = None) -> List[Tuple[str,str,str,Optional[str]]]:
    await page.goto("https://flashscore.co.jp/soccer/", wait_until="commit")
    await page.wait_for_selector("div.lmc__block")

    opened = await open_all_blocks_fast(page, overall_ms=180000, batch_size=120, settle_ms=120)
    print(f"opened_blocks: {opened}")

    leagues: List[Tuple[str,str,str,Optional[str]]] = []
    blocks = await page.query_selector_all("div.lmc__block.lmc__blockOpened")
    for b in blocks:
        cspan = await b.query_selector("span.lmc__elementName")
        country = (await cspan.inner_text()).strip() if cspan else ""

        if allowed_countries is not None and country not in allowed_countries:
            continue
        if TARGETS is not None and country not in TARGETS:
            continue

        links = await b.query_selector_all("span.lmc__template a.lmc__templateHref") \
             or await b.query_selector_all("span.lmc__template a")

        for a in links:
            league_name = (await a.inner_text()).strip()
            if TARGETS is not None:
                if league_name not in TARGETS.get(country, set()):
                    continue

            key = f"{country}: {league_name}"
            if any(key.endswith(x) for x in EXP_LIST) or any(g in key for g in GENDER_LIST):
                continue

            href = await a.get_attribute("href")
            if not href:
                continue

            comp_id: Optional[str] = None
            try:
                parent = await a.evaluate_handle("el => el.parentElement")
                if parent:
                    pin = await parent.query_selector("span.pin")
                    if pin:
                        label_key = await pin.get_attribute("data-label-key") or ""
                        classes = await pin.get_attribute("class") or ""
                        comp_id = extract_comp_id(label_key) or extract_comp_id(classes)
            except Exception:
                pass

            print(f"   - {league_name} -> {href} (compId={comp_id or '-'})")
            leagues.append((country, league_name, href, comp_id))

    return leagues

# =========================
# リーグ → チーム（Page使い回し版）
# =========================
async def scrape_league_to_teams_on_page(
    page,
    country: str,
    league: str,
    href: str,
    comp_id: Optional[str]
) -> List[Tuple[str, str]]:
    """
    - goto_standings_with_redirect が False でも、wcl-tabs/hash切替で復帰できる可能性があるため
      途中returnしない（=以降の処理を継続して最終条件を満たすか試す）。
    - 最終成功条件は「/team/ リンクがDOMに現れること」。
    - 失敗しても例外で落とさず、[] を返して呼び出し側で次リーグへ進める。
    """
    teams: List[Tuple[str, str]] = []

    # 0) まずstandingsへ（compId優先だが失敗しても続行）
    ok = await goto_standings_with_redirect(page, href, comp_id)
    if not ok:
        print(f"[WARN] standings到達失敗: {country} / {league} ({href}) -> 救済ルート継続")

    # 1) wcl-tabs があるなら先頭タブへ寄せる（降格戦/プレーオフ等の回避）
    try:
        await goto_first_wcl_tab(page, href)
    except Exception as e:
        print(f"[WCL WARN] goto_first_wcl_tab exception: {e.__class__.__name__}")

    # 2) 最終成功条件：/team/ が出るまで待つ（出なければこのリーグは諦める）
    try:
        await wait_any_selector(page, [
            ".ui-table__body a[href^='/team/']",
            "a.tableCellParticipant__name[href^='/team/']",
            "a.tableCellParticipant__image[href^='/team/']",
            ".table__cell--participant a[href^='/team/']",
            # “到着”判定も保険で
            "div.tableWrapper",
            "div#tournamentTable",
            "div[data-testid='wcl-tabs']",
        ], timeout_ms=SEL_TIMEOUT)
    except Exception:
        print(f"[WARN] {country}:{league} standings基盤DOMが出ず終了 ({page.url})")
        return []

    # /team/ が本当に出るまで（wcl-tabsだけ出てテーブル未ロードのケースがある）
    try:
        await wait_any_selector(page, [
            ".ui-table__body a[href^='/team/']",
            "a.tableCellParticipant__name[href^='/team/']",
            "a.tableCellParticipant__image[href^='/team/']",
            ".table__cell--participant a[href^='/team/']",
        ], timeout_ms=SEL_TIMEOUT)
    except Exception:
        print(f"[WARN] {country}:{league} /team/ が見つからず終了 ({page.url})")
        return []

    # 3) 仮想化対策：行数が増えなくなるまで描画
    try:
        await fully_render_standings(page)
    except Exception as e:
        print(f"[WARN] fully_render_standings failed: {country}:{league} ({e.__class__.__name__})")

    # 4) まずはDOMから抽出
    tmp: List[Tuple[str, str]] = []
    try:
        anchors = await page.query_selector_all(
            ".ui-table__body a[href^='/team/'], "
            "a.tableCellParticipant__image, "
            "a.tableCellParticipant__name"
        )
        for a in anchors:
            href2 = (await a.get_attribute("href")) or ""
            if not href2.startswith("/team/"):
                continue
            name = (await a.get_attribute("title")) or (await a.text_content()) or ""
            name = name.strip()
            if not name:
                continue
            tmp.append((name, href2))
    except Exception as e:
        print(f"[WARN] DOM抽出失敗: {country}:{league} ({e.__class__.__name__})")

    # 5) 0件ならHTMLフォールバック
    if not tmp:
        try:
            html = await page.content()
            # title優先
            for m in re.finditer(r'<a[^>]+href="(/team/[^"]+/)"[^>]*title="([^"]+)"', html):
                tmp.append((m.group(2).strip(), m.group(1)))
            # テキスト優先
            for m in re.finditer(r'<a[^>]+href="(/team/[^"]+/)"[^>]*>([^<]+)</a>', html):
                nm = re.sub(r"\s+", " ", m.group(2)).strip()
                if nm:
                    tmp.append((nm, m.group(1)))
        except Exception as e:
            print(f"[WARN] HTMLフォールバック失敗: {country}:{league} ({e.__class__.__name__})")

    # 6) 重複除去
    seen: Set[Tuple[str, str]] = set()
    for t in tmp:
        if t in seen:
            continue
        seen.add(t)
        teams.append(t)

    print(f"[{country}:{league}] チーム抽出: {len(teams)}件")
    return teams

# =========================
# JSON 読み込み
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
# 実行
# =========================
async def main():
    global TARGETS

    now = datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    print(f"{now}  データ取得対象の時間です。（SEED）")

    # JSON 指定があればそれだけ回す
    _, country_league_pairs = extract_countries_and_leagues(B001_JSON_PATH)
    json_has_pairs = bool(country_league_pairs)

    if json_has_pairs:
        json_targets: Dict[str, Set[str]] = {}
        for c, l in country_league_pairs:
            c2 = re.sub(r"\s+", " ", str(c or "")).strip()
            l2 = re.sub(r"\s+", " ", str(l or "")).strip()
            if c2 and l2:
                json_targets.setdefault(c2, set()).add(l2)

        TARGETS = json_targets
        allowed_countries: Optional[Set[str]] = set(TARGETS.keys())

        print(f"[FILTER] JSONあり: countries={len(allowed_countries)} pairs={sum(len(v) for v in TARGETS.values())}")
        print("[TARGETS]", {k: sorted(list(v)) for k, v in TARGETS.items()})
    else:
        # JSONが無い/空なら CONTAINS_LIST のみ
        # ※ 起動時に作った TARGETS をそのまま使う
        allowed_countries = set(TARGETS.keys())

        print("[FILTER] JSONなし/空: CONTAINS_LIST のみ運用")
        print("[TARGETS]", {k: sorted(list(v)) for k, v in TARGETS.items()})

    async with async_playwright() as p:
        browser = await p.chromium.launch(
            headless=True,
            args=[
                "--disable-gpu",
                "--no-sandbox",
                "--disable-setuid-sandbox",
                "--no-zygote",
                "--disable-dev-shm-usage",
            ],
        )

        # メニュー取得（CSSは必要なことが多いので block_css=False）
        menu_ctx = await make_context(browser, block_css=False)
        menu_page = await menu_ctx.new_page()
        leagues = await collect_target_leagues(menu_page, allowed_countries=allowed_countries)
        await menu_page.close()
        await menu_ctx.close()
        print(f"対象リーグ数: {len(leagues)}")

        # work page を1枚だけ使い回す
        work_ctx = await make_context(browser, block_css=True)
        work_page = await work_ctx.new_page()

        try:
            for (country, league, href, comp_id) in leagues:
                teams = await scrape_league_to_teams_on_page(work_page, country, league, href, comp_id)
                if not teams:
                    print(f"[WARN] {country}:{league} チーム取得ゼロ")
                    continue

                csv_path, _added = write_teams_csv(country, league, teams)

                key = s3_key_for_path(csv_path, base_dir=TEAMS_BY_LEAGUE_DIR)
                s3_upload_file(csv_path, key)

        finally:
            await work_page.close()
            await work_ctx.close()
            await browser.close()

def handler(event, context):
    try:
        asyncio.run(main())
    except RuntimeError:
        loop = asyncio.get_event_loop()
        loop.run_until_complete(main())
    return {"statusCode": 200, "body": "ok"}

if __name__ == "__main__":
    asyncio.run(main())
