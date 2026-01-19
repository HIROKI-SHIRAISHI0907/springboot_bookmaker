# -*- coding: utf-8 -*-
"""
Flashscore「開催予定」から翌日の対象リーグ試合だけ抽出し、
「ECS停止してよい時間帯」を算出して S3 に JSON 保存するスクリプト。

想定:
- ECS(Fargate) で実行
- Playwright(Chromium) をコンテナ内で動かす
- 出力は S3 (デフォルト: aws-s3-no-ecs-task-time-csv) に put_object

必要なAWS権限(タスクロール):
- s3:PutObject (保存先プレフィックス配下)
"""

from playwright.sync_api import sync_playwright
import time
import re
import datetime
import json
import os
from typing import List, Dict, Optional, Tuple
from pathlib import Path

import boto3
from botocore.exceptions import ClientError


# =========================
# Environment / Settings
# =========================

# Playwright / Flashscore
FLASHCORE_URL = "https://www.flashscore.co.jp/"
TIMEZONE_ID = "Asia/Tokyo"
LOCALE = "ja-JP"
USER_AGENT ="Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

HEADLESS = True
SLOW_MO_MS = 0.0

# Slot calculation
# どれくらいの時間ECS稼働が停止できたら停止扱いにするか(コンスタント停止は起動失敗リスクがあるため)
MIN_GAP_MINUTES = int(os.environ.get("MIN_GAP_MINUTES", "0"))
POST_MATCH_BUFFER_MINUTES = int(os.environ.get("POST_MATCH_BUFFER_MINUTES", "180"))  # 試合後3hは稼働
PRE_MATCH_BUFFER_MINUTES = int(os.environ.get("PRE_MATCH_BUFFER_MINUTES", "30"))     # 次試合30分前に稼働再開

# Local temp dir (ECS/Lambda friendly)
LOCAL_OUT_DIR = "/tmp/no_game"

# S3 output
S3_BUCKET_NO_ECS = "aws-s3-no-ecs-task-time-csv"
S3_KEY_PREFIX = ""  # 空でもOK
S3_REGION = "ap-northeast-1"  # boto3は自動でもOK

# Logging
VERBOSE = "1"


def log(msg: str):
    if VERBOSE:
        print(msg)


# =========================
# League Filters (same as your futureData style)
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


# =========================
# Helpers
# =========================

def text_clean(s: str) -> str:
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


# =========================
# Flashscore navigation
# =========================

def goto_football_top(page):
    log("🌐 Flashscore トップへアクセス...")
    page.goto(FLASHCORE_URL, timeout=45000, wait_until="domcontentloaded")

    # Cookie banner
    try:
        page.locator("#onetrust-accept-btn-handler").click(timeout=2000)
        log("✅ Cookieバナーを閉じました")
    except:
        pass

    # Click "サッカー" just in case
    try:
        soccer_btn = page.locator("a,button").filter(has_text="サッカー").first
        if soccer_btn and soccer_btn.count():
            soccer_btn.click(timeout=4000)
            time.sleep(0.8)
    except:
        pass

    # Switch to "開催予定" tab
    try:
        tab = page.locator("div.filters__tab[data-analytics-alias='scheduled']").first
        if tab and tab.count():
            tab.click(timeout=4000)
        else:
            tab = page.locator("div.filters__tab").filter(has_text=re.compile(r"(開催予定)")).first
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
    log("📂 折りたたみリーグ（非表示）を展開します...")
    btn_selector = (
        "button[data-testid='wcl-accordionButton']"
        "[aria-label='リーグ全試合 表示']"
    )
    max_loops = 200
    for _ in range(max_loops):
        btns = page.locator(btn_selector)
        count = btns.count()
        if count == 0:
            log("   ✅ すべての折りたたみリーグを展開しました")
            return
        log(f"   残り『表示』ボタン数: {count}")
        btn = btns.first
        try:
            btn.scroll_into_view_if_needed()
        except:
            pass
        try:
            btn.click(timeout=2000)
        except Exception as e:
            log(f"   ⚠️ ボタンクリック失敗: {e}")
            break
        page.wait_for_timeout(200)
    log("   ⚠️ ループ上限。まだ非表示が残っている可能性あり。")


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


# =========================
# Match row parsing
# =========================

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

    # Old UI
    try:
        h = row.locator(".event__participant--home .event__participant--name").first
        a = row.locator(".event__participant--away .event__participant--name").first
        if h.count():
            home = text_clean(h.text_content() or "")
        if a.count():
            away = text_clean(a.text_content() or "")
    except:
        pass

    # Other patterns
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
        log(f"⚠️ チーム名取得失敗: time={ktime}, snippet={snippet}")

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


# =========================
# Match page -> Country/League + filter
# =========================

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

        spans = page.locator("nav[data-testid='wcl-breadcrumbs'] span[data-testid='wcl-scores-overline-03']")
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
        category = f"{country}: {league}" if (country and league) else (country or league or "")

        if not category:
            log("⏭️ カテゴリ取得失敗 → 除外")
            continue

        if not any(c in category for c in CONTAINS_LIST):
            log(f"⏭️ 対象外リーグ: {category}")
            continue

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


# =========================
# Slot calculation
# =========================

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
    dts: List[datetime.datetime] = []
    for m in matches:
        dt = _parse_match_datetime(m.get("datetime_str", ""))
        if dt:
            dts.append(dt)

    if not dts:
        log("⚠️ 有効な試合開始時刻がありません。")
        return []

    target_date = dts[0].date()
    start_of_day = datetime.datetime.combine(target_date, datetime.time(0, 0))
    end_of_day = start_of_day + datetime.timedelta(days=1)

    log(f"\n🎯 対象日: {target_date}\n")
    log("📍 対象日の試合開始時刻（対象リーグのみ）:")
    for dt in sorted(dts):
        log(" ・ " + dt.strftime("%Y-%m-%d %H:%M"))
    log("")

    return _calc_free_slots_for_date(start_of_day, end_of_day, dts, min_gap_minutes)


def convert_free_slots_to_ecs_slots(
    free_slots: List[Tuple[datetime.datetime, datetime.datetime]],
    post_match_buffer_minutes: int = 180,
    pre_match_buffer_minutes: int = 30
) -> List[Tuple[datetime.datetime, datetime.datetime]]:
    ecs_slots: List[Tuple[datetime.datetime, datetime.datetime]] = []
    delta_post = datetime.timedelta(minutes=post_match_buffer_minutes)
    delta_pre = datetime.timedelta(minutes=pre_match_buffer_minutes)

    for free_start, free_end in free_slots:
        ecs_start = free_start + delta_post
        ecs_end = free_end - delta_pre
        if ecs_start < ecs_end:
            ecs_slots.append((ecs_start, ecs_end))
    return ecs_slots


# =========================
# S3 output
# =========================

def upload_json_to_s3(bucket: str, key: str, json_obj) -> bool:
    s3 = boto3.client("s3", region_name=S3_REGION or None)
    body = json.dumps(json_obj, ensure_ascii=False, indent=2).encode("utf-8")
    try:
        s3.put_object(
            Bucket=bucket,
            Key=key,
            Body=body,
            ContentType="application/json; charset=utf-8",
        )
        log(f"✅ S3へアップロード完了: s3://{bucket}/{key}")
        return True
    except ClientError as e:
        log(f"❌ S3アップロード失敗: {e}")
        return False


# =========================
# Main flow
# =========================

def fetch_nextday_matches_and_free_slots(min_gap_minutes: int = 0):
    matches: List[Dict[str, str]] = []
    scraped_ok = False

    with sync_playwright() as p:
        browser = p.chromium.launch(
            headless=HEADLESS,
            slow_mo=SLOW_MO_MS,
            args=["--no-sandbox", "--disable-dev-shm-usage", "--disable-gpu"],
        )
        ctx = browser.new_context(user_agent=USER_AGENT, locale=LOCALE, timezone_id=TIMEZONE_ID)
        page = ctx.new_page()

        goto_football_top(page)

        ok = click_next_day(page)
        if not ok:
            log("❌ 翌日に進めなかったため終了します。")
            try: page.close()
            except: pass
            browser.close()
            return [], [], False

        log("==================== 翌日の試合を取得 ====================")
        day_results = collect_scheduled_matches_on_current_day(page)
        matches.extend(day_results)
        scraped_ok = True

        try: page.close()
        except: pass

        enrich_and_filter_by_league(ctx, matches)
        browser.close()

    free_slots = get_free_slots_for_matches(matches, min_gap_minutes=min_gap_minutes)
    return matches, free_slots, scraped_ok

def fetch_nextday_matches_and_free_slots(min_gap_minutes: int = 0):
    matches: List[Dict[str, str]] = []
    scraped_ok = False

    with sync_playwright() as p:
        browser = p.chromium.launch(
            headless=HEADLESS,
            slow_mo=SLOW_MO_MS,
            args=["--no-sandbox", "--disable-dev-shm-usage", "--disable-gpu"],
        )
        ctx = browser.new_context(user_agent=USER_AGENT, locale=LOCALE, timezone_id=TIMEZONE_ID)
        page = ctx.new_page()

        goto_football_top(page)

        ok = click_next_day(page)
        if not ok:
            log("❌ 翌日に進めなかったため終了します。")
            try: page.close()
            except: pass
            browser.close()
            return [], [], False  # ← 失敗

        log("==================== 翌日の試合を取得 ====================")
        day_results = collect_scheduled_matches_on_current_day(page)
        matches.extend(day_results)
        scraped_ok = True  # ← 一覧取得はできた、とみなす

        try: page.close()
        except: pass

        enrich_and_filter_by_league(ctx, matches)
        browser.close()

    free_slots = get_free_slots_for_matches(matches, min_gap_minutes=min_gap_minutes)
    return matches, free_slots, scraped_ok

def main():
    matches, free_slots, scraped_ok = fetch_nextday_matches_and_free_slots(
        min_gap_minutes=MIN_GAP_MINUTES
    )

    log("==== 🕒 翌日の『試合がない時間帯（raw free slots）』 ====")
    if not free_slots:
        log("（スキマ時間なし / 試合なし / 取得失敗）")
    else:
        for start, end in free_slots:
            log(f"  {start.strftime('%Y-%m-%d %H:%M')} 〜 {end.strftime('%H:%M')}")

    ecs_slots = convert_free_slots_to_ecs_slots(
        free_slots,
        post_match_buffer_minutes=POST_MATCH_BUFFER_MINUTES,
        pre_match_buffer_minutes=PRE_MATCH_BUFFER_MINUTES
    )

    # ✅ 試合ゼロ件でも「取得成功が確認できたときだけ」丸1日停止
    if scraped_ok and not matches:
        log("⚠️ 対象試合が0件（取得成功）→ 翌日は24時間 ECS停止とします。")
        target_date = datetime.date.today() + datetime.timedelta(days=1)
        start_of_day = datetime.datetime.combine(target_date, datetime.time(0, 0))
        end_of_day   = start_of_day + datetime.timedelta(days=1)
        ecs_slots = [(start_of_day, end_of_day)]
    elif (not scraped_ok) and (not matches):
        log("⚠️ 取得失敗の可能性があるため、24時間停止は行いません（安全側）。")

    log("\n==== 📴 ECS 停止していてよい時間帯（derived from free slots） ====")
    if not ecs_slots:
        log("（停止可能な時間帯はありません）")
    else:
        for start, end in ecs_slots:
            log(f"  {start.strftime('%Y-%m-%d %H:%M')} 〜 {end.strftime('%H:%M')}")

    json_slots = [{"start": s.isoformat(), "end": e.isoformat()} for s, e in ecs_slots]
    json_payload = {
        "generated_at": datetime.datetime.now().isoformat(),
        "timezone": TIMEZONE_ID,
        "min_gap_minutes": MIN_GAP_MINUTES,
        "post_match_buffer_minutes": POST_MATCH_BUFFER_MINUTES,
        "pre_match_buffer_minutes": PRE_MATCH_BUFFER_MINUTES,
        "ecs_stop_intervals": json_slots,
        "matched_games_count": len(matches),
    }

    # 出力ファイル名（日付付き）
    target_date_str = None
    if matches:
        target_date_str = (matches[0].get("datetime_str") or "")[:10].replace("/", "-")
    if not target_date_str:
        target_date_str = (datetime.date.today() + datetime.timedelta(days=1)).strftime("%Y-%m-%d")

    out_dir = Path(LOCAL_OUT_DIR)
    out_dir.mkdir(parents=True, exist_ok=True)
    out_file = f"ecs_slots_{target_date_str}.json"
    out_path = out_dir / out_file

    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(json_payload, f, ensure_ascii=False, indent=2)

    log(f"\n✅ ローカル保存: {out_path}")

    # S3 upload
    s3_key = out_file  # ✅ バケット直下
    ok = upload_json_to_s3(S3_BUCKET_NO_ECS, s3_key, json_payload)

    # ECSのタスクとしては、失敗を exit code に反映しておくと運用しやすい
    if not ok:
        raise SystemExit(2)

    log("\n🎉 完了")


if __name__ == "__main__":
    main()
