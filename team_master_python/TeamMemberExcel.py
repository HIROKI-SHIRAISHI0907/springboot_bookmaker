# -*- coding: utf-8 -*-
"""
TeamMemberExcel.py
- /Users/shiraishitoshio/bookmaker/teams_by_league/*.xlsx をすべて読み、
  行ごとの (国, リーグ, チーム, チームリンク) を使って順次スクレイピング
- 各チームのスカッド → 各選手の詳細を取得
- 出力は /Users/shiraishitoshio/bookmaker/team_member_{seq}.xlsx（50行で自動ローテーション）
- 既取得は existing_players.txt と既存の team_member_*.xlsx を見て重複スキップ
"""

import os
import glob
import asyncio
import datetime
from typing import List, Tuple, Dict, Set

from openpyxl import Workbook, load_workbook
from playwright.async_api import async_playwright

# =========================
# 設定
# =========================
BASE_DIR = "/Users/shiraishitoshio/bookmaker"
TEAMS_BY_LEAGUE_DIR = os.path.join(BASE_DIR, "teams_by_league")

EXISTING_KEYS_FILE = os.path.join(BASE_DIR, "existing_players.txt")
EXISTING_KEYS_COPY_FILE = os.path.join(BASE_DIR, "copyteam", "existing_players.txt")

EXCEL_BASE_PREFIX = "team_member_"
EXCEL_MAX_RECORDS = 50

# タイムアウト(ms)
OP_TIMEOUT = 5000             # 操作系
NAV_TIMEOUT = 12000           # ページ遷移
SEL_TIMEOUT = 20000           # セレクタ待ち

# =========================
# ユーティリティ
# =========================
def ensure_dirs():
    os.makedirs(BASE_DIR, exist_ok=True)
    os.makedirs(TEAMS_BY_LEAGUE_DIR, exist_ok=True)
    os.makedirs(os.path.dirname(EXISTING_KEYS_FILE), exist_ok=True)
    os.makedirs(os.path.dirname(EXISTING_KEYS_COPY_FILE), exist_ok=True)

def load_existing_keys_from_file() -> Set[Tuple[str, str, str, str]]:
    """existing_players.txt を読み、(国,リーグ,チーム,選手名) の集合を返す"""
    s: Set[Tuple[str, str, str, str]] = set()
    if os.path.exists(EXISTING_KEYS_FILE):
        with open(EXISTING_KEYS_FILE, "r", encoding="utf-8") as f:
            for line in f:
                parts = line.strip().split("|||")
                if len(parts) == 4:
                    s.add(tuple(parts))  # type: ignore
    return s

def save_new_keys_to_file(new_keys: List[Tuple[str, str, str, str]]) -> None:
    """existing_players.txt と copy 側に追記"""
    for p in [EXISTING_KEYS_FILE, EXISTING_KEYS_COPY_FILE]:
        os.makedirs(os.path.dirname(p), exist_ok=True)
        with open(p, "a", encoding="utf-8") as f:
            for k in new_keys:
                f.write("|||".join(k) + "\n")

def load_existing_player_keys_from_excels() -> Set[Tuple[str, str, str, str]]:
    """既存の team_member_*.xlsx をスキャンして既取得キー化"""
    s: Set[Tuple[str, str, str, str]] = set()
    for path in sorted(glob.glob(os.path.join(BASE_DIR, f"{EXCEL_BASE_PREFIX}*.xlsx"))):
        try:
            wb = load_workbook(path)
            ws = wb.active
            for row in ws.iter_rows(min_row=2, values_only=True):
                if not row or row[0] is None:
                    continue
                key = (str(row[0]), str(row[1]), str(row[2]), str(row[3]))
                s.add(key)
        except Exception as e:
            print(f"[WARN] 既存Excel読込失敗: {path} / {e}")
    return s

def read_teams_from_file(path: str) -> List[Tuple[str, str, str, str]]:
    """
    teams_by_league のExcel1件から、(国, リーグ, チーム, チームリンク) を取得
    1行目は ["国","リーグ","チーム","チームリンク"] を想定
    """
    out: List[Tuple[str, str, str, str]] = []
    try:
        wb = load_workbook(path, data_only=True)
        ws = wb.active
        # ヘッダ柔軟検出
        header = next(ws.iter_rows(min_row=1, max_row=1))
        name_to_idx: Dict[str, int] = {}
        for idx, cell in enumerate(header):
            val = (str(cell.value) if cell.value is not None else "").strip()
            if val:
                name_to_idx[val] = idx
        idx_country = name_to_idx.get("国", 0)
        idx_league = name_to_idx.get("リーグ", 1)
        idx_team = name_to_idx.get("チーム", 2)
        idx_href = name_to_idx.get("チームリンク", 3)

        for r in ws.iter_rows(min_row=2, values_only=True):
            if not r:
                continue
            c = ((r[idx_country] if len(r) > idx_country else "") or "").strip()
            l = ((r[idx_league] if len(r) > idx_league else "") or "").strip()
            t = ((r[idx_team] if len(r) > idx_team else "") or "").strip()
            h = ((r[idx_href] if len(r) > idx_href else "") or "").strip()
            if c and l and t and h:
                out.append((c, l, t, h))
    except Exception as e:
        print(f"[WARN] チームExcel読込失敗: {path} / {e}")
    return out

def makeExcelAndNotice(loop_bef_seq: int, country: str, league: str, team: str, name: str, position: str,
                       jersey: str, goals: str, age_text: str, birth_date: str, market_value: str,
                       loan_info: str, contract_date: str, img_url: str, injury_text: str) -> int:
    """team_member_{seq}.xlsx へ追記。50行で次ファイルへ。先頭12列で重複判定。"""
    if loop_bef_seq < 0:
        for seq in range(1, 3000):
            path = os.path.join(BASE_DIR, f"{EXCEL_BASE_PREFIX}{seq}.xlsx")
            if os.path.exists(path):
                loop_bef_seq = seq
            else:
                wb = Workbook()
                ws = wb.active
                ws.append([
                    "国","リーグ","所属チーム","選手名","ポジション","背番号",
                    "得点数","年齢","誕生日","市場価値","ローン保有元",
                    "契約期限","顔写真","故障情報","データ取得時間"
                ])
                wb.save(path)
                loop_bef_seq = seq
                break

    while True:
        path = os.path.join(BASE_DIR, f"{EXCEL_BASE_PREFIX}{loop_bef_seq}.xlsx")
        if os.path.exists(path):
            wb = load_workbook(path)
            ws = wb.active
        else:
            wb = Workbook()
            ws = wb.active
            ws.append([
                "国","リーグ","所属チーム","選手名","ポジション","背番号",
                "得点数","年齢","誕生日","市場価値","ローン保有元",
                "契約期限","顔写真","故障情報","データ取得時間"
            ])
        if ws.max_row - 1 >= EXCEL_MAX_RECORDS:
            loop_bef_seq += 1
            continue
        break

    row = [
        country, league, team, name, position, jersey, goals,
        age_text, birth_date, market_value, loan_info,
        contract_date, img_url, injury_text, datetime.datetime.now()
    ]

    # 重複判定（先頭12列）
    is_dup = False
    for r in ws.iter_rows(min_row=2, values_only=True):
        if not r:
            continue
        ex = [str(c) if c is not None else "" for c in r]
        if ex[:12] == [str(x) for x in row[:12]]:
            is_dup = True
            break

    if not is_dup:
        ws.append(row)
        print(f"[WRITE] Excel 追記: {team} / {name}")
    else:
        print(f"[SKIP] Excel 重複: {team} / {name}")

    wb.save(path)
    return loop_bef_seq

# =========================
# Playwright ヘルパ
# =========================
async def make_context(browser):
    """
    軽量化コンテキスト（画像/スタイル/フォント/メディアを遮断）。
    DOMが取れればOKなページ想定。
    """
    ctx = await browser.new_context(
        locale="ja-JP",
        timezone_id="Asia/Tokyo",
        user_agent=("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
                    "AppleWebKit/537.36 (KHTML, like Gecko) "
                    "Chrome/124.0.0.0 Safari/537.36"),
        viewport={"width": 1366, "height": 900},
        ignore_https_errors=True,
    )
    ctx.set_default_timeout(OP_TIMEOUT)
    ctx.set_default_navigation_timeout(NAV_TIMEOUT)

    async def _route(route):
        rtype = route.request.resource_type
        # CSS も遮断して高速化（必要なら 'stylesheet' を外してください）
        if rtype in {"image", "stylesheet", "font", "media"}:
            await route.abort()
        else:
            await route.continue_()
    await ctx.route("**/*", _route)
    return ctx

async def goto_with_retry(page, url: str, selector_to_wait: str | None,
                          nav_timeout=NAV_TIMEOUT, sel_timeout=SEL_TIMEOUT, tries=3) -> bool:
    last = None
    for i in range(tries):
        try:
            await page.goto(url, wait_until="domcontentloaded", timeout=nav_timeout)
            if selector_to_wait:
                await page.wait_for_selector(selector_to_wait, timeout=sel_timeout)
            return True
        except Exception as e:
            last = e
            await asyncio.sleep(1.0 * (i + 1))
    print(f"Page.goto 最終失敗: {url} / {last}")
    return False

# =========================
# スクレイピング本体
# =========================
async def scrape_player_detail(ctx, full_url: str) -> Dict[str, str]:
    """選手詳細ページから各種情報を取得"""
    page = await ctx.new_page()
    age = birth = mv = contract = loan = img = inj = "N/A"
    try:
        ok = await goto_with_retry(page, full_url, "div.playerInfoItem")
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
        print(f"[PLAYER DETAIL] 取得失敗: {full_url} / {e}")
    finally:
        await page.close()

    return {
        "age_text": age, "birth_date": birth, "market_value": mv,
        "loan_info": loan, "contract_date": contract,
        "img_url": img, "injury_text": inj
    }

async def process_team_sequential(ctx,
                                  existing_keys: Set[Tuple[str, str, str, str]],
                                  state: Dict[str, int],
                                  country: str, league: str, team: str, href: str):
    """チーム1件を順次処理（スカッド→各選手詳細→Excel追記）"""
    page = await ctx.new_page()
    try:
        # フルURL化
        team_url = href if href.startswith("http") else f"https://flashscore.co.jp{href}"
        squad_url = team_url.rstrip("/") + "/squad/"

        ok = await goto_with_retry(page, squad_url, "div.lineupTable.lineupTable--soccer")
        if not ok:
            print(f"[{country}:{league}:{team}] スカッド取得失敗: goto failed")
            return

        tables = await page.query_selector_all("div.lineupTable.lineupTable--soccer")
        if not tables:
            print(f"[{country}:{league}:{team}] スカッドテーブルなし")
            return

        # ループは完全逐次（チーム→テーブル→行→選手詳細）
        for t in tables:
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
                goal_el = await r.query_selector(".lineupTable__cell--goal")
                goals = (await goal_el.text_content()).strip() if goal_el else "N/A"

                if not href2:
                    continue

                key = (country, league, team, name)
                if key in existing_keys:
                    # 既取得スキップ
                    # print(f"[SKIP] 既存: {key}")
                    continue

                # 予約して重複回避
                existing_keys.add(key)
                save_new_keys_to_file([key])

                purl = href2 if href2.startswith("http") else f"https://flashscore.co.jp{href2}"
                detail = await scrape_player_detail(ctx, purl)

                state["seq"] = makeExcelAndNotice(
                    state["seq"],
                    country, league, team,
                    name, position, jersey, goals,
                    detail["age_text"], detail["birth_date"], detail["market_value"],
                    detail["loan_info"], detail["contract_date"], detail["img_url"], detail["injury_text"]
                )

    except Exception as e:
        print(f"[{country}:{league}:{team}] 処理中エラー: {e}")
    finally:
        await page.close()

# =========================
# エントリポイント
# =========================
async def main():
    ensure_dirs()

    now = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    print(f"{now}  TeamMemberExcel: 開始")

    # 既取得キー
    existing_keys = load_existing_keys_from_file()
    existing_keys |= load_existing_player_keys_from_excels()
    print(f"[INIT] 既取得キー: {len(existing_keys)} 件")

    # teams_by_league/*.xlsx をすべて読む（重複行は後でkey化して吸収）
    team_rows: List[Tuple[str, str, str, str]] = []
    paths = sorted(glob.glob(os.path.join(TEAMS_BY_LEAGUE_DIR, "*.xlsx")))
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

    async with async_playwright() as p:
        browser = await p.chromium.launch(headless=True, args=["--disable-gpu"])
        ctx = await make_context(browser)

        state = {"seq": -1}  # 出力Excelの分割番号

        # 完全逐次で1件ずつ処理
        for i, (country, league, team, href) in enumerate(uniq_rows, 1):
            print(f"[{i}/{len(uniq_rows)}] チーム処理: {country} / {league} / {team}")
            await process_team_sequential(ctx, existing_keys, state, country, league, team, href)

        await ctx.close()
        await browser.close()

    print("TeamMemberExcel: 完了")

if __name__ == "__main__":
    asyncio.run(main())
