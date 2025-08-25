import os
import re
import asyncio
from typing import Tuple

from openpyxl import Workbook, load_workbook
from playwright.async_api import async_playwright, TimeoutError as PwTimeoutError

SEASON_XLSX = "season_data.xlsx"
BASE_URL = "https://www.flashscore.co.jp"
ICON_HEADER = "リーグアイコン"  # アイコンURLを書き込む列名


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


def yield_targets(ws):
    """
    「シーズン開始」と「シーズン終了」のどちらかが空の行だけを対象にする。
    両方とも埋まっている行はスキップする。
    """
    col = get_colmap(ws)
    max_row = ws.max_row if ws.max_row else 1
    for r in range(2, max_row + 1):
        path  = (ws.cell(row=r, column=col["パス"]).value or "").strip()
        if not path:
            continue

        start = ws.cell(row=r, column=col["シーズン開始"]).value
        end   = ws.cell(row=r, column=col["シーズン終了"]).value

        # 両方埋まっていたらスキップ
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

# --- メイン ---
async def main():
    wb, ws = open_or_init_season_book(SEASON_XLSX)
    col = get_colmap(ws)

    async with async_playwright() as p:
        browser = await p.chromium.launch(headless=True)
        context = await browser.new_context()
        page = await context.new_page()

        updated_rows = 0
        for row, path in yield_targets(ws):
            # フルURLに整形
            full_url = f"{BASE_URL}{path}fixtures/"

            print(f"[更新対象] row={row} -> {full_url}")

            icon_url, start_ddmm, end_ddmm = await fetch_icon_and_progress_ddmm(page, full_url)

            # 空セルだけ埋める（上書きしない）
            if (not ws.cell(row=row, column=col["シーズン開始"]).value) and start_ddmm:
                ws.cell(row=row, column=col["シーズン開始"], value=start_ddmm)
            if (not ws.cell(row=row, column=col["シーズン終了"]).value) and end_ddmm:
                ws.cell(row=row, column=col["シーズン終了"], value=end_ddmm)
            if (not ws.cell(row=row, column=col[ICON_HEADER]).value) and icon_url:
                ws.cell(row=row, column=col[ICON_HEADER], value=icon_url)

            updated_rows += 1
            await page.wait_for_timeout(200)  # 負荷軽減

        wb.save(SEASON_XLSX)
        await context.close()
        await browser.close()

    print(f"更新行数: {updated_rows}")


if __name__ == "__main__":
    asyncio.run(main())
