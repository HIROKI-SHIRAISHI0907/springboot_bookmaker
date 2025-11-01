# -*- coding: utf-8 -*-
from playwright.sync_api import sync_playwright
import time, re
# 追加
from urllib.parse import urlsplit, urlunsplit
import datetime, os
import pandas as pd
from typing import Optional, List
import pickle

BOT_WALL_PAT = re.compile(r"(Just a moment|Access Denied|verify you are human|チェック|確認)", re.I)
STAT_CONTAINER = "div.section"

SAVE_DIR = "/Users/shiraishitoshio/bookmaker/outputs"

SEQMAP_PATH = os.path.join(SAVE_DIR, "seqmap.pkl")

# ====== グローバル変数 ======
SEQMAP = {}  # 試合IDごとの連番管理用

# ===== HEADER =====
HEADER = [
    "ホーム順位","試合国及びカテゴリ","試合時間","ホームチーム","ホームスコア","アウェー順位","アウェーチーム",
    "アウェースコア","ホーム期待値","アウェー期待値","ホーム枠内ゴール期待値","アウェー枠内ゴール期待値",
    "ホームボール支配率","アウェーボール支配率","ホームシュート数","アウェーシュート数",
    "ホーム枠内シュート数","アウェー枠内シュート数","ホーム枠外シュート数","アウェー枠外シュート数",
    "ホームブロックシュート","アウェーブロックシュート","ホームビッグチャンス","アウェービッグチャンス",
    "ホームコーナーキック","アウェーコーナーキック","ホームボックス内シュート","アウェーボックス内シュート",
    "ホームボックス外シュート","アウェーボックス外シュート","ホームゴールポスト","アウェーゴールポスト","ホームヘディングゴール","アウェーヘディングゴール",
    "ホームキーパーセーブ","アウェーキーパーセーブ","ホームフリーキック","アウェーフリーキック",
    "ホームオフサイド","アウェーオフサイド","ホームファウル","アウェーファウル",
    "ホームイエローカード","アウェーイエローカード","ホームレッドカード","アウェーレッドカード","ホームスローイン","アウェースローイン",
    "ホーム相手ボックスタッチ","アウェー相手ボックスタッチ","ホームパス","アウェーパス","ホームロングパス","アウェーロングパス","ホームファイナルサードパス","アウェーファイナルサードパス",
    "ホームクロス","アウェークロス","ホームタックル","アウェータックル","ホームクリア","アウェークリア","ホームデュエル勝利数","アウェーデュエル勝利数",
    "ホームインターセプト","アウェーインターセプト",
    "スコア時間","天気","気温","湿度","審判名","ホーム監督名","アウェー監督名","ホームフォーメーション","アウェーフォーメーション",
    "スタジアム","収容人数","観客数","場所","ホームチーム最大得点取得者","アウェーチーム最大得点取得者","ホームチーム最大得点取得者出場状況","アウェーチーム最大得点取得者出場状況",
    "ホームチームホーム得点","ホームチームホーム失点","アウェーチームホーム得点","アウェーチームホーム失点","ホームチームアウェー得点","ホームチームアウェー失点",
    "アウェーチームアウェー得点","アウェーチームアウェー失点","通知フラグ","試合リンク文字列","ゴール時間","選手名","判定結果","ホームチームスタイル","アウェイチームスタイル",
    "ゴール確率","得点予想時間","試合ID","通番","ソート用秒"
]

STAT_KEY_MAP = {
    # 攻撃・得点関連
    "アタック:期待値（xG）": ("ホーム期待値", "アウェー期待値"),
    "アタック:枠内ゴール期待値": ("ホーム枠内ゴール期待値", "アウェー枠内ゴール期待値"),
    "ポゼッション:ボール支配率": ("ホームボール支配率", "アウェーボール支配率"),
    "シュート:シュート数": ("ホームシュート数", "アウェーシュート数"),
    "シュート:枠内シュート数": ("ホーム枠内シュート数", "アウェー枠内シュート数"),
    "シュート:枠外シュート数": ("ホーム枠外シュート数", "アウェー枠外シュート数"),
    "シュート:ブロックシュート": ("ホームブロックシュート", "アウェーブロックシュート"),
    "アタック:ビッグチャンス": ("ホームビッグチャンス", "アウェービッグチャンス"),
    "セットプレー:コーナーキック": ("ホームコーナーキック", "アウェーコーナーキック"),
    "シュート:ボックス内シュート": ("ホームボックス内シュート", "アウェーボックス内シュート"),
    "シュート:ボックス外シュート": ("ホームボックス外シュート", "アウェーボックス外シュート"),
    "シュート:ポストヒット": ("ホームゴールポスト", "アウェーゴールポスト"),
    "シュート:ヘディングゴール": ("ホームヘディングゴール", "アウェーヘディングゴール"),

    # ディフェンス・反則
    "ディフェンス:キーパーセーブ": ("ホームキーパーセーブ", "アウェーキーパーセーブ"),
    "ディフェンス:フリーキック": ("ホームフリーキック", "アウェーフリーキック"),
    "ディフェンス:オフサイド": ("ホームオフサイド", "アウェーオフサイド"),
    "ディフェンス:ファウル": ("ホームファウル", "アウェーファウル"),
    "カード:イエローカード": ("ホームイエローカード", "アウェーイエローカード"),
    "カード:レッドカード": ("ホームレッドカード", "アウェーレッドカード"),
    "ディフェンス:スローイン": ("ホームスローイン", "アウェースローイン"),

    # パス・ビルドアップ
    "パス:相手ボックスタッチ": ("ホーム相手ボックスタッチ", "アウェー相手ボックスタッチ"),
    "パス:総パス数": ("ホームパス", "アウェーパス"),
    "パス:ロングパス": ("ホームロングパス", "アウェーロングパス"),
    "パス:ファイナルサードパス": ("ホームファイナルサードパス", "アウェーファイナルサードパス"),
    "パス:クロス": ("ホームクロス", "アウェークロス"),

    # 守備
    "ディフェンス:タックル": ("ホームタックル", "アウェータックル"),
    "ディフェンス:クリア": ("ホームクリア", "アウェークリア"),
    "ディフェンス:デュエル勝利": ("ホームデュエル勝利数", "アウェーデュエル勝利数"),
    "ディフェンス:インターセプト": ("ホームインターセプト", "アウェーインターセプト"),
}

VERBOSE = True  # ログをたくさん出す場合は True

def log(msg: str):
    if VERBOSE:
        print(msg)

# ================= ユーティリティ =================

def load_seqmap():
    global SEQMAP
    if os.path.exists(SEQMAP_PATH):
        with open(SEQMAP_PATH, "rb") as f:
            SEQMAP = pickle.load(f)

def save_seqmap():
    with open(SEQMAP_PATH, "wb") as f:
        pickle.dump(SEQMAP, f)

def text_clean(s: str) -> str:
    return re.sub(r"\s+", " ", (s or "")).strip()

def wait_netidle(pg, ms=2500):
    try:
        pg.wait_for_load_state("networkidle", timeout=ms)
    except:
        pass

# ============== 試合ページ：基本情報 ==============

def get_display_time(pg):
    sels = [
        ".fixedHeaderDuel:not(.fixedHeaderDuel--isHidden) [data-testid='wcl-time']",
        ".duelParticipant__container [data-testid='wcl-time']",
        "[data-testid='wcl-time']",
    ]
    for s in sels:
        try:
            el = pg.locator(s).first
            if el.count():
                t = text_clean(el.text_content() or "")
                if t:
                    return re.sub(r"\s+", "", t)
        except:
            pass
    return ""

def get_home_away_names(pg):
    try:
        cont = pg.locator("div.duelParticipant__container").first
        if not cont.count():
            cont = pg
        # テキスト優先
        h = cont.locator(".duelParticipant__home a.participant__participantName").first
        a = cont.locator(".duelParticipant__away a.participant__participantName").first
        home = text_clean(h.text_content()) if h.count() else ""
        away = text_clean(a.text_content()) if a.count() else ""
        # フォールバック：エンブレムの alt
        if not home:
            img = cont.locator(".duelParticipant__home img.participant__image").first
            home = text_clean(img.get_attribute("alt") or "")
        if not away:
            img = cont.locator(".duelParticipant__away img.participant__image").first
            away = text_clean(img.get_attribute("alt") or "")
        return home, away
    except:
        return "", ""

def get_scores(pg):
    """
    スコアを安定取得。
    優先順:
      1) 固定ヘッダーの可視スコア
      2) 詳細スコアの live ラッパー
      3) 詳細スコアの汎用ラッパー
      4) テキスト全体からの最終フォールバック
    """
    def _clean(s: str) -> str:
        return re.sub(r"\s+", "", (s or "").replace("\u00A0", " ")).strip()

    def _from_container(el):
        # 1) span 群から divider を除外し、数字だけを抽出
        try:
            spans = el.locator("span")
            n = spans.count()
            vals = []
            for i in range(n):
                sp = spans.nth(i)
                cls = (sp.get_attribute("class") or "")
                if "divider" in cls:  # detailScore__divider / fixedScore__divider など
                    continue
                txt = _clean(sp.text_content() or "")
                if re.fullmatch(r"\d+", txt):
                    vals.append(txt)
            if len(vals) >= 2:
                return vals[0], vals[-1]
        except:
            pass

        # 2) コンテナのテキストを正規化して "d - d" を拾う（ハイフン揺れ対応）
        try:
            t = _clean(el.inner_text() or "")
            # 例: 1-0 / 1 – 0 / 1—0 など
            m = re.search(r"(\d+)\s*[\-\u2212\u2012\u2013\u2014\u2015]\s*(\d+)", t)
            if m:
                return m.group(1), m.group(2)
        except:
            pass
        return "", ""

    # 1) 固定ヘッダー（表示されている方だけ）
    try:
        fx = pg.locator(".fixedHeaderDuel:not(.fixedHeaderDuel--isHidden) .fixedScore").first
        if fx and fx.count():
            h, a = _from_container(fx)
            if h and a:
                return h, a
    except:
        pass

    # 2) ライブ用の詳細スコア
    try:
        live = pg.locator("div.detailScore__wrapper.detailScore__live").first
        if live and live.count():
            h, a = _from_container(live)
            if h and a:
                return h, a
    except:
        pass

    # 3) 汎用の詳細スコア（ライブクラスが無いケース）
    try:
        wrap = pg.locator("div.detailScore__wrapper").first
        if wrap and wrap.count():
            h, a = _from_container(wrap)
            if h and a:
                return h, a
    except:
        pass

    # 4) 最終フォールバック：ページ全体の主要スコア候補
    try:
        # data-testid でまとめて取れるレイアウトも存在（「1–0」丸ごと）
        tnodes = pg.locator("[data-testid='wcl-score']").all()
        for el in tnodes:
            t = (el.text_content() or "").replace("\u00A0", " ")
            m = re.search(r"(\d+)\s*[\-\u2212\u2012\u2013\u2014\u2015]\s*(\d+)", t)
            if m:
                return m.group(1).strip(), m.group(2).strip()
    except:
        pass

    return "", ""

# ============== 統計タブ：遷移と待機 =============
STAT_CONTAINER = "[data-analytics-context='tab-match-statistics']"

def goto_statistics_match_tab(pg):
    """試合詳細URLから統計ページ（/summary/stats/0/）へ直接遷移"""
    import re
    cur = pg.url
    # 例: https://www.flashscore.co.jp/match/soccer/imabari-0fQDWIvJ/v-varen-nagasaki-hl74HAcF/?mid=4SyKJEwn
    # → https://www.flashscore.co.jp/match/soccer/imabari-0fQDWIvJ/v-varen-nagasaki-hl74HAcF/summary/stats/0/?mid=4SyKJEwn
    if "/summary/stats" not in cur:
        base, mid_part = cur.split("?", 1) if "?" in cur else (cur, "")
        if not base.endswith("/"):
            base += "/"
        stats_url = re.sub(r"/summary/[^/?]*/?", "/summary/stats/0/", base)
        if stats_url == base:
            # summary が存在しないURL → 追記
            stats_url = base + "summary/stats/0/"
        if mid_part:
            stats_url = stats_url + "?" + mid_part
        # 統計ページに遷移
        pg.goto(stats_url, timeout=45000, wait_until="domcontentloaded")

    # 統計セクションが描画されるまで待機
    pg.wait_for_selector("[data-testid='wcl-statistics']", timeout=20000)
    # 念のため少し待つ（遅延ロード対策）
    time.sleep(1.2)


def scrape_stats_pairs(pg):
    """
    { "セクション名:指標名": ("ホーム値","アウェー値") } を返す
    値は <strong> のメインと直下の <span>（括弧は除去）を連結する
    """
    goto_statistics_match_tab(pg)

    def strip_paren(s: str) -> str:
        s = (s or "").strip()
        return s[1:-1] if s.startswith("(") and s.endswith(")") else s

    def get_val(side_el):
        if not side_el or not side_el.count():
            return ""
        main = ""
        sub = ""
        try:
            main = (side_el.locator("strong").first.inner_text() or "").strip()
        except: pass
        try:
            sub = (side_el.locator(":scope > span").first.inner_text() or "").strip()
            sub = strip_paren(sub)
        except: pass
        return f"{main} {sub}".strip()

    stats = {}
    sections = pg.locator(f"{STAT_CONTAINER} div.section")
    for si in range(sections.count()):
        sec = sections.nth(si)
        try:
            sec_title = (sec.locator(".sectionHeader").first.inner_text() or "").strip()
        except:
            sec_title = ""
        rows = sec.locator("[data-testid='wcl-statistics']")
        for i in range(rows.count()):
            r = rows.nth(i)
            # 指標名
            try:
                label = (r.locator("[data-testid='wcl-statistics-category'] strong").first.inner_text() or "").strip()
            except:
                label = ""
            if not label:
                continue
            # 左右の数値（※ data-testid だけで取得。先頭=ホーム、末尾=アウェー）
            vals = r.locator("[data-testid='wcl-statistics-value']")
            if vals.count() < 2:
                continue
            home = get_val(vals.first)
            away = get_val(vals.last)

            key = f"{sec_title}:{label}" if sec_title else label
            stats[key] = (home, away)

    return stats

def _read_value_box(box):
    """ホーム／アウェイの値をテキストで抽出"""
    try:
        strongs = box.locator("strong")
        texts = [s.inner_text().strip() for s in strongs.all() if s.inner_text().strip()]
        return " ".join(texts)
    except:
        return ""

def wait_statistics_rows(pg, max_tries=20):
    """[data-testid='wcl-statistics'] が出るまで、スクロール＋待機で粘る"""
    rows = pg.locator("[data-testid='wcl-statistics']")
    for _ in range(max_tries):
        try:
            c = rows.count()
            if c > 0:
                return True
        except: pass
        try: pg.mouse.wheel(0, 1400)
        except: pass
        try: pg.wait_for_load_state("networkidle", timeout=1200)
        except: pass
        time.sleep(0.25)
    return rows.count() > 0

def _read_value_box(box):
    """1つの側(ホーム/アウェー)を読む: strong + (span) -> '82% 264/323'"""
    try:
        main = (box.locator("strong").first.inner_text() or "").strip()
    except: main = ""
    try:
        sub  = (box.locator(":scope > span").first.inner_text() or "").strip()
        # (264/323) → 264/323
        if sub.startswith("(") and sub.endswith(")"):
            sub = sub[1:-1].strip()
    except: sub = ""
    if sub and main:
        return f"{main} {sub}"
    return main or sub

def scrape_stats_pairs(pg):
    """
    すべての section(例: シュート/アタック/パス/ディフェンス/キーパー) 内から
    data-testid='wcl-statistics' を抽出。
    """
    goto_statistics_match_tab(pg)
    wait_statistics_rows(pg)

    stats = {}
    # 各セクションを走査
    sections = pg.locator("div.section")
    nsec = sections.count()

    for si in range(nsec):
        sec = sections.nth(si)
        try:
            sec_title = (sec.locator(".sectionHeader").first.inner_text() or "").strip()
        except:
            sec_title = ""
        # 行単位で統計を読む
        rows = sec.locator("[data-testid='wcl-statistics']")
        nrow = rows.count()
        for i in range(nrow):
            r = rows.nth(i)
            try:
                label = (r.locator("[data-testid='wcl-statistics-category'] strong").first.inner_text() or "").strip()
            except:
                label = ""
            if not label:
                continue
            vals = r.locator("[data-testid='wcl-statistics-value']")
            home = _read_value_box(vals.first)
            away = _read_value_box(vals.last)

            key = label
            if sec_title:
                key = f"{sec_title}:{label}"
            stats[key] = (home, away)

    return stats

def wait_statistics_rows(pg, max_tries=12):
    """
    統計行が描画されるまで、スクロール & 待機で粘る。
    """
    rows = pg.locator("[data-analytics-context='tab-match-statistics'] [data-testid='wcl-statistics']")
    for _ in range(max_tries):
        try:
            if rows.count() > 0:
                return True
        except:
            pass
        try:
            pg.mouse.wheel(0, 1600)
        except:
            pass
        time.sleep(0.25)
        try:
            pg.wait_for_load_state("networkidle", timeout=2000)
        except:
            pass
    try:
        return rows.count() > 0
    except:
        return False

# ============== 統計スクレイパ ==============

def scrape_stats_pairs(pg):
    """
    { "ラベル": ("ホーム値","アウェー値") } を返す。
    値は <strong>のメイン値 + <span>の補助値（あれば）を「括弧を外した形」で連結。
    例: 「82% 232/282」
    """
    goto_statistics_match_tab(pg)
    ok = wait_statistics_rows(pg, max_tries=10)
    if not ok:
        return {}

    js = r"""
(() => {
  const clean = s => (s || "").replace(/\u00A0/g, " ").replace(/\s+/g, " ").trim();
  const stripParen = s => {
    const m = /^\((.*)\)$/.exec((s || "").trim());
    return m ? m[1] : (s || "");
  };
  const getVal = (el) => {
    if (!el) return "";
    const main = clean(el.querySelector("strong")?.textContent || "");
    const sub = clean(el.querySelector(":scope > span")?.textContent || "");
    const sub2 = stripParen(sub);
    return sub2 ? (main ? `${main} (${sub2})` : sub2) : main;
  };

  const out = {};
  document.querySelectorAll("div.section").forEach(section => {
    const title = clean(section.querySelector(".sectionHeader")?.textContent || "");
    if (!title) return;
    const catMap = {};
    section.querySelectorAll("div.wcl-row_2oCpS[data-testid='wcl-statistics']").forEach(r => {
      const label = clean(r.querySelector("[data-testid='wcl-statistics-category'] strong")?.textContent || "");
      if (!label) return;
      const home = getVal(r.querySelector(".wcl-homeValue_3Q-7P"));
      const away = getVal(r.querySelector(".wcl-awayValue_Y-QR1"));
      catMap[label] = [home, away];
    });
    if (Object.keys(catMap).length > 0) {
      out[title] = catMap;
    }
  });
  return out;
})();
"""

    try:
        return pg.evaluate(js, STAT_CONTAINER) or {}
    except:
        return {}

def save_to_excel(match_results, output_dir="."):
    """試合データ（list[dict]）を output_x.xlsx 形式で保存"""
    os.makedirs(output_dir, exist_ok=True)
    existing = [f for f in os.listdir(output_dir) if f.startswith("output_") and f.endswith(".xlsx")]
    next_num = len(existing) + 1
    out_path = os.path.join(output_dir, f"output_{next_num}.xlsx")

    # DataFrame 化 & HEADER 順に
    df = pd.DataFrame(match_results)
    for col in HEADER:
        if col not in df.columns:
            df[col] = ""
    df = df[HEADER]

    # 列ごとの非空件数をログ
    try:
        non_empty_counts = df.apply(lambda s: s.astype(str).str.strip().ne("").sum())
        log("📄 [EXCEL] 列ごとの非空件数（上位10列）:")
        top10 = non_empty_counts.sort_values(ascending=False).head(10)
        for col, cnt in top10.items():
            log(f"   - {col}: {cnt}")
        log(f"📄 [EXCEL] 総行数: {len(df)} / 総列数: {len(df.columns)}")
    except Exception as e:
        log(f"⚠️ [EXCEL] 非空件数計算で例外: {e}")

    # Excel保存
    df.to_excel(out_path, index=False)
    print(f"💾 Excel保存完了: {out_path}")

# ============== メイン（ライブ検出→試合巡回） ==============

def main():
    global SEQMAP
    load_seqmap()

    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True, slow_mo=70)
        ctx = browser.new_context(
            user_agent=("Mozilla/5.0 (Macintosh; Intel Mac OS X 13_6) "
                        "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"),
            locale="ja-JP", timezone_id="Asia/Tokyo"
        )
        page = ctx.new_page()

        print("🌐 Flashscoreトップを開きます...")
        page.goto("https://www.flashscore.co.jp/", timeout=45000, wait_until="domcontentloaded")

        # Cookieバナー対応
        try:
            page.locator("#onetrust-accept-btn-handler").click(timeout=1500)
        except:
            pass

        # ライブタブへ
        try:
            live_sel = "div.filters__tab:has(div.filters__text--short:has-text('ライブ')), div.filters__tab:has(div.filters__text--long:has-text('開催中の試合'))"
            page.locator(live_sel).first.click(timeout=4000)
        except:
            pass

        try:
            page.wait_for_selector("div.event__match.event__match--live", timeout=20000)
        except:
            pass

        # =========================
        # 🔹 ライブタブを確実にアクティブ化
        # =========================
        try:
            # 「ライブ / 開催中の試合」のタブを探してクリック（複数回リトライ）
            live_tab = page.locator("div.filters__tab").filter(
                has_text=re.compile(r"(ライブ|開催中の試合)")
            ).first

            for _ in range(3):
                cls = (live_tab.get_attribute("class") or "")
                if "filters__tab--active" in cls:
                    break
                live_tab.click(timeout=2000)
                # タブ切り替え後の描画待ち
                try:
                    page.wait_for_selector("div.event__match", timeout=4000)
                except:
                    pass
                time.sleep(0.4)

            # 状態ログ
            total_rows = page.locator("div.event__match").count()
            live_rows  = page.locator("div.event__match.event__match--live").count()
            print(f"📋 行検出: live={live_rows} / total={total_rows}")
        except Exception as e:
            print(f"⚠️ ライブタブ切替で例外: {e}")

        # =========================
        # 🔹 閉じているリーグ（accordionButton）をすべて開く
        # =========================
        try:
            buttons = page.locator("button[data-testid='wcl-accordionButton']")
            n_btn = buttons.count()
            opened = 0
            print(f"📂 折りたたみボタン検出: {n_btn} 件")

            for i in range(n_btn):
                btn = buttons.nth(i)
                aria = btn.get_attribute("aria-label") or ""
                # 「非表示」が含まれている = 既に開いている（スキップ）
                if "非表示" in aria:
                    continue

                # 「試合表示選択」など → 閉じているので開く
                try:
                    btn.scroll_into_view_if_needed(timeout=1000)
                    btn.click(timeout=1500)
                    opened += 1
                    time.sleep(0.3)
                except Exception as e:
                    print(f"⚠️ ボタンクリック失敗 {i}: {e}")

            if opened:
                print(f"✅ {opened} 件のリーグを展開しました。")
                time.sleep(1.0)  # 展開反映待機
            else:
                print("✅ すべてのリーグは既に展開済み。")
        except Exception as e:
            print(f"⚠️ 折りたたみ展開処理で例外: {e}")


        # =========================
        # 🔹 ライブ行のみからURL抽出（安全）
        # =========================
        links = page.eval_on_selector_all(
            "div.event__match.event__match--live a.eventRowLink[href*='/match/'][href*='?mid=']",
            "els => els.map(e => e.href)"
        ) or []
        
        # URL抽出後の重複除去をより厳密にする
        processed_mids = set()
        unique_links = []
        for link in links:
            mid = extract_mid(link)
            if not mid or mid in processed_mids:
                print(f"⏭️ 既処理試合: {mid}")
                continue
            processed_mids.add(mid)
            unique_links.append(link)
            links = unique_links
            print(f"🎯 重複除去後ライブ試合:{len(links)}件")

        print(f"🎯 ライブ試合 検出:{len(links)}件")


        for i, url in enumerate(links[:], 1):
            print(f"\n[{i}/{len(links[:])}] {url}")
            gp_page = ctx.new_page()
            try:
                gp_page.goto(url, timeout=45000, wait_until="domcontentloaded")

                # BOT防止ページ対応
                if BOT_WALL_PAT.search(gp_page.content() or ""):
                    time.sleep(2)
                    try: gp_page.reload(wait_until="domcontentloaded", timeout=30000)
                    except: pass

                # チーム名・スコア取得
                home, away = get_home_away_names(gp_page)
                hs, aw = get_scores(gp_page)
                print(f"⚽  | {home} {hs}-{aw} {away}")

                # 🔹 統計データ取得
                stats_pairs = scrape_stats_pairs(gp_page)

                # 取得スタッツのダンプ(print [STATS] §{section} )
                debug_dump_stats(stats_pairs)
                print(f"📊 統計データ取得完了 ({len(stats_pairs)}項目)" if stats_pairs else "⚠️ 統計データなし")

                # 🔹 試合メタ情報・順位情報
                meta  = get_match_meta(gp_page)
                ranks = get_match_standings(gp_page, home, away)

                def get_meta(key: str) -> str:
                    v = meta.get(key, "")
                    return v if isinstance(v, str) else ""

                def get_rank_value(key: str):
                    v = ranks.get(key, None) if isinstance(ranks, dict) else None
                    return v if (v is None or isinstance(v, (int, float))) else None

                # 🔹 スタッツ抽出補完
                def get_stat(sec, key):
                    try:
                        return stats_pairs.get(sec, {}).get(key, ["", ""])
                    except:
                        return ["", ""]

                country = get_meta("国")
                league  = get_meta("リーグ")
                game_category = f"{country}: {league}" if country or league else ""

                #🔹 試合メタ情報・順位情報

                # ==========================================
                # ✅ 対象リーグフィルタリング処理を追加
                # ==========================================
                contains_list = [
                    "ケニア: プレミアリーグ","コロンビア: プリメーラ A","タンザニア: プレミアリーグ","イングランド: プレミアリーグ",
                    "イングランド: EFL チャンピオンシップ","イングランド: EFL リーグ 1","エチオピア: プレミアリーグ","コスタリカ: リーガ FPD",
                    "ジャマイカ: プレミアリーグ","スペイン: ラ・リーガ","ブラジル: セリエ A ベターノ","ブラジル: セリエ B","ドイツ: ブンデスリーガ",
                    "韓国: K リーグ 1","中国: 中国スーパーリーグ","日本: J1 リーグ","日本: J2 リーグ","日本: J3 リーグ","インドネシア: スーパーリーグ",
                    "オーストラリア: A リーグ・メン","チュニジア: チュニジア･プロリーグ","ウガンダ: プレミアリーグ","メキシコ: リーガ MX",
                    "フランス: リーグ・アン","スコットランド: プレミアシップ","オランダ: エールディビジ","アルゼンチン: トルネオ・ベターノ",
                    "イタリア: セリエ A","イタリア: セリエ B","ポルトガル: リーガ・ポルトガル","トルコ: スュペル・リグ","セルビア: スーペルリーガ",
                    "日本: WEリーグ","ボリビア: LFPB","ブルガリア: パルヴァ・リーガ","カメルーン: エリート 1","ペルー: リーガ 1",
                    "エストニア: メスタリリーガ","ウクライナ: プレミアリーグ","ベルギー: ジュピラー･プロリーグ","エクアドル: リーガ・プロ",
                    "日本: YBC ルヴァンカップ","日本: 天皇杯"
                ]
                under_list  = ["U17","U18","U19","U20","U21","U22","U23","U24","U25"]
                gender_list = ["女子"]
                exp_list    = ["ポルトガル: リーガ・ポルトガル 2","イングランド: プレミアリーグ 2","イングランド: プレミアリーグ U18"]

                # 判定処理
                # ✅1: contains_list に完全一致するものだけ対象
                if not any(c in game_category for c in contains_list):
                    print(f"⏭️ スキップ対象: {game_category}（リスト外）")
                    gp_page.close()
                    continue

                # ✅2: 年代（Uxx）・女子・例外リーグを含む場合はスキップ
                if any(x in game_category for x in under_list) or any(x in game_category for x in gender_list) or any(x in game_category for x in exp_list):
                    print(f"🚫 除外対象: {game_category}")
                    gp_page.close()
                    continue

                print(f"✅ 処理対象リーグ: {game_category}")

                live = get_meta("試合時間")
                get_record = get_meta("取得時刻")
                home_rank = get_rank_value("home_rank")
                away_rank = get_rank_value("away_rank")
                shot_exp_home, shot_exp_away = get_stat("主なスタッツ", "ゴール期待値（xG）")
                ball_pos_home, ball_pos_away = get_stat("主なスタッツ", "ボール支配率")
                shoot_home, shoot_away = get_stat("主なスタッツ", "シュート数")
                shot_sum_home, shot_sum_away = get_stat("主なスタッツ", "合計シュート")
                shot_in_home, shot_in_away = get_stat("主なスタッツ", "枠内シュート")
                big_chance_home, big_chance_away = get_stat("主なスタッツ", "ビッグチャンス")
                corner_home, corner_away = get_stat("主なスタッツ", "コーナーキック")
                yellow_home, yellow_away = get_stat("主なスタッツ", "イエローカード")
                shot_home, shot_away = get_stat("主なスタッツ", "シュート")
                shot_in_exp_home, shot_in_exp_away = get_stat("シュート", "枠内ゴール期待値（xGOT）")
                shot_out_home, shot_out_away = get_stat("シュート", "枠外シュート")
                shot_block_home, shot_block_away = get_stat("シュート", "シュートブロック")
                shot_in_box_block_home, shot_in_box_block_away = get_stat("シュート", "ボックス内からのシュート")
                shot_out_box_block_home, shot_out_box_block_away = get_stat("シュート", "ボックス外からのシュート")
                shot_post_home, shot_post_away = get_stat("シュート", "ゴール枠に当たる")
                shot_head_home, shot_head_away = get_stat("シュート", "ヘディングによるゴール")
                oppo_in_box_touch_home, oppo_in_box_touch_away = get_stat("アタック", "相手ボックス内でのタッチ")
                offside_home, offside_away = get_stat("アタック", "オフサイド")
                free_kick_home, free_kick_away = get_stat("アタック", "フリーキック")
                pass_home, pass_away = get_stat("パス", "パス")
                long_pass_home, long_pass_away = get_stat("パス", "ロングパス")
                final_third_pass_home, final_third_pass_away = get_stat("パス", "ファイナルサードでのパス")
                cross_home, cross_away = get_stat("パス", "クロス")
                throw_in_home, throw_in_away = get_stat("パス", "スローイン")
                foul_home, foul_away = get_stat("ディフェンス", "ファウル")
                tackle_home, tackle_away = get_stat("ディフェンス", "タックル")
                duel_home, duel_away = get_stat("ディフェンス", "デュエル勝利数")
                clear_home, clear_away = get_stat("ディフェンス", "クリアリング")
                intercept_home, intercept_away = get_stat("ディフェンス", "インターセプト")
                keeper_save_home, keeper_save_away = get_stat("ゴールキーパー", "キーパーセーブ")
                get_link = ranks.get("url", "") if isinstance(ranks, dict) else ""
                referee = get_meta("レフェリー")
                studium = get_meta("開催地")
                capacity = get_meta("収容人数")
                audience = get_meta("参加")

                # ===== ここから追記（d.update(stats_dict) の直後） =====

                def first_nonempty(*vals):
                    for v in vals:
                        if str(v or "").strip():
                            return v
                    return ""

                def put(hkey, akey, hval, aval, overwrite=False):
                    # hkey への書き込み
                    if overwrite or not str(d.get(hkey, "")).strip():
                        d[hkey] = hval if hval is not None else ""

                    # akey が空の場合は何もしない（空キーを作らない）
                    if akey:
                        if overwrite or not str(d.get(akey, "")).strip():
                            d[akey] = aval if aval is not None else ""

                # 🔹 HEADER構造体生成
                d = {col: "" for col in HEADER}
                
                mid = extract_mid(get_link) or ""
                tkey = parse_live_time_to_seconds(meta.get("試合時間", ""))

                last_seq = int(SEQMAP.get(mid, 0))
                seq = last_seq + 1
                SEQMAP[mid] = seq

                # --- メタデータ ---
                put("ホームチーム","", home, "")
                put("アウェーチーム","", away, "")
                put("ホームスコア","", hs, "")
                put("アウェースコア","", aw, "")
                put("試合国及びカテゴリ","", game_category, "")
                put("ホーム順位","アウェー順位", home_rank, away_rank)
                put("試合時間","", live, "")
                put("試合リンク文字列","", get_link, "")

                put("レフェリー","", referee, "")
                put("スタジアム","", studium, "")
                put("収容人数","", capacity, "")
                put("観客数","", audience, "")

                # --- 主なスタッツ（優先的に反映。空欄なら後述の代替で補完） ---
                put("ホーム期待値","アウェー期待値", shot_exp_home, shot_exp_away)
                put("ホームボール支配率","アウェーボール支配率", ball_pos_home, ball_pos_away)

                #   シュート総数は、(シュート数, シュート, 合計シュート) の順で優先
                shoot_home_final = first_nonempty(shoot_home, shot_home, shot_sum_home)
                shoot_away_final = first_nonempty(shoot_away, shot_away, shot_sum_away)
                put("ホームシュート数","アウェーシュート数", shoot_home_final, shoot_away_final)

                put("ホーム枠内シュート数","アウェー枠内シュート数", shot_in_home, shot_in_away)
                put("ホーム枠外シュート数","アウェー枠外シュート数", shot_out_home, shot_out_away)
                put("ホームブロックシュート","アウェーブロックシュート", shot_block_home, shot_block_away)
                put("ホームボックス内シュート","アウェーボックス内シュート", shot_in_box_block_home, shot_in_box_block_away)
                put("ホームボックス外シュート","アウェーボックス外シュート", shot_out_box_block_home, shot_out_box_block_away)
                put("ホームゴールポスト","アウェーゴールポスト", shot_post_home, shot_post_away)
                put("ホームヘディングゴール","アウェーヘディングゴール", shot_head_home, shot_head_away)

                put("ホームビッグチャンス","アウェービッグチャンス", big_chance_home, big_chance_away)
                put("ホームコーナーキック","アウェーコーナーキック", corner_home, corner_away)
                put("ホームイエローカード","アウェーイエローカード", yellow_home, yellow_away)

                # xGOT（枠内ゴール期待値）
                put("ホーム枠内ゴール期待値","アウェー枠内ゴール期待値", shot_in_exp_home, shot_in_exp_away)

                # アタック系
                put("ホーム相手ボックスタッチ","アウェー相手ボックスタッチ", oppo_in_box_touch_home, oppo_in_box_touch_away)
                put("ホームオフサイド","アウェーオフサイド", offside_home, offside_away)
                put("ホームフリーキック","アウェーフリーキック", free_kick_home, free_kick_away)
                put("ホームパス","アウェーパス", pass_home, pass_away)
                put("ホームロングパス","アウェーロングパス", long_pass_home, long_pass_away)
                put("ホームファイナルサードパス","アウェーファイナルサードパス", final_third_pass_home, final_third_pass_away)
                put("ホームクロス","アウェークロス", cross_home, cross_away)
                put("ホームスローイン","アウェースローイン", throw_in_home, throw_in_away)

                # ディフェンス系
                put("ホームファウル","アウェーファウル", foul_home, foul_away)
                put("ホームタックル","アウェータックル", tackle_home, tackle_away)
                put("ホームデュエル勝利数","アウェーデュエル勝利数", duel_home, duel_away)
                put("ホームクリア","アウェークリア", clear_home, clear_away)
                put("ホームインターセプト","アウェーインターセプト", intercept_home, intercept_away)

                put("スコア時間","", get_record, "")

                # GK
                put("ホームキーパーセーブ","アウェーキーパーセーブ", keeper_save_home, keeper_save_away)


                put("試合ID","", mid, "")
                put("通番","", seq, "")
                put("ソート用秒","", tkey, "")
            
                # 今回1行の埋まり具合
                debug_filled_columns(d)
                # ✅ ここで即Excelに1行追記
                append_row_to_excel(d, SAVE_DIR)
                
            except Exception as e:
                print("⚠️ 取得エラー:", e)
            finally:
                try: gp_page.close()
                except: pass

        browser.close()

    save_seqmap()

def get_match_meta(pg):
    """試合ページの国・リーグ・試合ステータス・詳細情報を抽出"""
    meta = {}

    # 現在のURLを保存
    cur_url = pg.url

    # --- ✅ summary タブに移動（統計タブでは情報がない）
    if "/summary" not in cur_url or "/summary/stats" in cur_url:
        summary_url = re.sub(r"/summary/.*", "/summary/", cur_url)
        if not summary_url.endswith("/summary/"):
            summary_url = re.sub(r"/(standings|stats|lineups|odds|commentary).*", "/summary/", cur_url)
        pg.goto(summary_url, timeout=30000, wait_until="domcontentloaded")
        time.sleep(1.2)

    # -------------------------
    # 🔹 パンくずリスト（国・リーグ）
    # -------------------------
    try:
        crumbs = pg.locator("ol.wcl-breadcrumbList_lC9sI li[data-testid='wcl-breadcrumbsItem'] span[itemprop='name']")
        txts = [text_clean(c.text_content() or "") for c in crumbs.all()]
        if len(txts) >= 2:
            meta["国"] = txts[1]
        if len(txts) >= 3:
            meta["リーグ"] = txts[2]
    except Exception as e:
        print(f"⚠️ パンくず取得失敗: {e}")

    # -------------------------
    # 🔹 試合情報ブロック（レフェリー・開催地・収容人数など）
    # -------------------------
    try:
        info_container = pg.locator("div[data-testid='wcl-summaryMatchInformation']")
        if not info_container.count():
            print("⚠️ 試合情報ブロックが見つかりません")
        else:
            label_elems = info_container.locator("span[data-testid='wcl-scores-overline-02']")
            print(f"ℹ️ 試合情報項目数: {label_elems.count()}")
            for i in range(label_elems.count()):
                try:
                    label = text_clean(label_elems.nth(i).text_content() or "")
                    label = label.replace(":", "").replace("：", "").strip()

                    # ラベルの親 div（infoLabelWrapper）から次の sibling div(infoValue) を取得
                    val_div = label_elems.nth(i).locator(
                        "xpath=ancestor::div[contains(@class,'wcl-infoLabelWrapper')]/following-sibling::div[1]"
                    ).first

                    vals = val_div.locator("[data-testid='wcl-scores-simple-text-01']")
                    txts = [text_clean(v.text_content()) for v in vals.all()]
                    value = " ".join([t for t in txts if t])
                    if label and value:
                        print(f"📋 {label}: {value}")
                        meta[label] = value
                except Exception as e:
                    print(f"⚠️ 項目処理失敗: {e}")
                    continue
    except Exception as e:
        print(f"⚠️ 試合情報ブロック取得失敗: {e}")

    # -------------------------
    # 🔹 試合時間（ライブ・終了など）
    # -------------------------
    try:
        meta["試合時間"] = get_match_time_text(pg)
    except:
        meta["試合時間"] = ""

    meta["取得時刻"] = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")

    # --- 元のページに戻る（必要なら）
    if "/summary/stats" in cur_url:
        try:
            pg.goto(cur_url, timeout=30000, wait_until="domcontentloaded")
        except:
            pass

    return meta

def extract_mid(s: str) -> Optional[str]:
    if not s:
        return None
    s = str(s).strip()
    m = re.search(r"[?&#]mid=([A-Za-z0-9]+)", s)
    if m: return m.group(1)
    m = re.search(r"/match/([A-Za-z0-9]{6,20})(?:/|$)", s)
    if m: return m.group(1)
    return None

def parse_live_time_to_seconds(tstr: str) -> int:
    """「90+3」「前半45」「後半12」「終了済」などを秒に変換"""
    if not tstr:
        return 0
    t = tstr.strip()
    if "終了" in t or "FT" in t.upper():
        return 5400  # 通常90分=5400秒
    if "前半" in t:
        num = re.sub(r"[^0-9]", "", t)
        return int(num) * 60 if num else 0
    if "後半" in t:
        num = re.sub(r"[^0-9]", "", t)
        return 2700 + int(num) * 60 if num else 2700
    if re.match(r"^\d+\+\d+$", t):  # 90+3 など
        a, b = t.split("+")
        return (int(a) + int(b)) * 60
    if t.isdigit():
        return int(t) * 60
    return 0

def _norm_team_name(s: str) -> str:
    """照合用にチーム名を正規化（空白と中点を除去、全角半角はそのままでも大抵OK）"""
    s = text_clean(s or "")
    s = s.replace("・", "")  # 例: 横浜F・マリノス → 横浜Fマリノス
    s = re.sub(r"\s+", "", s)
    return s

def goto_standings_overall(pg):
    """
    現在の試合URLから standings/standings/overall/ に遷移する。
    midクエリは維持。/summary/... など別タブでもOK。
    """
    cur = pg.url
    parts = list(urlsplit(cur))
    # path を /.../standings/standings/overall/ に強制
    # 例: /match/soccer/xxx/yyyy/ → /match/soccer/xxx/yyyy/standings/standings/overall/
    if not parts[2].endswith("/"):
        parts[2] += "/"
    base = re.sub(r"/(summary|h2h|lineups|odds|commentary|stats|standings)(/.*)?$", "/", parts[2])
    parts[2] = base + "standings/standings/overall/"
    url = urlunsplit(parts)

    pg.goto(url, timeout=45000, wait_until="domcontentloaded")
    # テーブル描画待ち
    try:
        pg.wait_for_selector(".ui-table__body .ui-table__row", timeout=15000)
    except:
        pass

def build_standings_url(match_url: str) -> str:
    """
    Flashscore試合URLから順位表URLを構築。
    正規形式:
      /match/soccer/<teamA>/ <teamB>/ → /match/soccer/<teamA>/<teamB>/standings/live-standings/?mid=...
    """
    # midパラメータを保持
    mid_part = ""
    if "?" in match_url:
        base, mid_part = match_url.split("?", 1)
    else:
        base = match_url

    # 末尾スラッシュを補う
    if not base.endswith("/"):
        base += "/"

    # 試合部分の抽出
    # 例: /match/soccer/kakamega-homeboyz-fc-OfdPtDuK/ulinzi-stars-KzxaGb9r/
    m = re.match(r"^(.*/match/soccer/[^/]+/[^/]+/)", base)
    if not m:
        # fallback: 試合ページ部分だけでも処理
        m = re.match(r"^(.*/match/soccer/[^/]+/)", base)
    if not m:
        return match_url  # 安全策

    prefix = m.group(1)
    # 正しい standings パスを構築
    standings_url = prefix + "standings/live-standings/"
    if mid_part:
        standings_url += "?" + mid_part
    return standings_url

def goto_standings_page(pg):
    """順位ページに遷移。live-standings優先、失敗したらoverall"""
    url1 = build_standings_url(pg.url)
    url2 = url1.replace("live-standings", "standings/standings/overall")

    try:
        pg.goto(url1, timeout=45000, wait_until="domcontentloaded")
        pg.wait_for_selector(".ui-table__body .ui-table__row", timeout=8000)
        return url1
    except:
        # fallback
        try:
            pg.goto(url2, timeout=45000, wait_until="domcontentloaded")
            pg.wait_for_selector(".ui-table__body .ui-table__row", timeout=8000)
            return url2
        except:
            return None

def goto_standings_overall(pg):
    url = build_standings_overall_url(pg.url)
    pg.goto(url, timeout=45000, wait_until="domcontentloaded")
    # テーブル描画待ち
    try:
        pg.wait_for_selector(".ui-table__body .ui-table__row", timeout=15000)
    except:
        pass

def parse_standings_table(pg):
    rows = pg.locator(".ui-table__body .ui-table__row")
    n = rows.count()
    out = []
    for i in range(n):
        r = rows.nth(i)
        rank_txt = text_clean(r.locator(".table__cell--rank .tableCellRank").first.text_content() or "")
        # 末尾の「.」除去
        try:
            rank = int(rank_txt.strip().rstrip("."))
        except:
            rank = None
        team = text_clean(r.locator(".table__cell--participant .tableCellParticipant__name").first.text_content() or "")
        vals = [text_clean(x.text_content() or "") for x in r.locator("span.table__cell--value").all()]
        # pts は配列の最後（勝点）
        pts = None
        if vals:
            try: pts = int(vals[-1])
            except: pass
        selected = "table__row--selected" in (r.get_attribute("class") or "")
        out.append({"rank": rank, "team": team, "pts": pts, "selected": selected})
    return out

def _norm_team_name(s: str) -> str:
    s = text_clean(s or "")
    s = s.replace("・", "")
    s = re.sub(r"\s+", "", s)
    return s

def get_match_standings(pg, home_name: str, away_name: str):
    """
    両チームの順位・勝点を抽出。
    live-standings 優先、なければ overall から取得。
    """
    url = goto_standings_page(pg)
    if not url:
        return {}

    rows = pg.locator(".ui-table__body .ui-table__row")
    if not rows.count():
        return {}

    out = {}
    for i in range(rows.count()):
        r = rows.nth(i)
        rank_txt = text_clean(r.locator(".table__cell--rank .tableCellRank").first.text_content() or "")
        team_name = text_clean(r.locator(".table__cell--participant .tableCellParticipant__name").first.text_content() or "")
        pts_txt = text_clean(r.locator(".table__cell--value").last.text_content() or "")

        try: rank = int(rank_txt.strip().rstrip("."))
        except: rank = None
        try: pts = int(pts_txt)
        except: pts = None

        out[team_name] = {"rank": rank, "pts": pts}

    h, a = text_clean(home_name), text_clean(away_name)
    home = next((v for k, v in out.items() if h in k or k in h), None)
    away = next((v for k, v in out.items() if a in k or k in a), None)

    return {
        "url": url,
        "home_rank": home["rank"] if home else None,
        "home_pts": home["pts"] if home else None,
        "away_rank": away["rank"] if away else None,
        "away_pts": away["pts"] if away else None,
    }

def get_match_time_text(pg) -> str:
    """
    ライブ経過(例: 88:03) も 終了表示(終了/終了済/試合終了/FT) も拾う。
    可視要素優先、十分に待機、複数候補を順にチェック。
    """
    # 1) 最初にステータスコンテナの可視化を待つ（どれかが出ればOK）
    candidates_any = [
        "div.detailScore__status",                              # 詳細スコアのステータス枠
        ".fixedHeaderDuel:not(.fixedHeaderDuel--isHidden)",     # 可視の固定ヘッダー
        "[data-testid='wcl-time']"                              # 時刻/終了テキスト（複数箇所に出る）
    ]
    found = False
    for sel in candidates_any:
        try:
            pg.wait_for_selector(f"{sel} >> visible=true", timeout=5000)
            found = True
            break
        except:
            pass
    # 2) 明示的に「終了系」テキストを探す（可視のみ）
    end_sels = [
        # 例: <span class="fixedHeaderDuel__detailStatus">試合終了</span>
        ".fixedHeaderDuel:not(.fixedHeaderDuel--isHidden) .fixedHeaderDuel__detailStatus",
        "div.detailScore__status .fixedHeaderDuel__detailStatus",
        # 例: data-testid="wcl-time" に「終了」等が入ることも多い
        "[data-testid='wcl-time']"
    ]
    for s in end_sels:
        try:
            el = pg.locator(f"{s} >> visible=true").first
            if el.count():
                txt = (el.text_content() or "").strip().replace("\u00A0", " ")
                if re.search(r"(終了|試合終了|FT)", txt, re.I):
                    return txt
        except:
            pass

    # 3) ライブ経過「mm:ss / 90+3 / 45'」など（可視の eventTime）
    live_sels = [
        "div.detailScore__status .eventAndAddedTime .eventTime",
        ".fixedHeaderDuel:not(.fixedHeaderDuel--isHidden) .eventAndAddedTime .eventTime",
        "[data-testid='wcl-time']"  # ここに 88' などが入るケースも
    ]
    for s in live_sels:
        try:
            el = pg.locator(f"{s} >> visible=true").first
            if el.count():
                txt = (el.text_content() or "").strip().replace("\u00A0", " ")
                # 88:03 / 90+3 / 45' / 45+2' などを許容
                if re.search(r"^\d{1,3}(:\d{2})?$", txt) or re.search(r"^\d{1,3}(\+\d{1,2})?('|’)?$", txt):
                    return txt
        except:
            pass

    # 4) ダメ押し：可視の detail status をそのまま返す（「前半」「後半」だけでも）
    for s in end_sels:
        try:
            el = pg.locator(f"{s} >> visible=true").first
            if el.count():
                txt = (el.text_content() or "").strip().replace("\u00A0", " ")
                if txt:
                    return txt
        except:
            pass

    # 5) 最後のフォールバック（不可視も含めて拾ってみる）
    try:
        el = pg.locator("[data-testid='wcl-time']").first
        if el.count():
            return (el.text_content() or "").strip()
    except:
        pass

    return ""

def get_match_teams_ranks(pg, home_name: str, away_name: str):
    """
    返り値:
      {
        "home_rank": int|None, "away_rank": int|None,
        "home_pts": int|None,  "away_pts": int|None,
        "table_rows": int
      }
    """
    goto_standings_overall(pg)
    table = parse_standings_table(pg)

    # 1) 選択行優先（通常2行 = 対象2チーム）
    selected_rows = [r for r in table if r["selected"]]
    home_rank = away_rank = home_pts = away_pts = None

    if len(selected_rows) >= 2:
        # 名前照合で home/away を割当（同点・別名対策でnormalize）
        hkey, akey = _norm_team_name(home_name), _norm_team_name(away_name)
        for r in selected_rows[:2]:
            tkey = _norm_team_name(r["team"])
            if tkey == hkey and home_rank is None:
                home_rank, home_pts = r["rank"], r["pts"]
            elif tkey == akey and away_rank is None:
                away_rank, away_pts = r["rank"], r["pts"]
        # 片方しか割り当てられなかった場合は残りをもう一方へ
        if (home_rank is None or away_rank is None):
            # 順不同で充当
            if home_rank is None:
                home_rank, home_pts = selected_rows[0]["rank"], selected_rows[0]["pts"]
            if away_rank is None:
                away_rank, away_pts = selected_rows[1]["rank"], selected_rows[1]["pts"]

    else:
        # 2) 選択行が1つ or 無い → 名前照合のみ
        hkey, akey = _norm_team_name(home_name), _norm_team_name(away_name)
        for r in table:
            tkey = _norm_team_name(r["team"])
            if tkey == hkey and home_rank is None:
                home_rank, home_pts = r["rank"], r["pts"]
            if tkey == akey and away_rank is None:
                away_rank, away_pts = r["rank"], r["pts"]

    return {
        "home_rank": home_rank, "away_rank": away_rank,
        "home_pts": home_pts,   "away_pts": away_pts,
        "table_rows": len(table),
        "standings_url": build_standings_overall_url(pg.url)  # デバッグ用
    }

def get_match_teams_ranks(pg, home_name: str, away_name: str):
    """
    その試合のホーム/アウェーに対応する順位を返す。
    返り値: {"home_rank": int|None, "away_rank": int|None, "home_pts": int|None, "away_pts": int|None}
    """
    goto_standings_overall(pg)
    table = parse_standings_table(pg)

    hkey = _norm_team_name(home_name)
    akey = _norm_team_name(away_name)

    home_rank = away_rank = home_pts = away_pts = None

    for row in table:
        tkey = _norm_team_name(row["team"])
        if tkey == hkey and home_rank is None:
            home_rank, home_pts = row["rank"], row["pts"]
        if tkey == akey and away_rank is None:
            away_rank, away_pts = row["rank"], row["pts"]

    # 片方だけマッチした場合のフォールバック（選択行に頼る）
    if (home_rank is None or away_rank is None):
        sel = [r for r in table if r["selected"]]
        # 通常、選択行は2行（両チーム）になることが多い
        if len(sel) >= 1:
            # team名の強制一致ができないケースの保険として採用
            if home_rank is None:
                home_rank = sel[0]["rank"] if len(sel) >= 1 else home_rank
                home_pts  = sel[0]["pts"]  if len(sel) >= 1 else home_pts
            if away_rank is None and len(sel) >= 2:
                away_rank = sel[1]["rank"]
                away_pts  = sel[1]["pts"]

    return {
        "home_rank": home_rank,
        "away_rank": away_rank,
        "home_pts": home_pts,
        "away_pts": away_pts,
        "table_rows": len(table)
    }

def build_standings_overall_url(match_url: str) -> str:
    """
    任意の試合URLから /standings/standings/overall/ へ飛ばすURLを作る。
    例:
      https://www.flashscore.co.jp/match/soccer/c-osaka-XXXX/kawasaki-YYYY/?mid=AwEGROFt
      → https://www.flashscore.co.jp/match/soccer/c-osaka-XXXX/kawasaki-YYYY/standings/standings/overall/?mid=AwEGROFt
    """
    parts = list(urlsplit(match_url))
    path = parts[2]
    # /match/.../ ← 試合の「ペア」までを抽出
    m = re.match(r"^(/match/[^/]+/[^/]+/)", path)
    base = m.group(1) if m else (path if path.endswith("/") else path + "/")
    parts[2] = base + "standings/live-standings/"
    return urlunsplit(parts)

def debug_dump_stats(stats_pairs: dict):
    """取得した stats_pairs（ネスト）を見やすくダンプ"""
    if not stats_pairs:
        log("   [STATS] 取得ゼロ")
        return
    n_labels = 0
    for section, sub in stats_pairs.items():
        if isinstance(sub, dict):
            log(f"   [STATS] §{section}")
            for label, pair in sub.items():
                n_labels += 1
                if isinstance(pair, (list, tuple)) and len(pair) == 2:
                    log(f"      - {label}: {pair[0]} | {pair[1]}")
                else:
                    log(f"      - {label}: (unexpected format) {pair}")
        else:
            # 平坦フォーマット（念のため）
            n_labels += 1
            log(f"   [STATS] {section}: {sub}")
    log(f"   [STATS] 合計ラベル数: {n_labels}")

def debug_filled_columns(d: dict):
    """今回の1試合分 d で、非空の列を表示（どの列が埋まったか）"""
    filled = [k for k in HEADER if str(d.get(k, "")).strip() != ""]
    log(f"   [ROW] 非空列: {len(filled)} / {len(HEADER)}")
    log("   [ROW] 非空列の例: " + ", ".join(filled[:12]) + (" ..." if len(filled) > 12 else ""))

# ================= 統計データ → HEADER統合 =================
def stats_to_header_dict(stats_pairs: dict) -> dict:
    """
    scrape_stats_pairs の結果（ネスト構造）を HEADER キー対応に展開。
    """
    out = {k: "" for k in HEADER}
    for section, subdict in stats_pairs.items():
        # section が dict の場合（通常）
        if isinstance(subdict, dict):
            for label, pair in subdict.items():
                if not isinstance(pair, (list, tuple)) or len(pair) != 2:
                    continue
                key = f"{section}:{label}"
                if key in STAT_KEY_MAP:
                    hcol, acol = STAT_KEY_MAP[key]
                    out[hcol] = pair[0]
                    out[acol] = pair[1]
        # 旧フォーマット（平坦）
        else:
            if not isinstance(subdict, (list, tuple)) or len(subdict) != 2:
                continue
            if section in STAT_KEY_MAP:
                hcol, acol = STAT_KEY_MAP[section]
                out[hcol] = subdict[0]
                out[acol] = subdict[1]
    return out

# ===== Excel 逐次書き込みユーティリティ =====
from pathlib import Path
try:
    import openpyxl
except ImportError:
    raise RuntimeError("openpyxl が必要です。`pip install openpyxl` を実行してください。")

# 1ファイルあたりの最大データ行数（ヘッダー除く）
MAX_ROWS_PER_FILE = 10
SHEET_NAME = "Sheet1"
FILE_PREFIX = "output_"
FILE_SUFFIX = ".xlsx"

def _existing_serials(output_dir: str) -> List[int]:
    p = Path(output_dir)
    nums = []
    for f in p.glob(f"{FILE_PREFIX}*{FILE_SUFFIX}"):
        m = re.match(rf"^{re.escape(FILE_PREFIX)}(\d+){re.escape(FILE_SUFFIX)}$", f.name)
        if m:
            nums.append(int(m.group(1)))
    return sorted(nums)

def _next_serial(output_dir: str) -> int:
    """既存の最大連番+1 を返す（※『最小連番+1』の表記は多義的なので最大+1を採用）"""
    nums = _existing_serials(output_dir)
    return (max(nums) + 1) if nums else 1

def _current_file_path(output_dir: str) -> Path:
    """今使うべきファイルパスを返す。無ければ新規（連番）"""
    p = Path(output_dir)
    nums = _existing_serials(output_dir)
    if not nums:
        return p / f"{FILE_PREFIX}{_next_serial(output_dir)}{FILE_SUFFIX}"
    # 直近のファイル（最大連番）
    return p / f"{FILE_PREFIX}{max(nums)}{FILE_SUFFIX}"

def _data_rows_in(path: Path) -> int:
    """既存Excelのデータ行数（ヘッダ除く）を返す。無ければ0。"""
    if not path.exists():
        return 0
    wb = openpyxl.load_workbook(path, read_only=True, data_only=True)
    ws = wb[SHEET_NAME] if SHEET_NAME in wb.sheetnames else wb.active
    # ヘッダ1行を除外
    total = ws.max_row or 0
    wb.close()
    return max(0, total - 1)

def _create_new_workbook(path: Path):
    """ヘッダー付きで新規作成"""
    df = pd.DataFrame(columns=HEADER)
    path.parent.mkdir(parents=True, exist_ok=True)
    with pd.ExcelWriter(path, engine="openpyxl") as w:
        df.to_excel(w, index=False, sheet_name=SHEET_NAME)

def append_row_to_excel(row_dict: dict, output_dir: str, max_rows_per_file: int = MAX_ROWS_PER_FILE):
    output_dir = Path(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    cur = _current_file_path(output_dir)
    if not cur.exists():
        _create_new_workbook(cur)

    # ここで必ず現在のデータ行数を取得しておく
    current_rows = _data_rows_in(cur)

    # 上限チェック（※上限超なら新規ファイルを作り、current_rows を 0 にリセット）
    if current_rows >= max_rows_per_file:
        cur = output_dir / f"{FILE_PREFIX}{_next_serial(output_dir)}{FILE_SUFFIX}"
        _create_new_workbook(cur)
        current_rows = 0

    # 追記用DFをHEADER順に整形
    df = pd.DataFrame([row_dict])
    for col in HEADER:
        if col not in df.columns:
            df[col] = ""
    df = df[HEADER]

    with pd.ExcelWriter(cur, engine="openpyxl", mode="a", if_sheet_exists="overlay") as w:
        startrow = current_rows + 1  # ヘッダ1行あり
        df.to_excel(w, index=False, header=False, sheet_name=SHEET_NAME, startrow=startrow)

    print(f"💾 追記完了: {cur.name} （データ行 {current_rows} → {current_rows+1} 件目を追加）")

if __name__ == "__main__":
    main()
