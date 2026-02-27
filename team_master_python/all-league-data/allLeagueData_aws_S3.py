# -*- coding: utf-8 -*-
"""
allLeagueData_s3_ecs.py（ECS用・非同期）
Flashscore: 左メニューの 国 -> リーグ一覧 を取得して CSV 作成 → S3へ

修正版（重要）:
- 左メニューが「国」タブ/モードを持つ場合に備えて "国" をクリックして固定
- スクロールは scrollTop 直書きではなく wheel で実施（仮想スクロール実装対策）
- スクロール位置/新規増分をログで追えるようにして「本当に動いてるか」を可視化
"""

import os
import sys
import csv
import time
import asyncio
import datetime
from typing import List, Tuple, Set, Optional

from playwright.async_api import async_playwright

import boto3
from botocore.exceptions import ClientError

S3_BUCKET_OUT = "aws-s3-all-league-csv"
BASE_DIR = "/tmp/bookmaker"
OUT_CSV_PATH = os.path.join(BASE_DIR, "all_league_master.csv")

BASE_URL = "https://www.flashscore.co.jp/"

_s3 = boto3.client("s3")

OP_TIMEOUT_MS = int(os.getenv("OP_TIMEOUT_MS", "15000"))
NAV_TIMEOUT_MS = int(os.getenv("NAV_TIMEOUT_MS", "60000"))
SEL_TIMEOUT_MS = int(os.getenv("SEL_TIMEOUT_MS", "60000"))
WAIT_UNTIL = os.getenv("WAIT_UNTIL", "domcontentloaded")
HEARTBEAT_SEC = int(os.getenv("HEARTBEAT_SEC", "30"))
PROGRESS_SEC = int(os.getenv("PROGRESS_SEC", "60"))
NAV_GAP_MS = int(os.getenv("NAV_GAP_MS", "700"))

# 国収集: 上限保険
COUNTRY_SCAN_MAX_STEPS = int(os.getenv("COUNTRY_SCAN_MAX_STEPS", "900"))
# 「増えない」判定
COUNTRY_STAGNATION_LIMIT = int(os.getenv("COUNTRY_STAGNATION_LIMIT", "14"))

# 国ブロック探索
COUNTRY_FIND_MAX_STEPS = int(os.getenv("COUNTRY_FIND_MAX_STEPS", "350"))
FIND_STAGNATION_LIMIT = int(os.getenv("FIND_STAGNATION_LIMIT", "10"))

# wheel スクロール量
WHEEL_DELTA_Y = int(os.getenv("WHEEL_DELTA_Y", "900"))
# クリック間隔など
SHORT_WAIT_MS = int(os.getenv("SHORT_WAIT_MS", "180"))


def log(msg: str):
    ts = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    print(f"{ts} {msg}", flush=True)
    try:
        sys.stdout.flush()
    except Exception:
        pass


def ensure_dirs():
    os.makedirs(BASE_DIR, exist_ok=True)


def s3_upload_to_bucket_root(bucket: str, local_path: str) -> bool:
    key = os.path.basename(local_path)
    try:
        _s3.upload_file(local_path, bucket, key)
        log(f"[S3 UPLOAD] {local_path} -> s3://{bucket}/{key}")
        return True
    except ClientError as e:
        code = e.response.get("Error", {}).get("Code", "Unknown")
        log(f"[S3 ERROR] upload failed: {local_path} -> s3://{bucket}/{key} (Code={code})")
        return False


class Progress:
    def __init__(self):
        self.started = time.time()
        self.last = 0.0
        self.c = {
            "countries_total": 0,
            "countries_done": 0,
            "countries_found": 0,
            "rows": 0,
            "goto_fail": 0,
            "find_fail": 0,
            "open_fail": 0,
        }

    def set(self, k: str, v: int):
        self.c[k] = v

    def inc(self, k: str, n: int = 1):
        self.c[k] = self.c.get(k, 0) + n

    def maybe_log(self, force: bool = False):
        now = time.time()
        if (not force) and (now - self.last < PROGRESS_SEC):
            return
        self.last = now
        elapsed = int(now - self.started)
        log(
            f"[PROGRESS] elapsed={elapsed}s "
            f"countries={self.c['countries_done']}/{self.c['countries_total']} "
            f"found={self.c['countries_found']} rows={self.c['rows']} "
            f"goto_fail={self.c['goto_fail']} find_fail={self.c['find_fail']} open_fail={self.c['open_fail']}"
        )


progress = Progress()

_last_nav_ts = 0.0
_nav_gap_lock = asyncio.Lock()


async def _respect_nav_gap():
    global _last_nav_ts
    async with _nav_gap_lock:
        loop = asyncio.get_running_loop()
        now = loop.time()
        due = _last_nav_ts + (NAV_GAP_MS / 1000.0)
        if now < due:
            await asyncio.sleep(due - now)
        _last_nav_ts = loop.time()


async def heartbeat_task():
    try:
        while True:
            await asyncio.sleep(HEARTBEAT_SEC)
            progress.maybe_log(force=True)
    except asyncio.CancelledError:
        return


BLOCKED_HOST_KEYWORDS = [
    "googletagmanager", "google-analytics", "doubleclick", "googlesyndication",
    "scorecardresearch", "criteo", "adsystem", "taboola", "facebook", "twitter",
    "hotjar", "onetrust", "cookiebot", "trustarc", "amazon-adsystem", "adservice",
]


async def make_context(browser):
    ctx = await browser.new_context(
        locale="ja-JP",
        timezone_id="Asia/Tokyo",
        viewport={"width": 1280, "height": 800},
        user_agent=(
            "Mozilla/5.0 (X11; Linux x86_64) "
            "AppleWebKit/537.36 (KHTML, like Gecko) "
            "Chrome/124.0.0.0 Safari/537.36"
        ),
        ignore_https_errors=True,
        java_script_enabled=True,
    )
    ctx.set_default_timeout(OP_TIMEOUT_MS)
    ctx.set_default_navigation_timeout(NAV_TIMEOUT_MS)

    async def _route(route):
        req = route.request
        rtype = req.resource_type
        url = (req.url or "").lower()
        if rtype in {"image", "font", "media", "beacon"}:
            return await route.abort()
        if any(k in url for k in BLOCKED_HOST_KEYWORDS):
            return await route.abort()
        return await route.continue_()

    await ctx.route("**/*", _route)
    return ctx


async def safe_goto(page, url: str, selector_to_wait: Optional[str] = None, tries: int = 3) -> bool:
    last = None
    for i in range(tries):
        try:
            await _respect_nav_gap()
            resp = await page.goto(url, wait_until=WAIT_UNTIL, timeout=NAV_TIMEOUT_MS)
            if resp is not None and resp.status >= 400:
                raise RuntimeError(f"HTTP {resp.status}")
            if selector_to_wait:
                await page.wait_for_selector(selector_to_wait, timeout=SEL_TIMEOUT_MS, state="attached")
            return True
        except Exception as e:
            last = e
            await asyncio.sleep(0.8 * (i + 1))
    progress.inc("goto_fail", 1)
    log(f"[GOTO FAIL] {url} / {last}")
    return False


def _path_parts(href: str) -> List[str]:
    return [p for p in href.strip("/").split("/") if p]


def _is_country_href(href: str) -> bool:
    parts = _path_parts(href)
    return len(parts) == 2 and parts[0] == "soccer"


def _country_path(country_href_or_url: str) -> str:
    s = country_href_or_url
    if "://www.flashscore.co.jp" in s:
        s = s.split("://www.flashscore.co.jp", 1)[1]
    if not s.startswith("/"):
        s = "/" + s
    if not s.endswith("/"):
        s += "/"
    return s


async def dismiss_overlays(page):
    """
    Cookie/Consentが前面に来て操作が死ぬのを回避するための雑多な閉じ処理。
    失敗しても無視でOK。
    """
    candidates = [
        # よくある文言（Flashscore以外も含む）
        ("button", "同意"),
        ("button", "同意する"),
        ("button", "承諾"),
        ("button", "許可"),
        ("button", "OK"),
        ("button", "閉じる"),
        ("button", "×"),
        ("a", "閉じる"),
    ]
    for role, name in candidates:
        try:
            # get_by_role は exact が効くので誤爆しづらい
            btn = page.get_by_role(role, name=name)
            if await btn.count() > 0:
                await btn.first.click(timeout=1200)
                await page.wait_for_timeout(200)
        except Exception:
            pass


async def expand_left_menu(page, max_clicks: int = 40):
    for _ in range(max_clicks):
        more = page.locator("#category-left-menu .lmc__itemMore")
        if await more.count() == 0:
            return
        try:
            await more.first.click(timeout=2000)
            await page.wait_for_timeout(SHORT_WAIT_MS)
        except Exception:
            return


async def switch_left_menu_to_country_mode(page):
    """
    左メニューに「国」タブ/ボタンがある場合に押して国一覧モードへ寄せる。
    無ければ何もしない。
    """
    # “中国” “国際” など誤爆しないように ^国$ を優先
    try:
        # まず role=tab / button を狙う
        for role in ["tab", "button"]:
            el = page.get_by_role(role, name="国")
            if await el.count() > 0:
                await el.first.click(timeout=2000)
                await page.wait_for_timeout(250)
                return
    except Exception:
        pass

    # フォールバック：テキスト一致（厳しめ）で探す
    try:
        el2 = page.locator("#category-left-menu").get_by_text("国", exact=True)
        if await el2.count() > 0:
            await el2.first.click(timeout=2000)
            await page.wait_for_timeout(250)
    except Exception:
        pass


async def get_left_menu_scroller_info(page) -> dict:
    """
    左メニュー内の「実際にスクロールしている要素」を推定し、その scrollTop 等を返す。
    """
    return await page.evaluate(
        """() => {
            const root = document.querySelector('#category-left-menu');
            if (!root) return {ok:false};

            const nodes = [root, ...Array.from(root.querySelectorAll('*'))];
            const scroller = nodes.find(el => el.scrollHeight > el.clientHeight + 5) || root;

            const rect = scroller.getBoundingClientRect();
            return {
              ok:true,
              scrollTop: scroller.scrollTop,
              scrollHeight: scroller.scrollHeight,
              clientHeight: scroller.clientHeight,
              rect: {x: rect.x, y: rect.y, w: rect.width, h: rect.height},
            };
        }"""
    )


async def wheel_scroll_left_menu(page, delta_y: int) -> bool:
    """
    左メニューにフォーカスを当て、wheelでスクロールする。
    戻り値: スクロール位置が変化したか
    """
    info1 = await get_left_menu_scroller_info(page)
    if not info1.get("ok"):
        return False

    r = info1["rect"]
    # スクロール領域の中央あたりにマウスを置いて wheel
    x = int(r["x"] + max(10, min(r["w"] - 10, r["w"] * 0.6)))
    y = int(r["y"] + max(10, min(r["h"] - 10, r["h"] * 0.4)))
    try:
        await page.mouse.move(x, y)
        await page.mouse.wheel(0, delta_y)
        await page.wait_for_timeout(140)
    except Exception:
        return False

    info2 = await get_left_menu_scroller_info(page)
    if not info2.get("ok"):
        return False
    return info2["scrollTop"] != info1["scrollTop"]


async def wheel_scroll_left_menu_to_top(page, rounds: int = 10):
    for _ in range(rounds):
        await wheel_scroll_left_menu(page, -2400)
        await page.wait_for_timeout(60)


async def collect_visible_countries(page) -> List[Tuple[str, str]]:
    """
    locator.count() に頼らず、DOMから一括で拾う（高速＆安定）
    """
    items = await page.evaluate(
        """() => {
            const root = document.querySelector('#category-left-menu');
            if (!root) return [];
            const as = Array.from(root.querySelectorAll('a[href^="/soccer/"]'));
            const out = [];
            for (const a of as) {
                const href = a.getAttribute('href') || '';
                out.push({
                    href,
                    text: (a.querySelector('span.lmc__elementName')?.textContent || a.textContent || '').trim()
                });
            }
            return out;
        }"""
    )

    out: List[Tuple[str, str]] = []
    for it in items:
        href = (it.get("href") or "").strip()
        if not href.startswith("/soccer/") or not _is_country_href(href):
            continue
        name = (it.get("text") or "").strip()
        if not name:
            continue
        url = f"https://www.flashscore.co.jp{href}"
        out.append((name, url))
    return out


async def collect_countries(page) -> List[Tuple[str, str]]:
    ok = await safe_goto(page, BASE_URL, selector_to_wait="#category-left-menu")
    if not ok:
        return []

    await page.wait_for_timeout(300)
    await dismiss_overlays(page)
    await switch_left_menu_to_country_mode(page)
    await wheel_scroll_left_menu_to_top(page)

    seen: Set[str] = set()
    out: List[Tuple[str, str]] = []

    stagnation = 0
    last_count = 0

    for step_i in range(COUNTRY_SCAN_MAX_STEPS):
        await expand_left_menu(page, max_clicks=15)

        vis = await collect_visible_countries(page)
        new_n = 0
        for name, url in vis:
            if url in seen:
                continue
            seen.add(url)
            out.append((name, url))
            new_n += 1
            progress.inc("countries_found", 1)

        if len(out) == last_count:
            stagnation += 1
        else:
            stagnation = 0
            last_count = len(out)

        if step_i % 30 == 0:
            info = await get_left_menu_scroller_info(page)
            log(f"[COUNTRY_SCAN] step={step_i} total={len(out)} new={new_n} stagnation={stagnation} scroller={info}")

        # ✅ ここが止まると「176で固定」になるので、ログに scroller を出してる
        if stagnation >= COUNTRY_STAGNATION_LIMIT:
            break

        moved = await wheel_scroll_left_menu(page, WHEEL_DELTA_Y)
        if not moved:
            # wheelで動かないなら、左メニューが別実装の可能性が高い
            # 少し待って “もっと見る” 出現や遅延描画も拾う
            await page.wait_for_timeout(250)
            # それでも増えないなら終了側へ寄せる
        await page.wait_for_timeout(120)

        if (not moved) and stagnation >= 4:
            break

        # たまに上に戻して再スキャン（仮想リストの描画飛び対策）
        if step_i > 0 and step_i % 240 == 0:
            await wheel_scroll_left_menu_to_top(page, rounds=8)

    out.sort(key=lambda x: x[0])
    return out


async def ensure_country_block_in_dom(page, cpath: str) -> bool:
    await dismiss_overlays(page)
    await switch_left_menu_to_country_mode(page)
    await wheel_scroll_left_menu_to_top(page, rounds=8)

    stagnation = 0
    for i in range(COUNTRY_FIND_MAX_STEPS):
        block = page.locator(f'#category-left-menu .lmc__block:has(a[href="{cpath}"])')
        if await block.count() > 0:
            return True

        await expand_left_menu(page, max_clicks=10)
        moved = await wheel_scroll_left_menu(page, WHEEL_DELTA_Y)

        if not moved:
            stagnation += 1
        else:
            stagnation = 0

        if i % 40 == 0:
            info = await get_left_menu_scroller_info(page)
            log(f"[FIND_COUNTRY] i={i} cpath={cpath} moved={moved} stagnation={stagnation} scroller={info}")

        if stagnation >= FIND_STAGNATION_LIMIT:
            break

    progress.inc("find_fail", 1)
    return False


async def force_open_country_block(page, cpath: str, country_name: str) -> bool:
    sel = f'#category-left-menu a[href="{cpath}"]'
    try:
        await page.evaluate(
            """(s) => {
                const a = document.querySelector(s);
                if (!a) return false;
                a.click();
                return true;
            }""",
            sel,
        )
        await page.wait_for_timeout(220)
    except Exception as e:
        log(f"❌ open失敗(JS click): {country_name} / {e}")

    opened = page.locator(f'#category-left-menu .lmc__block.lmc__blockOpened:has(a[href="{cpath}"])')
    if await opened.count() > 0:
        return True

    # フォールバック：国ページへgotoしてから再オープン
    try:
        ok = await safe_goto(page, f"https://www.flashscore.co.jp{cpath}", "#category-left-menu")
        if not ok:
            return False

        await page.wait_for_timeout(300)
        await dismiss_overlays(page)
        await switch_left_menu_to_country_mode(page)

        ok2 = await ensure_country_block_in_dom(page, cpath)
        if not ok2:
            return False

        await page.evaluate(
            """(s) => {
                const a = document.querySelector(s);
                if (!a) return false;
                a.click();
                return true;
            }""",
            sel,
        )
        await page.wait_for_timeout(220)

    except Exception as e:
        log(f"❌ open失敗(fallback goto): {country_name} / {e}")
        return False

    opened = page.locator(f'#category-left-menu .lmc__block.lmc__blockOpened:has(a[href="{cpath}"])')
    return (await opened.count()) > 0


async def collect_leagues_for_country(page, country_name: str, country_url: str) -> List[str]:
    ok = await safe_goto(page, BASE_URL, selector_to_wait="#category-left-menu")
    if not ok:
        return []

    await page.wait_for_timeout(250)
    await dismiss_overlays(page)
    await switch_left_menu_to_country_mode(page)

    cpath = _country_path(country_url)

    if not await ensure_country_block_in_dom(page, cpath):
        log(f"❌ 国ブロックがDOMに出ない: {country_name} / {cpath}")
        return []

    if not await force_open_country_block(page, cpath, country_name):
        progress.inc("open_fail", 1)
        log(f"❌ open失敗: {country_name} / {cpath}")
        return []

    league_loc = page.locator(
        f'#category-left-menu .lmc__block.lmc__blockOpened:has(a[href="{cpath}"]) a.lmc__templateHref'
    )
    n = await league_loc.count()
    log(f"{country_name}: 左メニューリーグ候補 {n} 件")

    leagues: List[str] = []
    seen: Set[str] = set()
    for i in range(n):
        txt = (await league_loc.nth(i).inner_text() or "").strip()
        if not txt or txt in seen:
            continue
        seen.add(txt)
        leagues.append(txt)

    return leagues


async def main():
    ensure_dirs()
    log("ECS/S3: 開始")

    hb = asyncio.create_task(heartbeat_task())

    f = open(OUT_CSV_PATH, "w", encoding="utf-8-sig", newline="")
    w = csv.writer(f)
    w.writerow(["国", "リーグ"])

    try:
        async with async_playwright() as p:
            browser = await p.chromium.launch(
                headless=True,
                args=[
                    "--disable-gpu",
                    "--no-sandbox",
                    "--disable-setuid-sandbox",
                    "--no-zygote",
                    "--disable-dev-shm-usage",
                    "--blink-settings=imagesEnabled=false",
                ],
            )
            ctx = await make_context(browser)
            page = await ctx.new_page()

            log("国一覧取得中（国モード強制 + wheelスクロール）...")
            countries = await collect_countries(page)
            progress.set("countries_total", len(countries))
            progress.maybe_log(force=True)

            # ✅ ここで判定できる（入ってなければ収集ロジック or 国モードが効いてない）
            for key in ["日本", "中国", "韓国"]:
                hit = any(name == key for name, _ in countries)
                log(f"[CHECK] {key}: {hit}")

            # 参考：先頭/末尾を出して、何を拾えてるか確認しやすくする
            log(f"[COUNTRIES] total={len(countries)} head={countries[:10]}")
            log(f"[COUNTRIES] tail={countries[-10:]}")

            for idx, (cname, curl) in enumerate(countries, 1):
                log(f"[{idx}/{len(countries)}] {cname} 開始")
                try:
                    leagues = await collect_leagues_for_country(page, cname, curl)
                    for lg in leagues:
                        w.writerow([cname, lg])
                        progress.inc("rows", 1)
                    log(f"[DONE] {cname}: {len(leagues)} リーグ")
                except Exception as e:
                    log(f"❌ エラー: {cname} / {e}")

                progress.inc("countries_done", 1)
                progress.maybe_log()

            await page.close()
            await ctx.close()
            await browser.close()

    finally:
        try:
            f.close()
        except Exception:
            pass

    log(f"CSV作成完了: {OUT_CSV_PATH}")
    s3_upload_to_bucket_root(S3_BUCKET_OUT, OUT_CSV_PATH)

    hb.cancel()
    try:
        await hb
    except asyncio.CancelledError:
        pass

    log("ECS/S3: 完了")


if __name__ == "__main__":
    asyncio.run(main())
