import os
import re
import asyncio
from typing import Tuple
import csv
import json
from pathlib import Path

from openpyxl import Workbook, load_workbook
from playwright.async_api import async_playwright, TimeoutError as PwTimeoutError

SEASON_XLSX = "season_data.xlsx"
BASE_URL = "https://www.flashscore.co.jp"
ICON_HEADER = "リーグアイコン"  # アイコンURLを書き込む列名
B001_JSON_PATH = "/Users/shiraishitoshio/bookmaker/json/b001/b001_country_league.json"


# --- Excel ユーティリティ ---
def open_or_init_season_book(file_path: str = SEASON_XLSX):
    """
    season_data.xlsx を開く。無ければ作成し、ヘッダー:
    国, リーグ, シーズン開始, シーズン終了, パス, リーグアイコン
    """
    if not os.path.exists(file_path):
        wb = Workbook()
        ws = wb.active
        ws.title = "season"
        ws.append(["国", "リーグ", "シーズン開始", "シーズン終了", "パス", ICON_HEADER])
        wb.save(file_path)
        return wb, ws

    wb = load_workbook(file_path)
    ws = wb.active

    header = [c.value for c in ws[1]]
    expected = ["国", "リーグ", "シーズン開始", "シーズン終了", "パス"]
    if not header or header[:5] != expected:
        # 不正な場合は作り直し（既存内容は破棄）
        ws.delete_rows(1, ws.max_row if ws.max_row else 1)
        ws.append(["国", "リーグ", "シーズン開始", "シーズン終了", "パス", ICON_HEADER])
        wb.save(file_path)
        return wb, ws

    # アイコン列がなければ追加
    if ICON_HEADER not in header:
        ws.cell(row=1, column=len(header) + 1, value=ICON_HEADER)
        wb.save(file_path)

    return wb, ws


def get_colmap(ws):
    header = [c.value for c in ws[1]]
    return {name: idx + 1 for idx, name in enumerate(header)}


def yield_targets(ws, allowed_countries: set[str] | None = None):
    """
    - シーズン開始/終了のどちらかが空の行だけ対象
    - allowed_countries が指定されていれば、国がその集合に含まれる行だけ対象
    """
    col = get_colmap(ws)
    max_row = ws.max_row if ws.max_row else 1

    for r in range(2, max_row + 1):
        path = (ws.cell(row=r, column=col["パス"]).value or "").strip()
        if not path:
            continue

        # ★ JSONがある場合の country フィルタ
        if allowed_countries is not None:
            country = (ws.cell(row=r, column=col["国"]).value or "").strip()
            if not country or country not in allowed_countries:
                continue

        start = ws.cell(row=r, column=col["シーズン開始"]).value
        end   = ws.cell(row=r, column=col["シーズン終了"]).value

        if (start not in (None, "")) and (end not in (None, "")):
            continue

        yield r, path


# --- Playwright でページから取得 ---
async def fetch_icon_and_progress_ddmm(page, full_url: str):
    """
    リーグページから
      - アイコンURL（リーグヘッダーロゴ）
      - event__time の最小/最大（dd.mm）
    を取得。
    """
    icon_url, start_ddmm, end_ddmm = "", "", ""

    await page.goto(full_url, wait_until="domcontentloaded")

    # リーグアイコン（なければ空のまま）
    try:
        icon = await page.query_selector("img.heading__logo, img[class*='heading__logo--']")
        if not icon:
            icon = await page.query_selector("[data-testid='wcl-headerLeague'] img.heading__logo")
        if icon:
            src = await icon.get_attribute("src")
            if src:
                icon_url = src
    except PwTimeoutError:
        pass

    # 「もっと試合を表示する」を押し切る
    try:
        await page.wait_for_selector(".event__match", timeout=8000)
    except PwTimeoutError:
        return icon_url, start_ddmm, end_ddmm

    try:
        clicks = await click_show_more_until_end(page)
        print(f"押した回数: {clicks}")
    except PwTimeoutError:
        pass

    # event__time をすべて取得し、dd.mm を抽出
    texts = await page.locator(".event__match .event__time").all_text_contents()
    pat = re.compile(r"(\d{1,2})\.(\d{1,2})\.")   # 例: 29.08. 21:30 -> (29, 08)

    ddmm_list = []
    for t in texts:
        m = pat.search(t)
        if not m:
            continue
        dd = int(m.group(1))
        mm = int(m.group(2))
        ddmm = f"{dd:02d}.{mm:02d}"
        key = mm * 100 + dd   # 年なし比較用の簡易キー
        ddmm_list.append((key, ddmm))

    if ddmm_list:
        # 最小/最大をシーズン開始/終了の候補として採用
        start_ddmm = min(ddmm_list, key=lambda x: x[0])[1]
        end_ddmm   = max(ddmm_list, key=lambda x: x[0])[1]

    return icon_url, start_ddmm, end_ddmm

async def click_show_more_until_end(
    page,
    button_text: str = "もっと試合を表示する",
    max_clicks: int = 50,
    wait_timeout: int = 15000
) -> int:
    """
    「もっと試合を表示する」を、表示されなくなる/増えなくなるまで押す。
    戻り値: 実際にクリックした回数
    """
    clicks = 0
    while clicks < max_clicks:
        link = page.locator("a[data-testid='wcl-buttonLink']").filter(has_text=button_text).first
        if await link.count() == 0 or not await link.is_visible():
            break

        before = await page.locator(".event__match").count()
        await link.scroll_into_view_if_needed()
        await link.click()
        clicks += 1

        # ★ ここが修正点: 第2引数はキーワード arg= で渡す
        try:
            await page.wait_for_function(
                """
                ({sel, before, linkSel}) => {
                  const now = document.querySelectorAll(sel).length;
                  const link = document.querySelector(linkSel);
                  return (now > before) || !link || link.offsetParent === null;
                }
                """,
                arg={"sel": ".event__match", "before": before, "linkSel": "a[data-testid='wcl-buttonLink']"},
                timeout=wait_timeout
            )
        except PwTimeoutError:
            break

    return clicks

def extract_countries(json_path: str = "bm001_country_league.json") -> list[str]:
    """
    bm001_country_league.json が存在したら読み込み、
    country だけをユニークに抽出してリストで返す。

    想定して拾うパターン（広め）:
      - [{"country": "...", "league": "..."}, ...]
      - {"items": [{"country": "...", ...}, ...]}
      - {"country_league": [{"country": "...", ...}, ...]}
      - {"Japan": ["J1", "J2"], "England": [...]} のような country -> leagues 配列（キーが国）
      - ネストした dict/list 内に "country" キーが出てくる形
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
            # よくあるコンテナキー配下
            for k in ("items", "country_league", "data", "results"):
                if k in obj:
                    walk(obj[k])

            # country キーがあれば拾う
            if "country" in obj:
                c = norm(obj["country"])
                if c:
                    countries.add(c)

            # country -> leagues のマップっぽい場合、キー側が国名になりやすい
            # 例: {"Japan": ["J1", "J2"], "England": ["Premier League"]}
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

def xlsx_to_csv(xlsx_path: str, sheet_name: str = "season", csv_path: str | None = None) -> str:
    """
    xlsx の指定シートを丸ごと CSV に変換する（ヘッダー行含む）。
    返り値: 出力した CSV パス
    """
    xlsx_path = str(xlsx_path)
    if csv_path is None:
        base = os.path.splitext(xlsx_path)[0]
        csv_path = base + ".csv"

    wb = load_workbook(xlsx_path, data_only=True)
    ws = wb[sheet_name] if sheet_name in wb.sheetnames else wb.active

    # 1行目（ヘッダー）を基準に列数を決める
    header = [c.value for c in ws[1]]
    max_col = len(header)

    # Excelの空行は ws.max_row に含まれることがあるので、末尾の完全空行は落とす
    last_row = ws.max_row
    while last_row >= 2:
        row_vals = [ws.cell(row=last_row, column=c).value for c in range(1, max_col + 1)]
        if any(v not in (None, "") for v in row_vals):
            break
        last_row -= 1

    # 日本語Excelで開くことを想定して utf-8-sig 推奨
    with open(csv_path, "w", newline="", encoding="utf-8-sig") as f:
        writer = csv.writer(f)
        writer.writerow(header)

        for r in range(2, last_row + 1):
            writer.writerow([ws.cell(row=r, column=c).value for c in range(1, max_col + 1)])

    return csv_path

def print_pairs(pairs: list[tuple[str, str]], limit: int = 50):
    if not pairs:
        print("[bm001_country_league.json] 見つからない or 組が抽出できませんでした")
        return
    print(f"[bm001_country_league.json] 抽出 {len(pairs)} 件（先頭{min(limit, len(pairs))}件）")
    for c, l in pairs[:limit]:
        print(f"- {c} / {l}")

# --- メイン ---
async def main():
    wb, ws = open_or_init_season_book(SEASON_XLSX)
    col = get_colmap(ws)

    # ✅ スクレイピング前に JSON 有無で対象 country を決める
    allowed_countries: set[str] | None = None
    countries = extract_countries(B001_JSON_PATH)

    if countries:  # JSONあり & country抽出できた
        allowed_countries = set(countries)
        print(f"[FILTER] JSONあり: country {len(allowed_countries)}件で絞り込み")
    else:          # JSONなし or 抽出できない
        allowed_countries = None
        print("[FILTER] JSONなし/空: 絞り込みなし（全件対象）")

    updated_rows = 0

    async with async_playwright() as p:
        browser = await p.chromium.launch(headless=True)
        context = await browser.new_context()
        page = await context.new_page()

        # ✅ スクレイピング（JSONがある場合は国一致行だけ回る）
        for row, path in yield_targets(ws, allowed_countries=allowed_countries):
            full_url = f"{BASE_URL}{path}fixtures/"
            print(f"[更新対象] row={row} -> {full_url}")

            icon_url, start_ddmm, end_ddmm = await fetch_icon_and_progress_ddmm(page, full_url)

            # ✅ エクセルに記入（空セルだけ）
            if (not ws.cell(row=row, column=col["シーズン開始"]).value) and start_ddmm:
                ws.cell(row=row, column=col["シーズン開始"], value=start_ddmm)
            if (not ws.cell(row=row, column=col["シーズン終了"]).value) and end_ddmm:
                ws.cell(row=row, column=col["シーズン終了"], value=end_ddmm)
            if (not ws.cell(row=row, column=col[ICON_HEADER]).value) and icon_url:
                ws.cell(row=row, column=col[ICON_HEADER], value=icon_url)

            updated_rows += 1
            await page.wait_for_timeout(200)

        await context.close()
        await browser.close()

    # ✅ Excel保存（スクレイピング後）
    wb.save(SEASON_XLSX)

    # ✅ 最後に CSV 変換
    csv_path = xlsx_to_csv(SEASON_XLSX, sheet_name="season")
    print(f"CSV出力: {csv_path}")

    print(f"更新行数: {updated_rows}")

if __name__ == "__main__":
    asyncio.run(main())
