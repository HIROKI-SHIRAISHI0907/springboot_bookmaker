# -*- coding: utf-8 -*-
from playwright.sync_api import sync_playwright
import time
import re
import datetime
from typing import List, Dict, Optional

# ============== 共通ユーティリティ ==============

VERBOSE = True

def log(msg: str):
    if VERBOSE:
        print(msg)

def text_clean(s: str) -> str:
    import re
    return re.sub(r"\s+", " ", (s or "")).strip()

def extract_mid(s: str) -> Optional[str]:
    """URLから mid を抽出"""
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

# ============== 今回の HEADER ==============

HEADER_SCHEDULED = [
    "試合国及びカテゴリ","試合予定時間","ホーム順位","アウェー順位","ホームチーム","アウェーチーム",
    "ホームチーム最大得点取得者","アウェーチーム最大得点取得者",
    "ホームチームホーム得点","ホームチームホーム失点","アウェーチームホーム得点","アウェーチームホーム失点",
    "ホームチームアウェー得点","ホームチームアウェー失点",
    "アウェーチームアウェー得点","アウェーチームアウェー失点","試合リンク文字列","データ取得時間"
]

# ============== Flashscore「開催予定」ナビ ==============

def goto_football_top(page):
    """サッカー → 開催予定タブへ遷移"""
    log("🌐 Flashscore トップへアクセス...")
    page.goto("https://www.flashscore.co.jp/", timeout=45000, wait_until="domcontentloaded")

    # Cookieバナーなど
    try:
        page.locator("#onetrust-accept-btn-handler").click(timeout=2000)
        log("✅ Cookieバナーを閉じました")
    except:
        pass

    # サッカーが選ばれていない場合に備えて「サッカー」クリック（だいたい不要だけど保険）
    try:
        soccer_btn = page.locator("a,button").filter(has_text="サッカー").first
        if soccer_btn and soccer_btn.count():
            soccer_btn.click(timeout=4000)
            time.sleep(0.8)
    except:
        pass

    # 「開催予定」タブをクリック
    try:
        # data-analytics-alias='scheduled' が最優先
        tab = page.locator("div.filters__tab[data-analytics-alias='scheduled']").first
        if tab and tab.count():
            tab.click(timeout=4000)
        else:
            # テキストフォールバック
            tab = page.locator("div.filters__tab").filter(
                has_text=re.compile(r"(開催予定)")
            ).first
            tab.click(timeout=4000)
        log("✅ 『開催予定』タブに切り替えました")
    except Exception as e:
        log(f"⚠️ 開催予定タブ切り替え失敗: {e}")

    # 試合行が描画されるまで少し待つ
    try:
        page.wait_for_timeout(1000)
        page.wait_for_load_state("networkidle", timeout=8000)
    except:
        pass

def expand_all_collapsed_leagues(page):
    """
    Flashscore『開催予定』タブで、
    折りたたまれているリーグ（＝リーグ全試合が非表示）をすべて展開する。
    """
    print("📂 折りたたみリーグ（非表示）を展開します...")

    # 「リーグ全試合 表示」ボタン = 今は非表示なので押すと表示になる
    # ※ 完全一致にすること！！ "*='表示'" は「非表示」にもマッチしてしまう
    btn_selector = (
        "button[data-testid='wcl-accordionButton']"
        "[aria-label='リーグ全試合 表示']"
    )
    # 予備で svg のテストIDを使う書き方（必要なら差し替え）
    # btn_selector = (
    #     "button[data-testid='wcl-accordionButton']"
    #     ":has(svg[data-testid='wcl-icon-action-navigation-arrow-down'])"
    # )

    max_loops = 200  # 念のため安全弁

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
        except Exception:
            pass

        try:
            btn.click(timeout=2000)
        except Exception as e:
            print(f"   ⚠️ ボタンクリック失敗: {e}")
            break

        # 非同期展開待ち
        page.wait_for_timeout(200)

    print("   ⚠️ ループ上限に達しました。まだ非表示リーグが残っている可能性があります。")

def _get_match_row_category(row):
    """
    1つの試合行から「試合国及びカテゴリ」（国: リーグ）を推定。
    DOM構造が変わる可能性があるので、必要に応じて微調整してください。
    """
    # 祖先のグループからヘッダテキストを取得するイメージ
    try:
        header = row.locator(
            "xpath=ancestor::div[contains(@class,'event__group')]//div[contains(@class,'event__title')]"
        ).first
        if not header.count():
            return ""

        country = ""
        league = ""

        try:
            country = text_clean(
                header.locator(".event__title--country").first.text_content() or ""
            )
        except:
            pass

        try:
            league = text_clean(
                header.locator(".event__title--type").first.text_content() or ""
            )
        except:
            pass

        if not country and not league:
            # ヘッダの素テキストを fallback にする
            txt = text_clean(header.text_content() or "")
            return txt

        if country and league:
            return f"{country}: {league}"
        return country or league
    except:
        return ""

def _get_match_row_teams_and_time(row):
    """
    1つの試合行から
      - 試合予定時間
      - ホームチーム名
      - アウェーチーム名
    を抽出（旧UI / 新UI 両対応）
    """
    # ===== 時間 =====
    ktime = ""

    # 旧UI: .event__time
    try:
        ktime = text_clean(row.locator(".event__time").first.text_content() or "")
    except Exception:
        pass

    # 新UI: data-testid ベース
    if not ktime:
        try:
            ktime = text_clean(
                row.locator(
                    "[data-testid='wcl-time'], "
                    "[data-testid='wcl-start-time'], "
                    "[data-testid='wcl-time-status']"
                ).first.text_content() or ""
            )
        except Exception:
            pass

    # ===== チーム名 =====
    home = ""
    away = ""

    # パターン①: 旧UI (.event__participant--home / --away)
    try:
        h = row.locator(".event__participant--home .event__participant--name").first
        a = row.locator(".event__participant--away .event__participant--name").first
        if h.count():
            home = text_clean(h.text_content() or "")
        if a.count():
            away = text_clean(a.text_content() or "")
    except Exception:
        pass

    # パターン②: 旧UI フォールバック (.event__participant)
    if not home or not away:
        try:
            ps = row.locator(".event__participant .event__participant--name")
            if ps.count() >= 2:
                if not home:
                    home = text_clean(ps.first.text_content() or "")
                if not away:
                    away = text_clean(ps.last.text_content() or "")
        except Exception:
            pass

    # パターン③: 新UI 明示的セレクタ
    #   <div class="wcl-participant_bctDY event__homeParticipant" ...>
    #       <span class="wcl-name_jjfMf" data-testid="wcl-scores-simple-text-01">...</span>
    #   </div>
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

            if h and h.count():
                if not home:
                    home = text_clean(h.text_content() or "")
            if a and a.count():
                if not away:
                    away = text_clean(a.text_content() or "")
        except Exception:
            pass

    # パターン④: 新UI さらにゆるく、全 participant から先頭/末尾
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
        except Exception:
            pass

    # パターン⑤: 最後の最後に <img alt="チーム名"> を使う（保険）
    if not home or not away:
        try:
            imgs = row.locator("[data-testid='wcl-matchRow-participant'] img[data-testid='wcl-participantLogo']")
            n_img = imgs.count()
            if n_img >= 2:
                if not home:
                    alt0 = imgs.nth(0).get_attribute("alt") or ""
                    home = text_clean(alt0)
                if not away:
                    alt1 = imgs.nth(n_img - 1).get_attribute("alt") or ""
                    away = text_clean(alt1)
        except Exception:
            pass

    # デバッグ用ログ（必要なら）
    if not home or not away:
        try:
            snippet = (row.inner_text() or "").strip().replace("\n", " ")[:200]
        except Exception:
            snippet = "<inner_text 取得失敗>"
        print(f"⚠️ チーム名取得失敗: time={ktime}, snippet={snippet}")

    return ktime, home, away

def _get_match_row_link(row) -> str:
    """試合行から matchURL を抽出"""
    try:
        a = row.locator("a.eventRowLink[href*='/match/'][href*='?mid=']").first
        if a and a.count():
            href = a.get_attribute("href") or ""
            # 相対パスの可能性があれば補完
            if href.startswith("http"):
                return href
            return "https://www.flashscore.co.jp" + href
    except:
        pass
    return ""

def collect_scheduled_matches_on_current_day(page) -> List[Dict[str, str]]:
    """
    現在表示中の日付（開催予定タブ）から試合情報を収集。
    ここでは HEADER_SCHEDULED のカラムをすべて作るが、順位・得点系は空欄のまま。
    """
    # 「開催予定」タブ上で、念のためニュースなどではなく event__match を待つ
    try:
        page.wait_for_selector("div.event__match", timeout=12000)
    except:
        log("⚠️ event__match が見つからないまま続行")

    # リーグを展開
    expand_all_collapsed_leagues(page)

    # 試合行取得: scheduled 用のクラスがあればそれを優先
    rows = page.locator("div.event__match.event__match--scheduled")
    if rows.count() == 0:
        # フォールバック: すべての試合行
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
            cat  = _get_match_row_category(row)
            log(f"row:  {ktime}, {home}, {away}, {link}, {cat}")

            mid = extract_mid(link)
            if mid and mid in seen_mids:
                log(f"   ⏭️ 重複試合(mid={mid})をスキップ")
                continue
            if mid:
                seen_mids.add(mid)

            d = {k: "" for k in HEADER_SCHEDULED}
            d["試合国及びカテゴリ"] = cat
            d["試合予定時間"] = ktime
            d["ホームチーム"] = home
            d["アウェーチーム"] = away
            d["試合リンク文字列"] = link
            d["データ取得時間"] = now_str

            # 以下の項目は「具体的な処理（どこから取るか）を後で詰める」前提で空欄のまま
            #   ホーム順位 / アウェー順位
            #   最大得点取得者
            #   ホーム/アウェー 得点・失点（ホーム戦/アウェー戦）
            # → ここで別途、試合詳細/チーム情報/順位表等から埋める処理を後で追加予定

            results.append(d)

            log(f"   [{i+1}/{n}] {cat} | {ktime} | {home} vs {away} | mid={mid}")
        except Exception as e:
            log(f"   ⚠️ 行{i}でエラー: {e}")
            continue

    log(f"✅ 当日分 取得件数: {len(results)}")
    return results

def click_next_day(page) -> bool:
    """
    「翌日」ボタンをクリックして日付を1日進める。
    成功したら True, 見つからなければ False。
    """
    try:
        btn = page.locator("button.wcl-arrow_YpdN4[data-day-picker-arrow='next']").first
        if not btn or not btn.count():
            log("⚠️ 翌日ボタンが見つかりませんでした")
            return False
        btn.click(timeout=3000)
        log("➡️ 『翌日』ボタンをクリックしました")
        # 非同期で内容が差し替わるので少し待機
        time.sleep(1.0)
        try:
            page.wait_for_load_state("networkidle", timeout=8000)
        except:
            pass
        return True
    except Exception as e:
        log(f"⚠️ 翌日ボタンクリック失敗: {e}")
        return False

# ============== 順位表 ==============

def build_standings_url_from_match_url(match_url: str) -> str:
    """
    試合ページURLを順位表タブのURLに変換する。
    例:
      /match/soccer/buhimba-.../police-.../?mid=xxx
      → /match/soccer/buhimba-.../police-.../standings/?mid=xxx
    """
    if not match_url:
        return ""
    if "/standings/" in match_url:
        return match_url
    # "/?mid=" の直前に "/standings" を差し込む
    return re.sub(r"/(\?mid=)", r"/standings/\1", match_url, count=1)

# ============== 順位表からホーム、アウェーチームの順位取得 ==============

def get_team_ranks_from_standings_table(page, home_name: str, away_name: str):
    """
    すでに「順位表」タブ（オーバーオール）が表示されている page から、
    ホーム＆アウェーチームの順位（rank）を取得する。

    戻り値: (home_rank, away_rank)  ※見つからなければ "" のまま
    """
    home_name_norm = text_clean(home_name)
    away_name_norm = text_clean(away_name)

    home_rank = ""
    away_rank = ""

    # テーブル本体の行
    rows = page.locator("div.ui-table__body > div.ui-table__row")
    n_rows = rows.count()

    for i in range(n_rows):
        row = rows.nth(i)
        try:
            team_elem = row.locator(".tableCellParticipant__name").first
            if not team_elem.count():
                continue
            team_name = text_clean(team_elem.text_content() or "")

            rank_elem = row.locator(".tableCellRank").first
            if not rank_elem.count():
                continue
            rank_text = text_clean(rank_elem.text_content() or "")
            # "4." みたいな表記を "4" に正規化
            rank_text = rank_text.rstrip(".").strip()

            if not home_rank and team_name == home_name_norm:
                home_rank = rank_text
            if not away_rank and team_name == away_name_norm:
                away_rank = rank_text

            if home_rank and away_rank:
                break
        except:
            continue

    return home_rank, away_rank

# ============== 1試合分の国リーグ,順位を取得する関数 ==============

def fetch_ranks_for_match(page, match_url: str, home_name: str, away_name: str):
    """
    試合URLから順位表タブに飛び、
      - ホーム順位
      - アウェー順位
      - 国名
      - リーグ名（ラウンド付き）
    を取得する。

    戻り値: (home_rank, away_rank, country, league)
    """
    if not match_url:
        return "", "", "", ""

    standings_url = build_standings_url_from_match_url(match_url)

    try:
        log(f"   📊 順位表取得: {standings_url}")
        page.goto(standings_url, timeout=25000, wait_until="domcontentloaded")

        # 念のため「順位表」タブが開いているかチェックして、開いていなければクリック
        try:
            standings_tab = page.locator(
                "a[data-analytics-alias='stats-detail'] button, "
                "a[href*='/standings/'] button"
            ).first
            if standings_tab and standings_tab.count():
                # data-selected="true" じゃなければクリック
                selected = standings_tab.get_attribute("data-selected")
                if selected != "true":
                    standings_tab.click(timeout=3000)
                    page.wait_for_timeout(500)
        except:
            pass
        
        # 上部の国＆リーグ名を取得
        country, league = get_country_and_league_from_match_page(page)

        # テーブルが描画されるのを待つ
        page.wait_for_selector("div.ui-table__body div.ui-table__row", timeout=12000)

        # テーブルからランク抽出
        home_rank, away_rank = get_team_ranks_from_standings_table(page, home_name, away_name)
        log(f"      → rank: home={home_rank}, away={away_rank}, country={country}, league={league}")
        return home_rank, away_rank, country, league

    except Exception as e:
        log(f"   ⚠️ 順位表取得失敗: {e}")
        return "", "", "", ""

# ============== 全試合に対して順位を埋める ==============

def fill_ranks_for_matches(ctx, matches: List[Dict[str, str]]):
    """
    すでに「開催予定」で収集した試合リストに対して、
    各試合ページの順位表から
      - ホーム順位
      - アウェー順位
      - 試合国及びカテゴリ（国: リーグ）
    を埋める。
    """
    if not matches:
        return

    page = ctx.new_page()  # 順位表専用タブ

    for idx, m in enumerate(matches):
        url = m.get("試合リンク文字列") or ""
        home = m.get("ホームチーム") or ""
        away = m.get("アウェーチーム") or ""

        if not url or not home or not away:
            continue

        log(f"=== 順位取得 {idx+1}/{len(matches)} ===")
        home_rank, away_rank, country, league = fetch_ranks_for_match(page, url, home, away)

        if home_rank:
            m["ホーム順位"] = home_rank
        if away_rank:
            m["アウェー順位"] = away_rank

        # 国＋リーグが取れたら「試合国及びカテゴリ」を上書き
        if country or league:
            if country and league:
                cat = f"{country}: {league}"
            else:
                cat = country or league
            m["試合国及びカテゴリ"] = cat

    page.close()

# ============== 国リーグ名を取得 ==============

def get_country_and_league_from_match_page(page):
    """
    試合ページ（サマリー / 順位表タブ）のパンくずから
    - 国名
    - リーグ名（＋ラウンド）
    を取得する。

    例（パンくず）:
      サッカー > イングランド > プレミアリーグ - ラウンド 14
    """
    country = ""
    league = ""

    try:
        # 念のためパンくずが描画されるのを軽く待つ
        try:
            page.wait_for_selector(
                "nav[data-testid='wcl-breadcrumbs'] span[data-testid='wcl-scores-overline-03']",
                timeout=3000
            )
        except Exception:
            # 待機失敗しても続行（あとで count=0 なら分かる）
            pass

        spans = page.locator(
            "nav[data-testid='wcl-breadcrumbs'] span[data-testid='wcl-scores-overline-03']"
        )
        count = spans.count()

        # デバッグ用：何が取れているか確認したい時に有効化
        # print(f"[DEBUG] breadcrumb span count={count}")
        # for i in range(count):
        #     print(f"[DEBUG] span[{i}] = {text_clean(spans.nth(i).text_content() or '')}")

        if count == 0:
            return "", ""

        texts = []
        for i in range(count):
            txt = text_clean(spans.nth(i).text_content() or "")
            if txt:
                texts.append(txt)

        # 期待パターン:
        #   0: サッカー
        #   1: イングランド（国）
        #   2: プレミアリーグ - ラウンド 14（リーグ）
        #
        # まず「サッカー」が先頭にあればスキップする
        start_idx = 0
        if texts and texts[0] == "サッカー":
            start_idx = 1

        if len(texts) > start_idx:
            country = texts[start_idx]
        if len(texts) > start_idx + 1:
            league = texts[start_idx + 1]

    except Exception:
        # 何かあっても country, league は "" のまま返す
        pass

    return country, league

# ============== メイン入口 ==============

def fetch_scheduled_matches(days) -> List[Dict[str, str]]:
    """
    Flashscore 開催予定タブから、
      - 今日（表示中の日付）
      - 翌日以降（days-1回『翌日』をクリック）
    の試合情報を取得してリストで返す。

    days=2 → 今日＋翌日
    """
    all_results: List[Dict[str, str]] = []

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

        # ① 開催予定タブから全試合を集める
        for day_idx in range(days):
            if day_idx > 0:
                ok = click_next_day(page)
                if not ok:
                    log("⏭️ 翌日への遷移ができなかったため、以降の取得はスキップします")
                    break
            log(f"==================== 日付オフセット {day_idx} 日目 ====================")
            day_results = collect_scheduled_matches_on_current_day(page)
            all_results.extend(day_results)

        # もう開催予定タブのページは不要なので閉じてもOK
        page.close()

        # ② 各試合ページの「順位表」から
        #    ホーム順位 / アウェー順位 / 国＆リーグを埋める
        fill_ranks_for_matches(ctx, all_results)

        browser.close()

    log(f"🎉 総取得件数: {len(all_results)}")
    return all_results

if __name__ == "__main__":
    # テスト実行例
    matches = fetch_scheduled_matches(days=3)
    print(f"総件数: {len(matches)}")
    if matches:
        # 先頭1件だけサンプル表示
        from pprint import pprint
        pprint(matches[0])
