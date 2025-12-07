# -*- coding: utf-8 -*-
from playwright.sync_api import sync_playwright
import time
import re
import datetime
import json
from typing import List, Dict, Optional, Tuple
from pathlib import Path

# bmData.py と同様の outputs ディレクトリを想定
SAVE_DIR_NOGAME = "/Users/shiraishitoshio/bookmaker/no_game"

# ========== 対象リーグ・除外条件（futureData.py と同じ） ==========

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

VERBOSE = True

def log(msg: str):
    if VERBOSE:
        print(msg)

def text_clean(s: str) -> str:
    import re
    return re.sub(r"\s+", " ", (s or "")).strip()

def extract_mid(s: str) -> Optional[str]:
    if not s:
        return None
    s = str(s).strip()
    m = re.search(r"[?&#]mid=([A-Za-z0-9]+)", s)
    if m:
        return m.group(1)
    m = re.search(r"/match/([A-Za-z0-9]{6,20})(?:/|$)", s)
    if m:
        return m.group(1)
    return None

# ========== Flashscore 「開催予定」タブ操作 ==========

def goto_football_top(page):
    log("🌐 Flashscore トップへアクセス...")
    page.goto("https://www.flashscore.co.jp/", timeout=45000, wait_until="domcontentloaded")

    # Cookieバナー
    try:
        page.locator("#onetrust-accept-btn-handler").click(timeout=2000)
        log("✅ Cookieバナーを閉じました")
    except:
        pass

    # 念のため「サッカー」をクリック
    try:
        soccer_btn = page.locator("a,button").filter(has_text="サッカー").first
        if soccer_btn and soccer_btn.count():
            soccer_btn.click(timeout=4000)
            time.sleep(0.8)
    except:
        pass

    # 「開催予定」タブ
    try:
        tab = page.locator("div.filters__tab[data-analytics-alias='scheduled']").first
        if tab and tab.count():
            tab.click(timeout=4000)
        else:
            tab = page.locator("div.filters__tab").filter(
                has_text=re.compile(r"(開催予定)")
            ).first
            tab.click(timeout=4000)
        log("✅ 『開催予定』タブに切り替えました")
    except Exception as e:
        log(f"⚠️ 開催予定タブ切り替え失敗: {e}")

    try:
        page.wait_for_timeout(1000)
        page.wait_for_load_state("networkidle", timeout=8000)
    except:
        pass

def expand_all_collapsed_leagues(page):
    print("📂 折りたたみリーグ（非表示）を展開します...")
    btn_selector = (
        "button[data-testid='wcl-accordionButton']"
        "[aria-label='リーグ全試合 表示']"
    )
    max_loops = 200
    for _ in range(max_loops):
        btns = page.locator(btn_selector)
        count = btns.count()
        if count == 0:
            print("   ✅ すべての折りたたみリーグを展開しました")
            return
        print(f"   残り『表示』ボタン数: {count}")
        btn = btns.first
        try:
            btn.scroll_into_view_if_needed()
        except:
            pass
        try:
            btn.click(timeout=2000)
        except Exception as e:
            print(f"   ⚠️ ボタンクリック失敗: {e}")
            break
        page.wait_for_timeout(200)
    print("   ⚠️ ループ上限。まだ非表示が残っている可能性あり。")

def click_next_day(page) -> bool:
    try:
        btn = page.locator("button.wcl-arrow_YpdN4[data-day-picker-arrow='next']").first
        if not btn or not btn.count():
            log("⚠️ 翌日ボタンが見つかりませんでした")
            return False
        btn.click(timeout=3000)
        log("➡️ 『翌日』ボタンをクリックしました")
        time.sleep(1.0)
        try:
            page.wait_for_load_state("networkidle", timeout=8000)
        except:
            pass
        return True
    except Exception as e:
        log(f"⚠️ 翌日ボタンクリック失敗: {e}")
        return False

# ========== 試合行（明日の時間・チーム・リンク）取得 ==========

def _get_match_row_teams_and_time(row):
    ktime = ""
    try:
        ktime = text_clean(row.locator(".event__time").first.text_content() or "")
    except:
        pass
    if not ktime:
        try:
            ktime = text_clean(
                row.locator(
                    "[data-testid='wcl-time'], "
                    "[data-testid='wcl-start-time'], "
                    "[data-testid='wcl-time-status']"
                ).first.text_content() or ""
            )
        except:
            pass

    home = ""
    away = ""

    # 旧UI
    try:
        h = row.locator(".event__participant--home .event__participant--name").first
        a = row.locator(".event__participant--away .event__participant--name").first
        if h.count():
            home = text_clean(h.text_content() or "")
        if a.count():
            away = text_clean(a.text_content() or "")
    except:
        pass

    # その他パターン
    if not home or not away:
        try:
            ps = row.locator(".event__participant .event__participant--name")
            if ps.count() >= 2:
                if not home:
                    home = text_clean(ps.first.text_content() or "")
                if not away:
                    away = text_clean(ps.last.text_content() or "")
        except:
            pass

    if not home or not away:
        try:
            h = row.locator(
                ".event__homeParticipant span.wcl-name_jjfMf, "
                ".event__homeParticipant [data-testid='wcl-scores-simple-text-01']"
            ).first
            a = row.locator(
                ".event__awayParticipant span.wcl-name_jjfMf, "
                ".event__awayParticipant [data-testid='wcl-scores-simple-text-01']"
            ).first
            if h and h.count() and not home:
                home = text_clean(h.text_content() or "")
            if a and a.count() and not away:
                away = text_clean(a.text_content() or "")
        except:
            pass

    if not home or not away:
        try:
            ps = row.locator(
                "[data-testid='wcl-matchRow-participant'] span.wcl-name_jjfMf, "
                "[data-testid='wcl-matchRow-participant'] [data-testid='wcl-scores-simple-text-01']"
            )
            n = ps.count()
            if n >= 2:
                if not home:
                    home = text_clean(ps.nth(0).text_content() or "")
                if not away:
                    away = text_clean(ps.nth(n - 1).text_content() or "")
        except:
            pass

    if not home or not away:
        try:
            imgs = row.locator("[data-testid='wcl-matchRow-participant'] img[data-testid='wcl-participantLogo']")
            n_img = imgs.count()
            if n_img >= 2:
                if not home:
                    home = text_clean(imgs.nth(0).get_attribute("alt") or "")
                if not away:
                    away = text_clean(imgs.nth(n_img - 1).get_attribute("alt") or "")
        except:
            pass

    if not home or not away:
        try:
            snippet = (row.inner_text() or "").strip().replace("\n", " ")[:200]
        except:
            snippet = "<inner_text取得失敗>"
        print(f"⚠️ チーム名取得失敗: time={ktime}, snippet={snippet}")

    return ktime, home, away

def _get_match_row_link(row) -> str:
    try:
        a = row.locator("a.eventRowLink[href*='/match/'][href*='?mid=']").first
        if a and a.count():
            href = a.get_attribute("href") or ""
            if href.startswith("http"):
                return href
            return "https://www.flashscore.co.jp" + href
    except:
        pass
    return ""

def get_current_match_date(page) -> Optional[datetime.date]:
    try:
        btn = page.locator("button[data-testid='wcl-dayPickerButton']").first
        if not btn or not btn.count():
            return None
        txt = text_clean(btn.inner_text() or "")
        m = re.search(r"(\d{2})/(\d{2})", txt)
        if not m:
            return None
        day = int(m.group(1))
        month = int(m.group(2))
        year = datetime.datetime.now().year
        return datetime.date(year, month, day)
    except:
        return None

def collect_scheduled_matches_on_current_day(page) -> List[Dict[str, str]]:
    """
    現在表示中の日付の試合一覧を取得
    戻り値: { 'datetime_str', 'home', 'away', 'url' } のリスト
    """
    try:
        page.wait_for_selector("div.event__match", timeout=12000)
    except:
        log("⚠️ event__match が見つからないまま続行")

    expand_all_collapsed_leagues(page)

    match_date = get_current_match_date(page)

    rows = page.locator("div.event__match.event__match--scheduled")
    if rows.count() == 0:
        rows = page.locator("div.event__match")
    n = rows.count()
    log(f"🎯 開催予定試合 行数: {n}")

    results: List[Dict[str, str]] = []
    seen_mids = set()
    now_str = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")

    for i in range(n):
        row = rows.nth(i)
        try:
            ktime, home, away = _get_match_row_teams_and_time(row)
            link = _get_match_row_link(row)

            if match_date and ktime:
                match_dt_str = f"{match_date.strftime('%Y-%m-%d')} {ktime}"
            else:
                match_dt_str = ktime

            mid = extract_mid(link)
            if mid and mid in seen_mids:
                log(f"   ⏭️ 重複試合(mid={mid})をスキップ")
                continue
            if mid:
                seen_mids.add(mid)

            d = {
                "datetime_str": match_dt_str,
                "home": home,
                "away": away,
                "url": link,
                "fetched_at": now_str,
            }
            results.append(d)
            log(f"   [{i+1}/{n}] | {match_dt_str} | {home} vs {away}")
        except Exception as e:
            log(f"   ⚠️ 行{i}でエラー: {e}")
            continue

    log(f"✅ 当日分 取得件数: {len(results)}")
    return results

# ========== 試合ページから「国: リーグ」取得 & フィルタ ==========

def get_country_and_league_from_match_page(page) -> Tuple[str, str]:
    country = ""
    league = ""
    try:
        try:
            page.wait_for_selector(
                "nav[data-testid='wcl-breadcrumbs'] span[data-testid='wcl-scores-overline-03']",
                timeout=3000
            )
        except:
            pass

        spans = page.locator(
            "nav[data-testid='wcl-breadcrumbs'] span[data-testid='wcl-scores-overline-03']"
        )
        count = spans.count()
        if count == 0:
            return "", ""

        texts = []
        for i in range(count):
            txt = text_clean(spans.nth(i).text_content() or "")
            if txt:
                texts.append(txt)

        start_idx = 0
        if texts and texts[0] == "サッカー":
            start_idx = 1
        if len(texts) > start_idx:
            country = texts[start_idx]
        if len(texts) > start_idx + 1:
            league = texts[start_idx + 1]
    except:
        pass
    return country, league

def enrich_and_filter_by_league(ctx, matches: List[Dict[str, str]]) -> None:
    """
    各試合URLへアクセスして「国: リーグ」を付与し、
    CONTAINS_LIST / U系 / 女子 / 例外リーグ でフィルタする。
    結果は matches をインプレースで書き換え（対象のみ残る）。
    """
    if not matches:
        return

    page = ctx.new_page()
    filtered: List[Dict[str, str]] = []

    for idx, m in enumerate(matches):
        url = m.get("url") or ""
        if not url:
            log("⏭️ URLなし試合をスキップ")
            continue

        log(f"=== リーグ取得 {idx+1}/{len(matches)} ===")
        try:
            page.goto(url, timeout=25000, wait_until="domcontentloaded")
        except Exception as e:
            log(f"   ⚠️ 試合ページ遷移失敗: {e}")
            continue

        country, league = get_country_and_league_from_match_page(page)

        if country and league:
            category = f"{country}: {league}"
        else:
            category = country or league or ""

        if not category:
            log("⏭️ カテゴリ取得失敗 → 除外")
            continue

        # 対象リーグ判定
        if not any(c in category for c in CONTAINS_LIST):
            log(f"⏭️ 対象外リーグ: {category}")
            continue

        # U系 / 女子 / 例外リーグは除外
        if (any(x in category for x in UNDER_LIST) or
            any(x in category for x in GENDER_LIST) or
            any(x in category for x in EXP_LIST)):
            log(f"🚫 除外カテゴリ: {category}")
            continue

        m["category"] = category
        filtered.append(m)
        log(f"✅ 採用: {category} | {m.get('home')} vs {m.get('away')}")

    page.close()
    matches[:] = filtered

# ========== 翌日の「試合がない時間帯」を計算 ==========

def _parse_match_datetime(dt_str: str) -> Optional[datetime.datetime]:
    if not dt_str:
        return None
    dt_str = str(dt_str).strip()
    for fmt in ("%Y-%m-%d %H:%M", "%Y/%m/%d %H:%M"):
        try:
            return datetime.datetime.strptime(dt_str, fmt)
        except ValueError:
            continue
    try:
        t = datetime.datetime.strptime(dt_str, "%H:%M").time()
        today = datetime.date.today()
        return datetime.datetime.combine(today, t)
    except ValueError:
        return None

def _calc_free_slots_for_date(
    start_of_day: datetime.datetime,
    end_of_day: datetime.datetime,
    match_datetimes: List[datetime.datetime],
    min_gap_minutes: int = 0
) -> List[Tuple[datetime.datetime, datetime.datetime]]:
    times = sorted(set(match_datetimes))
    free_slots: List[Tuple[datetime.datetime, datetime.datetime]] = []
    current = start_of_day

    for dt in times:
        if dt > current:
            gap_minutes = (dt - current).total_seconds() / 60
            if gap_minutes >= min_gap_minutes:
                free_slots.append((current, dt))
        if dt > current:
            current = dt

    if current < end_of_day:
        gap_minutes = (end_of_day - current).total_seconds() / 60
        if gap_minutes >= min_gap_minutes:
            free_slots.append((current, end_of_day))

    return free_slots

def get_free_slots_for_matches(matches: List[Dict[str, str]], min_gap_minutes: int = 0):
    """
    1日分の試合（datetime_str が同じ日付）から、その日の空き時間帯を返す。
    戻り値: [(free_start_dt, free_end_dt), ...]
    """
    dts: List[datetime.datetime] = []
    for m in matches:
        dt = _parse_match_datetime(m.get("datetime_str", ""))
        if dt:
            dts.append(dt)

    if not dts:
        print("⚠️ 有効な試合開始時刻がありません。")
        return []

    # このスクリプトでは「翌日1日分」しか取っていない想定
    target_date = dts[0].date()

    start_of_day = datetime.datetime.combine(target_date, datetime.time(0, 0))
    end_of_day   = start_of_day + datetime.timedelta(days=1)

    print(f"\n🎯 対象日: {target_date}\n")
    print("📍 対象日の試合開始時刻（対象リーグのみ）:")
    for dt in sorted(dts):
        print(" ・", dt.strftime("%Y-%m-%d %H:%M"))
    print()

    free_slots = _calc_free_slots_for_date(start_of_day, end_of_day, dts, min_gap_minutes)
    return free_slots

def convert_free_slots_to_ecs_slots(
    free_slots: List[Tuple[datetime.datetime, datetime.datetime]],
    post_match_buffer_minutes: int = 180,   # 前の試合後 3時間
    pre_match_buffer_minutes: int = 30      # 次の試合の30分前に再稼働
) -> List[Tuple[datetime.datetime, datetime.datetime]]:
    """
    free_slots（試合が1つもない時間帯）から、
    ECSを停止していてよい時間帯を計算して返す。

    各 free_slot (free_start, free_end) について:
      ecs_start = free_start + post_match_buffer_minutes
      ecs_end   = free_end   - pre_match_buffer_minutes

    ecs_start < ecs_end のときだけ有効な停止スロットとして採用。
    """
    ecs_slots: List[Tuple[datetime.datetime, datetime.datetime]] = []

    delta_post = datetime.timedelta(minutes=post_match_buffer_minutes)
    delta_pre  = datetime.timedelta(minutes=pre_match_buffer_minutes)

    for free_start, free_end in free_slots:
        ecs_start = free_start + delta_post
        ecs_end   = free_end   - delta_pre

        if ecs_start < ecs_end:
            ecs_slots.append((ecs_start, ecs_end))

    return ecs_slots

# ========== メイン：翌日の対象試合 & 空き時間取得 ==========

def fetch_nextday_matches_and_free_slots(min_gap_minutes: int = 0):
    """
    Flashscore 開催予定タブから
      - 翌日の対象試合一覧
      - その日の「試合がない時間帯」一覧
    を取得して返す。
    """
    matches: List[Dict[str, str]] = []

    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True, slow_mo=70)
        ctx = browser.new_context(
            user_agent=("Mozilla/5.0 (Macintosh; Intel Mac OS X 13_6) "
                        "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"),
            locale="ja-JP",
            timezone_id="Asia/Tokyo",
        )
        page = ctx.new_page()

        goto_football_top(page)

        # 翌日へ
        ok = click_next_day(page)
        if not ok:
            log("❌ 翌日に進めなかったため終了します。")
            browser.close()
            return [], []

        log("==================== 翌日の試合を取得 ====================")
        day_results = collect_scheduled_matches_on_current_day(page)
        matches.extend(day_results)

        page.close()

        # 試合ページにアクセスして「国: リーグ」を付与しながらフィルタ
        enrich_and_filter_by_league(ctx, matches)

        browser.close()

    log(f"🎉 翌日・対象リーグの取得件数: {len(matches)}")

    free_slots = get_free_slots_for_matches(matches, min_gap_minutes=min_gap_minutes)
    return matches, free_slots

# ========== スクリプトとして実行された場合 ==========

if __name__ == "__main__":
    # freeスロットをそのまま出す場合の最小ギャップ（分）
    MIN_GAP_MINUTES = 0

    matches, free_slots = fetch_nextday_matches_and_free_slots(
        min_gap_minutes=MIN_GAP_MINUTES
    )

    print("==== 🕒 翌日の『試合がない時間帯（raw free slots）』 ====")
    if not free_slots:
        print("（スキマ時間なし / 試合なし / 取得失敗）")
    else:
        for start, end in free_slots:
            print(f"  {start.strftime('%Y-%m-%d %H:%M')} 〜 {end.strftime('%H:%M')}")

    # 🔹 free_slots から ECS停止時間帯を計算
    ecs_slots = convert_free_slots_to_ecs_slots(
        free_slots,
        post_match_buffer_minutes=180,   # 試合後 3時間は動かす
        pre_match_buffer_minutes=30      # 次の試合の30分前には再稼働
    )

    print("\n==== 📴 ECS 停止していてよい時間帯（derived from free slots） ====")
    if not ecs_slots:
        print("（停止可能な時間帯はありません）")
    else:
        for start, end in ecs_slots:
            print(f"  {start.strftime('%Y-%m-%d %H:%M')} 〜 {end.strftime('%H:%M')}")

    # 🔹 JSON_FREE_SLOTS は「ECS停止時間」を出力するように変更
    json_slots = [
        {
            "start": start.isoformat(),
            "end": end.isoformat(),
        }
        for start, end in ecs_slots
    ]
    print("\n==== JSON_FREE_SLOTS (ECS stop intervals) ====")
    print(json.dumps(json_slots, ensure_ascii=False))

    output_dir_path = Path(SAVE_DIR_NOGAME)
    output_dir_path.mkdir(parents=True, exist_ok=True)

    # 🔹 ファイル名
    output_file = "ecs_slots.json"

    # 🔹 ディレクトリとファイル名を連結
    output_path = output_dir_path / output_file

    # 🔹 JSONファイルとして保存
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(json_slots, f, ensure_ascii=False, indent=2)

    print(f"JSON出力完了: {output_path}")

