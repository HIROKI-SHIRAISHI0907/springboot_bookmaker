# -*- coding: utf-8 -*-
"""
Flashscore 終了済（RESULTS）試合 -> midリンク突合 -> (stats / summary meta / standings) を取得し、
1試合=1行のCSVを S3 に逐次保存する全処理版（方式A）

✅ 方式A（推奨）
- S3は追記できないので「1行=1オブジェクト」で保存
- 失敗しても他行に影響なし
- SEQMAP（試合IDごとの通番）もS3に保存して永続化（ECSでも連番が続く）

✅ 重要仕様（あなたのライブ版を踏襲）
- stats の値は「34%（31/90）」など“そのまま”取得（加工しない）
- "セクション:ラベル" -> canonicalキー -> STAT_KEY_MAP -> HEADER列へ投入
- VERIFYログ（マッピング一致/未一致、row埋まり状況）あり

✅ 変更点（ライブ版 → 終了済版）
- データソースをライブから終了済（RESULTS）へ変更
- 終了済試合ページを開いた後、ページ内の <a href*="mid="> を走査し
  S3 JSONの matchId(mid) に一致するリンクのみ「直接開く」
- 一致しない試合はスキップ

想定
- ECS(Fargate) で実行
- Playwright(Chromium) はコンテナ内
- タスクロールで S3 PutObject/GetObject 権限
"""

from playwright.sync_api import sync_playwright, TimeoutError as PWTimeout
import time
import re
import os
import pickle
import datetime
import traceback
from urllib.parse import urlsplit, urlunsplit, urlparse, parse_qs
from typing import Optional, List, Dict, Tuple, Any
import multiprocessing as mp
import queue as pyqueue
import io
import csv
import json

import boto3
from botocore.exceptions import ClientError


# =========================
# Env helpers (ECS envは全部文字列)
# =========================
def env_bool(name: str, default: bool = True) -> bool:
    v = os.environ.get(name)
    if v is None:
        return default
    return v.strip().lower() in ("1", "true", "t", "yes", "y", "on")

def env_int(name: str, default: int = 0) -> int:
    v = os.environ.get(name)
    if v is None or v.strip() == "":
        return default
    try:
        return int(float(v.strip()))
    except ValueError:
        return default

def env_float(name: str, default: float = 0.0) -> float:
    v = os.environ.get(name)
    if v is None or v.strip() == "":
        return default
    try:
        return float(v.strip())
    except ValueError:
        return default

def env_str(name: str, default: str = "") -> str:
    v = os.environ.get(name)
    return default if v is None else v


# =========================
# Settings
# =========================
FLASHCORE_URL = "https://www.flashscore.co.jp/"
TIMEZONE_ID   = "Asia/Tokyo"
LOCALE        = "ja-JP"
USER_AGENT    = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

HEADLESS      = env_bool("HEADLESS", False)
SLOW_MO_MS    = env_float("SLOW_MO_MS", 0.0)

WORKER_TIMEOUT_SEC = env_int("WORKER_TIMEOUT_SEC", 220)  # 子プロセス全体（1試合）
NAV_TIMEOUT_MS     = env_int("NAV_TIMEOUT_MS", 25000)    # safe_goto 1st try
WAIT_TIMEOUT_MS    = env_int("WAIT_TIMEOUT_MS", 12000)   # wait_for_selector
MAX_DAY_STEPS = env_int("MAX_DAY_STEPS", 120)  # prev/next クリック最大回数

VERBOSE = env_bool("VERBOSE", True)

BOT_WALL_PAT = r"Just a moment|Access Denied|verify you are human|チェック|確認"

# S3 settings（方式A）
S3_BUCKET_OUTPUTS = "aws-s3-outputs-csv"
S3_REGION         = "ap-northeast-1"
S3_PREFIX         = ""  # 例: "outputs/"。バケット直下なら空。

# SEQMAP（S3にpickle保存）
SEQMAP_S3_KEY     = "seqmap/seqmap.pkl"

# 1行CSVにヘッダーを含めるか（1オブジェクト=1行運用なら True が便利）
INCLUDE_HEADER_IN_EACH_ROW = env_bool("INCLUDE_HEADER_IN_EACH_ROW", True)

# mp start method（Linuxでspawnにしたい場合のみ）
FORCE_SPAWN = env_bool("FORCE_SPAWN", True)

# RESULTS一覧で「もっと表示」押下回数（対象midが多いなら増やす）
MAX_LOAD_MORE_CLICKS = env_int("MAX_LOAD_MORE_CLICKS", 25)

# JSONにあるmidを全部見つけたら列挙を打ち切る（親側で早期終了）
STOP_WHEN_ALL_MIDS_FOUND = env_bool("STOP_WHEN_ALL_MIDS_FOUND", True)

def log(msg: str):
    if VERBOSE:
        print(msg, flush=True)


# =========================
# HEADER（CSV列）
# =========================
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


# =========================
# 統計キー -> HEADER列対応（canonicalキー前提）
# =========================
STAT_KEY_MAP = {
    "アタック:期待値（xG）": ("ホーム期待値", "アウェー期待値"),
    "アタック:枠内ゴール期待値": ("ホーム枠内ゴール期待値", "アウェー枠内ゴール期待値"),
    "ポゼッション:ボール支配率": ("ホームボール支配率", "アウェーボール支配率"),
    "シュート:シュート数": ("ホームシュート数", "アウェーシュート数"),
    "シュート:枠内シュート数": ("ホーム枠内シュート数", "アウェー枠内シュート数"),
    "シュート:枠外シュート数": ("ホーム枠外シュート数", "アウェー枠外シュート数"),
    "シュート:ブロックシュート": ("ホームブロックシュート", "アウェーブロックシュート"),
    "アタック:ビッグチャンス": ("ホームビッグチャンス", "アウェービッグチャンス"),
    "セットプレー:コーナーキック": ("ホームコーナーキック", "アウェーコーナーキック"),
    "シュート:ボックス内シュート": ("ホームボックス内シュート", "アウェーボックス内シュート"),  # ※元コードに合わせるならここは要確認
    "シュート:ボックス外シュート": ("ホームボックス外シュート", "アウェーボックス外シュート"),
    "シュート:ポストヒット": ("ホームゴールポスト", "アウェーゴールポスト"),
    "シュート:ヘディングゴール": ("ホームヘディングゴール", "アウェーヘディングゴール"),

    "ディフェンス:キーパーセーブ": ("ホームキーパーセーブ", "アウェーキーパーセーブ"),
    "ディフェンス:フリーキック": ("ホームフリーキック", "アウェーフリーキック"),
    "ディフェンス:オフサイド": ("ホームオフサイド", "アウェーオフサイド"),
    "ディフェンス:ファウル": ("ホームファウル", "アウェーファウル"),
    "カード:イエローカード": ("ホームイエローカード", "アウェーイエローカード"),
    "カード:レッドカード": ("ホームレッドカード", "アウェーレッドカード"),
    "ディフェンス:スローイン": ("ホームスローイン", "アウェースローイン"),

    "パス:相手ボックスタッチ": ("ホーム相手ボックスタッチ", "アウェー相手ボックスタッチ"),
    "パス:総パス数": ("ホームパス", "アウェーパス"),
    "パス:ロングパス": ("ホームロングパス", "アウェーロングパス"),
    "パス:ファイナルサードパス": ("ホームファイナルサードパス", "アウェーファイナルサードパス"),
    "パス:クロス": ("ホームクロス", "アウェークロス"),

    "ディフェンス:タックル": ("ホームタックル", "アウェータックル"),
    "ディフェンス:クリア": ("ホームクリア", "アウェークリア"),
    "ディフェンス:デュエル勝利": ("ホームデュエル勝利数", "アウェーデュエル勝利数"),
    "ディフェンス:インターセプト": ("ホームインターセプト", "アウェーインターセプト"),
}

LABEL_TO_CANON: Dict[str, str] = {
    "ゴール期待値（xG）": "アタック:期待値（xG）",
    "枠内ゴール期待値（xGOT）": "アタック:枠内ゴール期待値",

    "ボール支配率": "ポゼッション:ボール支配率",
    "合計シュート": "シュート:シュート数",
    "枠内シュート": "シュート:枠内シュート数",
    "枠外シュート": "シュート:枠外シュート数",
    "シュートブロック": "シュート:ブロックシュート",
    "ボックス内からのシュート": "シュート:ボックス内シュート",
    "ボックス外からのシュート": "シュート:ボックス外シュート",
    "ゴール枠に当たる": "シュート:ポストヒット",
    "ゴール枠に当たるシュート": "シュート:ポストヒット",

    "ビッグチャンス": "アタック:ビッグチャンス",
    "コーナーキック": "セットプレー:コーナーキック",

    "イエローカード": "カード:イエローカード",
    "レッドカード": "カード:レッドカード",
    "ファウル": "ディフェンス:ファウル",
    "オフサイド": "ディフェンス:オフサイド",
    "フリーキック": "ディフェンス:フリーキック",
    "スローイン": "ディフェンス:スローイン",
    "タックル": "ディフェンス:タックル",
    "デュエル勝利数": "ディフェンス:デュエル勝利",
    "クリアリング": "ディフェンス:クリア",
    "クリア": "ディフェンス:クリア",
    "インターセプト": "ディフェンス:インターセプト",
    "キーパーセーブ": "ディフェンス:キーパーセーブ",

    "相手ボックス内でのタッチ": "パス:相手ボックスタッチ",
    "相手ボックス内タッチ": "パス:相手ボックスタッチ",
    "パス": "パス:総パス数",
    "ロングパス": "パス:ロングパス",
    "ファイナルサードでのパス": "パス:ファイナルサードパス",
    "ファイナルサードのパス": "パス:ファイナルサードパス",
    "クロス": "パス:クロス",
}

SECTION_PREFER = ["主なスタッツ", "シュート", "アタック", "パス", "ディフェンス", "ゴールキーパー"]
def _section_rank(section: str) -> int:
    try:
        return SECTION_PREFER.index(section)
    except ValueError:
        return 999


# =========================
# VERIFY
# =========================
def verify_header_and_stat_map():
    bad = []
    for k, (hcol, acol) in STAT_KEY_MAP.items():
        if hcol not in HEADER:
            bad.append(("HOME", k, hcol))
        if acol not in HEADER:
            bad.append(("AWAY", k, acol))
    if bad:
        log("❌ [VERIFY] STAT_KEY_MAP が参照している列が HEADER に無い")
        for side, k, col in bad:
            log(f"   - {side} key='{k}' col='{col}'")
        raise RuntimeError("STAT_KEY_MAP column mismatch with HEADER")
    log("✅ [VERIFY] STAT_KEY_MAP の列名は HEADER と整合しています")

def verify_stats_mapping(keys_title: str, stats_pairs: Dict[str, Tuple[str, str]]):
    scraped_keys = set(stats_pairs.keys())
    mapped_keys  = set(STAT_KEY_MAP.keys())
    direct_hit   = sorted(scraped_keys & mapped_keys)
    miss_scraped = sorted(scraped_keys - mapped_keys)

    log(f"✅ [VERIFY:{keys_title}] 直接一致キー数: {len(direct_hit)}")
    for k in direct_hit[:40]:
        hv, av = stats_pairs.get(k, ("",""))
        log(f"   ✓ {k} = ({hv}, {av})")

    log(f"⚠️ [VERIFY:{keys_title}] マップに無いスクレイプキー数: {len(miss_scraped)}")
    for k in miss_scraped[:60]:
        hv, av = stats_pairs.get(k, ("",""))
        log(f"   ? {k} = ({hv}, {av})")

def verify_row_filled(row: Dict[str, Any], limit: int = 120):
    filled = []
    for col in HEADER:
        v = row.get(col, "")
        if v not in ("", None):
            filled.append((col, v))
    log(f"✅ [VERIFY] row に値が入った列数: {len(filled)}")
    for col, v in filled[:limit]:
        log(f"   • {col} = {v}")


# =========================
# URL builders / helpers
# =========================
_MATCH_ROOT_RE = re.compile(r"^(/match/[^/]+/[^/]+/[^/]+/)")

def text_clean(s: str) -> str:
    return re.sub(r"\s+", " ", (s or "")).strip()

def extract_mid(any_url: str) -> Optional[str]:
    if not any_url:
        return None
    qs = parse_qs(urlparse(any_url).query)
    return qs.get("mid", [None])[0]

def _match_root_from_any_url(any_url: str) -> Tuple[Tuple[str, str, str], str]:
    parts = urlsplit(any_url)
    path = parts.path or ""
    m = _MATCH_ROOT_RE.search(path)
    if not m:
        raise ValueError(f"match root not found: {any_url}")
    match_root_path = m.group(1)
    mid = extract_mid(any_url) or ""
    return (parts.scheme, parts.netloc, match_root_path), mid

def build_stats_url(any_url: str) -> str:
    (scheme, netloc, root), mid = _match_root_from_any_url(any_url)
    path = root + "summary/stats/overall/"
    query = f"mid={mid}" if mid else ""
    return urlunsplit((scheme, netloc, path, query, ""))

def build_summary_url(any_url: str) -> str:
    (scheme, netloc, root), mid = _match_root_from_any_url(any_url)
    path = root + "summary/"
    query = f"mid={mid}" if mid else ""
    return urlunsplit((scheme, netloc, path, query, ""))

def build_live_standings_url(any_url: str) -> str:
    (scheme, netloc, root), mid = _match_root_from_any_url(any_url)
    path = root + "standings/live-standings/"
    query = f"mid={mid}" if mid else ""
    return urlunsplit((scheme, netloc, path, query, ""))

def build_overall_standings_url(any_url: str) -> str:
    (scheme, netloc, root), mid = _match_root_from_any_url(any_url)
    path = root + "standings/standings/overall/"
    query = f"mid={mid}" if mid else ""
    return urlunsplit((scheme, netloc, path, query, ""))


# =========================
# STOP対策：safe_goto
# =========================
def safe_goto(pg, url: str, timeout_ms: int = NAV_TIMEOUT_MS, tag: str = "") -> bool:
    ttag = f" {tag}" if tag else ""
    log(f"🧭 [GOTO]{ttag} try1 commit: {url}")
    try:
        pg.goto(url, timeout=timeout_ms, wait_until="commit")
        return True
    except PWTimeout as e:
        log(f"⏱️ [GOTO]{ttag} try1 timeout: {e}")
    except Exception as e:
        log(f"⚠️ [GOTO]{ttag} try1 error: {type(e).__name__}: {e}")

    try:
        log(f"🛑 [GOTO]{ttag} window.stop()")
        pg.evaluate("() => window.stop()")
    except Exception as e:
        log(f"⚠️ [GOTO]{ttag} window.stop error: {type(e).__name__}: {e}")

    log(f"🧭 [GOTO]{ttag} try2 domcontentloaded: {url}")
    try:
        pg.goto(url, timeout=8000, wait_until="domcontentloaded")
        return True
    except PWTimeout as e:
        log(f"⏱️ [GOTO]{ttag} try2 timeout: {e}")
    except Exception as e:
        log(f"⚠️ [GOTO]{ttag} try2 error: {type(e).__name__}: {e}")

    try:
        log(f"🧼 [GOTO]{ttag} about:blank")
        pg.goto("about:blank", timeout=3000, wait_until="commit")
    except Exception:
        pass

    log(f"🧭 [GOTO]{ttag} try3 domcontentloaded(after blank): {url}")
    try:
        pg.goto(url, timeout=8000, wait_until="domcontentloaded")
        return True
    except Exception as e:
        log(f"❌ [GOTO]{ttag} try3 failed: {type(e).__name__}: {e}")
        return False

def _get_breadcrumb_country_league(pg) -> Tuple[str, str, List[str]]:
    """
    新UIのパンくず:
      1: サッカー
      2: 日本
      3: J2・J3 リーグ - ラウンド 1
    を確実に取る
    """
    # 3つ揃うまで待つ（重要）
    try:
        pg.wait_for_function("""
        () => {
          const xs = Array.from(
            document.querySelectorAll("li[data-testid='wcl-breadcrumbsItem'] [itemprop='name']")
          ).map(e => (e.textContent || '').replace(/\\s+/g,' ').trim()).filter(Boolean);
          return xs.length >= 3;
        }
        """, timeout=WAIT_TIMEOUT_MS)
    except Exception:
        pass

    # JSで一括抽出（locator ループより安定）
    crumbs = []
    try:
        crumbs = pg.evaluate("""
        () => Array.from(
          document.querySelectorAll("li[data-testid='wcl-breadcrumbsItem'] [itemprop='name']")
        ).map(e => (e.textContent || '').replace(/\\s+/g,' ').trim()).filter(Boolean)
        """) or []
    except Exception:
        crumbs = []

    # 想定: ["サッカー", "日本", "J2・J3 リーグ - ラウンド 1"]
    country = crumbs[1] if len(crumbs) >= 2 else ""
    league  = crumbs[2] if len(crumbs) >= 3 else ""

    return text_clean(country), text_clean(league), crumbs


# =========================
# consent / bot wall
# =========================
def kill_onetrust(page):
    try:
        btn = page.locator("#onetrust-accept-btn-handler")
        if btn.count():
            btn.click(timeout=1200, force=True)
    except:
        pass
    try:
        page.evaluate("""
        () => {
          const ids = ["onetrust-consent-sdk", "onetrust-banner-sdk"];
          ids.forEach(id => document.getElementById(id)?.remove());
          document.querySelectorAll(".ot-sdk-container, .ot-sdk-row, .otOverlay, .ot-pc-footer, .ot-pc-header")
            .forEach(el => el.remove());
        }""", timeout=1200)
    except:
        pass

def kill_consent_banners(page):
    kill_onetrust(page)
    candidates = [
        "button:has-text('すべて拒否する')",
        "button:has-text('全て拒否する')",
        "button:has-text('拒否する')",
        "button:has-text('同意します')",
        "button:has-text('同意する')",
        "button:has-text('Reject all')",
        "button:has-text('Accept all')",
        "[role='button']:has-text('すべて拒否する')",
        "[role='button']:has-text('同意します')",
    ]
    for sel in candidates:
        try:
            b = page.locator(sel).first
            if b.count() and b.is_visible(timeout=500):
                b.click(timeout=1200, force=True)
                break
        except:
            pass

    try:
        page.evaluate("""
        () => {
          const kill = [
            "#qc-cmp2-container",
            "#didomi-host",
            "#sp_message_container_",
            ".fc-consent-root",
            ".message-component",
            ".pmConsentWall"
          ];
          kill.forEach(s => document.querySelectorAll(s).forEach(el => el.remove()));
        }""", timeout=1200)
    except:
        pass

def is_bot_wall(pg) -> bool:
    try:
        return pg.locator(f"text=/{BOT_WALL_PAT}/i").first.is_visible(timeout=900)
    except:
        return False


# =========================
# route blocking（軽量化）
# =========================
def setup_route_blocking(ctx):
    def _route(route):
        try:
            rtype = route.request.resource_type
            url = (route.request.url or "").lower()
            if rtype in ("image", "media", "font"):
                return route.abort()
            if any(x in url for x in ("doubleclick", "googlesyndication", "adservice", "adsystem", "taboola", "outbrain")):
                return route.abort()
        except:
            pass
        return route.continue_()
    ctx.route("**/*", _route)


# =========================
# match page: teams/scores/time
# =========================
def get_home_away_names(pg) -> Tuple[str, str]:
    try:
        cont = pg.locator("div.duelParticipant__container").first
        if not cont.count():
            cont = pg
        h = cont.locator(".duelParticipant__home a.participant__participantName").first
        a = cont.locator(".duelParticipant__away a.participant__participantName").first
        home = text_clean(h.text_content()) if h.count() else ""
        away = text_clean(a.text_content()) if a.count() else ""
        if not home:
            img = cont.locator(".duelParticipant__home img.participant__image").first
            home = text_clean(img.get_attribute("alt") or "")
        if not away:
            img = cont.locator(".duelParticipant__away img.participant__image").first
            away = text_clean(img.get_attribute("alt") or "")
        return home, away
    except:
        return "", ""

def get_scores(pg) -> Tuple[str, str]:
    def _clean(s: str) -> str:
        return re.sub(r"\s+", "", (s or "").replace("\u00A0", " ")).strip()

    def _from_container(el):
        try:
            spans = el.locator("span")
            vals = []
            for i in range(spans.count()):
                sp = spans.nth(i)
                cls = (sp.get_attribute("class") or "")
                if "divider" in cls:
                    continue
                txt = _clean(sp.text_content() or "")
                if re.fullmatch(r"\d+", txt):
                    vals.append(txt)
            if len(vals) >= 2:
                return vals[0], vals[-1]
        except:
            pass

        try:
            t = _clean(el.inner_text() or "")
            m = re.search(r"(\d+)\s*[\-\u2212\u2012\u2013\u2014\u2015]\s*(\d+)", t)
            if m:
                return m.group(1), m.group(2)
        except:
            pass
        return "", ""

    try:
        fx = pg.locator(".fixedHeaderDuel:not(.fixedHeaderDuel--isHidden) .fixedScore").first
        if fx.count():
            h, a = _from_container(fx)
            if h and a:
                return h, a
    except:
        pass

    try:
        live = pg.locator("div.detailScore__wrapper.detailScore__live").first
        if live.count():
            h, a = _from_container(live)
            if h and a:
                return h, a
    except:
        pass

    try:
        wrap = pg.locator("div.detailScore__wrapper").first
        if wrap.count():
            h, a = _from_container(wrap)
            if h and a:
                return h, a
    except:
        pass

    return "", ""

def get_match_time_text(pg) -> str:
    def _txt(locator) -> str:
        try:
            if locator.count():
                t = (locator.first.text_content() or "").strip().replace("\u00A0", " ")
                return t
        except:
            pass
        return ""

    event_selectors = [
        ".fixedHeaderDuel:not(.fixedHeaderDuel--isHidden) .eventAndAddedTime .eventTime",
        "div.detailScore__status .eventAndAddedTime .eventTime",
        ".eventAndAddedTime .eventTime",
    ]
    for s in event_selectors:
        t = _txt(pg.locator(f"{s} >> visible=true"))
        if t:
            return t

    testid_selectors = [
        "[data-testid='wcl-time']",
    ]
    for s in testid_selectors:
        t = _txt(pg.locator(f"{s} >> visible=true"))
        if t:
            return t

    status_selectors = [
        ".fixedHeaderDuel:not(.fixedHeaderDuel--isHidden) .fixedHeaderDuel__detailStatus",
        "div.detailScore__status .fixedHeaderDuel__detailStatus",
        "div.detailScore__status",
    ]
    for s in status_selectors:
        t = _txt(pg.locator(f"{s} >> visible=true"))
        if t:
            return t

    return ""

def parse_live_time_to_seconds(tstr: str) -> int:
    if not tstr:
        return 0
    t = tstr.strip()
    if "終了" in t or "FT" in t.upper():
        return 5400
    if "前半" in t:
        num = re.sub(r"[^0-9]", "", t)
        return int(num) * 60 if num else 0
    if "後半" in t:
        num = re.sub(r"[^0-9]", "", t)
        return 2700 + int(num) * 60 if num else 2700
    if re.match(r"^\d+\+\d+$", t):
        a, b = t.split("+")
        return (int(a) + int(b)) * 60
    if t.isdigit():
        return int(t) * 60
    return 0


# =========================
# RowBuilder（dict）
# =========================
class RowBuilder:
    def __init__(self, header: List[str], match_tag: str = ""):
        self.header = header
        self.d = {col: "" for col in header}
        self.match_tag = match_tag

    def put(self, key: str, value: Any):
        if key not in self.d:
            return
        self.d[key] = "" if value is None else value

    def put_pair(self, home_key: str, away_key: str, home_val: Any, away_val: Any):
        self.put(home_key, home_val)
        self.put(away_key, away_val)

def apply_stats_to_row(rb: RowBuilder, canon_pairs: Dict[str, Tuple[str, str]]):
    for stat_key, (hcol, acol) in STAT_KEY_MAP.items():
        if stat_key in canon_pairs:
            hv, av = canon_pairs[stat_key]
            rb.put_pair(hcol, acol, hv, av)


# =========================
# stats raw（加工なし）
# =========================
STAT_ROW_SELECTOR = "[data-testid='wcl-statistics']"

def goto_statistics_page(pg) -> bool:
    stats_url = build_stats_url(pg.url)

    kill_consent_banners(pg)

    if "/summary/stats/" not in pg.url:
        log(f"➡️ [STATS] goto: {stats_url}")
        ok = safe_goto(pg, stats_url, timeout_ms=NAV_TIMEOUT_MS, tag="STATS")
        if not ok:
            log("⚠️ [STATS] goto失敗（safe_gotoでも）")
            return False
        kill_consent_banners(pg)

    if is_bot_wall(pg):
        log("🧱 [STATS] BOT壁っぽい → statsスキップ")
        return False

    log("⏳ [STATS] wait_for_selector wcl-statistics ...")
    for attempt in range(2):
        try:
            pg.wait_for_selector(STAT_ROW_SELECTOR, timeout=WAIT_TIMEOUT_MS)
            log("✅ [STATS] statistics selector appeared")
            return True
        except Exception as e:
            log(f"⚠️ [STATS] wait_for_selector failed({attempt+1}/2): {type(e).__name__}: {e}")
            kill_consent_banners(pg)

    log("⚠️ [STATS] 統計出ない（同意除去後も）")
    return False

def scrape_stats_raw(pg) -> Dict[str, List[str]]:
    """
    out: {"主なスタッツ:パス": ["34%（31/90）","51%（24/47）"], ...}
    ※ 値は加工しない
    """
    if not goto_statistics_page(pg):
        return {}

    log("📊 [STATS] JS extraction start")
    try:
        pg.wait_for_selector('[data-testid="wcl-statistics"]', timeout=WAIT_TIMEOUT_MS)
    except Exception:
        log("⚠️ [STATS] statistics selector not found")
        return {}

    stats = pg.evaluate("""
    () => {
      const out = {};
      const wrapper = document.querySelector('.sectionsWrapper');
      if (!wrapper) return out;

      const clean = (s) =>
        (s || '')
          .replace(/\\u00A0/g, ' ')
          .replace(/\\s+/g, ' ')
          .trim();

      const pickValue = (cell) => {
        if (!cell) return '';
        const spans = cell.querySelectorAll("span[data-testid='wcl-scores-simple-text-01']");
        if (!spans || spans.length === 0) return '';
        const parts = [];
        for (const sp of spans) {
          const t = clean(sp.textContent);
          if (t) parts.push(t);
        }
        return parts.join(' ');
      };

      const sections = wrapper.querySelectorAll('.section');
      for (const sec of sections) {
        const sectionTitle = clean(sec.querySelector('.sectionHeader')?.textContent);
        if (!sectionTitle) continue;

        const rows = sec.querySelectorAll('[data-testid="wcl-statistics"]');
        for (const row of rows) {
          const label = clean(
            row.querySelector('[data-testid="wcl-statistics-category"] span')?.textContent
          );
          if (!label) continue;

          const values = row.querySelectorAll('[data-testid="wcl-statistics-value"]');
          if (!values || values.length < 2) continue;

          const home = pickValue(values[0]);
          const away = pickValue(values[values.length - 1]);

          out[`${sectionTitle}:${label}`] = [home, away];
        }
      }
      return out;
    }
    """) or {}

    log(f"📊 [STATS] extracted items = {len(stats)}")
    return stats

def normalize_stats_raw_to_canon(stats_raw: Dict[str, Any]) -> Dict[str, Tuple[str, str]]:
    """
    stats_raw: {"主なスタッツ:合計シュート":[home,away], ...}
    -> {"シュート:シュート数":(home,away), ...}
    """
    if not stats_raw:
        return {}

    best_by_label: Dict[str, Tuple[int, Tuple[str, str]]] = {}

    for k, v in stats_raw.items():
        if not isinstance(v, (list, tuple)) or len(v) < 2:
            continue
        if ":" not in k:
            continue

        section, label = k.split(":", 1)
        section = (section or "").strip()
        label = (label or "").strip()

        canon = LABEL_TO_CANON.get(label)
        if not canon:
            continue

        hv, av = str(v[0]), str(v[1])
        rank = _section_rank(section)

        if (label not in best_by_label) or (rank < best_by_label[label][0]):
            best_by_label[label] = (rank, (hv, av))

    out: Dict[str, Tuple[str, str]] = {}
    for label, (_, pair) in best_by_label.items():
        canon = LABEL_TO_CANON.get(label)
        if canon:
            out[canon] = pair
    return out


# =========================
# meta（summary）
# =========================
def get_match_meta(pg) -> Dict[str, str]:
    meta: Dict[str, str] = {}
    summary_url = build_summary_url(pg.url)

    if "/summary/" not in pg.url or "/summary/stats" in pg.url:
        log(f"➡️ [META] goto: {summary_url}")
        ok = safe_goto(pg, summary_url, timeout_ms=NAV_TIMEOUT_MS, tag="META")
        if not ok:
            meta["試合時間"] = get_match_time_text(pg)
            meta["取得時刻"] = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
            return meta
        kill_consent_banners(pg)

    if is_bot_wall(pg):
        meta["試合時間"] = get_match_time_text(pg)
        meta["取得時刻"] = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        return meta

    try:
        pg.wait_for_selector(
            "ol li span[itemprop='name'], [data-testid*='breadcrumbs'], .tournamentHeader, .duelParticipant",
            timeout=WAIT_TIMEOUT_MS
        )
    except:
        pass

    country = ""
    league = ""

    try:
        crumb_txts = []
        cand = pg.locator("ol li span[itemprop='name'], [data-testid*='breadcrumbs'] span[itemprop='name']")
        for i in range(cand.count()):
            t = text_clean(cand.nth(i).text_content() or "")
            if t:
                crumb_txts.append(t)
        if len(crumb_txts) >= 2:
            country = crumb_txts[1]
        if len(crumb_txts) >= 3:
            league = crumb_txts[2]
    except:
        pass

    if not country or not league:
        try:
            c = pg.locator(".tournamentHeader__country, .tournamentHeader__category, [class*='tournamentHeader__category']").first
            t = text_clean(c.text_content() or "")
            if t:
                if ":" in t and (not country or not league):
                    a, b = [x.strip() for x in t.split(":", 1)]
                    if not country:
                        country = a
                    if not league:
                        league = b
                elif not country:
                    country = t
        except:
            pass

        try:
            l = pg.locator(".tournamentHeader__name, [class*='tournamentHeader__name']").first
            t = text_clean(l.text_content() or "")
            if t and not league:
                league = t
        except:
            pass

    # ここは軽く待つ（commit直後対策）
    try:
        pg.wait_for_selector("li[data-testid='wcl-breadcrumbsItem']", timeout=WAIT_TIMEOUT_MS)
    except:
        pass

    # ★ ここで新方式：パンくずを確実に取る
    country, league, crumbs = _get_breadcrumb_country_league(pg)
    if crumbs:
        log(f"🧩 [META] breadcrumbs={crumbs}")

    meta["国"] = country
    meta["リーグ"] = league

    label_aliases = {
        "レフェリー": "レフェリー",
        "審判": "レフェリー",
        "主審": "レフェリー",
        "開催地": "開催地",
        "スタジアム": "開催地",
        "会場": "開催地",
        "収容人数": "収容人数",
        "キャパシティ": "収容人数",
        "観客": "観客",
        "観客数": "観客",
        "参加": "観客",
    }
    want = set(label_aliases.keys())

    def put_meta(label: str, value: str):
        label = text_clean(label).replace(":", "").replace("：", "").strip()
        value = text_clean(value)
        if not label or not value:
            return
        norm = label_aliases.get(label, label)
        if norm not in meta:
            meta[norm] = value

    try:
        dts = pg.locator("dl dt")
        n = dts.count()
        for i in range(n):
            dt = dts.nth(i)
            lab = text_clean(dt.text_content() or "")
            if lab not in want:
                continue
            dd = dt.locator("xpath=following-sibling::dd[1]").first
            val = text_clean(dd.text_content() or "") if dd.count() else ""
            put_meta(lab, val)
    except:
        pass

    meta["試合時間"] = get_match_time_text(pg)
    meta["取得時刻"] = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    return meta

def jst_today_date() -> datetime.date:
    return (datetime.datetime.utcnow() + datetime.timedelta(hours=9)).date()

def parse_iso_date(dk: str) -> datetime.date:
    return datetime.datetime.strptime(dk, "%Y-%m-%d").date()

def fmt_ddmm(d: datetime.date) -> str:
    # dayPickerButton は "25/02 水" のように dd/mm
    return d.strftime("%d/%m")

def get_day_picker_text(page) -> str:
    try:
        # visible要件なしでDOMから取得（最強）
        t = page.evaluate("""
        () => {
          const b = document.querySelector("[data-testid='wcl-dayPickerButton']");
          return b ? (b.textContent || "").replace(/\\s+/g, " ").trim() : "";
        }
        """)
        return text_clean(t or "")
    except:
        return ""

def click_day_arrow(page, direction: str) -> bool:
    # direction: 'prev' or 'next'
    sel = f"button[data-day-picker-arrow='{direction}']"
    try:
        btn = page.locator(sel).first
        if btn.count():
            btn.scroll_into_view_if_needed(timeout=1500)
            btn.click(timeout=3000, force=True)
            return True
    except Exception as e:
        log(f"⚠️ [DATE] arrow click error({direction}): {type(e).__name__}: {e}")

    # aria fallback
    aria = "前日" if direction == "prev" else "翌日"
    try:
        btn = page.locator(f"button[aria-label='{aria}']").first
        if btn.count():
            btn.scroll_into_view_if_needed(timeout=1500)
            btn.click(timeout=3000, force=True)
            return True
    except Exception as e:
        log(f"⚠️ [DATE] arrow(aria) click error({direction}): {type(e).__name__}: {e}")

    return False

def goto_results_date_by_arrows(page, current_date: datetime.date, target_date: datetime.date) -> datetime.date:
    """
    現在日付 current_date（JST想定）から target_date に、prev/next で移動。
    戻り値: 到達したとみなす日付（基本は target_date、失敗時は途中）
    """
    if current_date == target_date:
        return current_date

    delta = (target_date - current_date).days
    direction = "next" if delta > 0 else "prev"
    steps = abs(delta)

    if steps > MAX_DAY_STEPS:
        log(f"⚠️ [DATE] steps={steps} > MAX_DAY_STEPS={MAX_DAY_STEPS} なので打ち切り（ENVで増やせます）")
        steps = MAX_DAY_STEPS

    want_ddmm = fmt_ddmm(target_date)

    for i in range(steps):
        kill_consent_banners(page)
        ok = click_day_arrow(page, direction)
        if not ok:
            log(f"❌ [DATE] arrow click failed direction={direction}")
            break

        # 一覧が更新されるので少し待つ（重い日は長めに）
        page.wait_for_timeout(600)

        # 表示確認（"25/02 水" などに want_ddmm が含まれるか）
        txt = get_day_picker_text(page)
        if want_ddmm and (want_ddmm in txt):
            return target_date

    # 最後にもう一度チェック（到達してる可能性がある）
    txt = get_day_picker_text(page)
    if want_ddmm and (want_ddmm in txt):
        return target_date

    # 到達できなかった場合は、current_date を近づけた分だけ進めた扱い（概算）
    moved = (steps if direction == "next" else -steps)
    return current_date + datetime.timedelta(days=moved)

# =========================
# standings（順位）
# =========================
def goto_standings_page(pg) -> Optional[str]:
    url1 = build_live_standings_url(pg.url)
    url2 = build_overall_standings_url(pg.url)

    log(f"➡️ [RANK] goto(live): {url1}")
    ok = safe_goto(pg, url1, timeout_ms=NAV_TIMEOUT_MS, tag="RANK-LIVE")
    if ok:
        kill_consent_banners(pg)
        try:
            pg.wait_for_selector(".ui-table__body .ui-table__row", timeout=WAIT_TIMEOUT_MS)
            return url1
        except Exception as e:
            log(f"⚠️ [RANK] live wait failed: {type(e).__name__}: {e}")

    log(f"➡️ [RANK] goto(overall): {url2}")
    ok = safe_goto(pg, url2, timeout_ms=NAV_TIMEOUT_MS, tag="RANK-OVERALL")
    if ok:
        kill_consent_banners(pg)
        try:
            pg.wait_for_selector(".ui-table__body .ui-table__row", timeout=WAIT_TIMEOUT_MS)
            return url2
        except Exception as e:
            log(f"⚠️ [RANK] overall wait failed: {type(e).__name__}: {e}")

    return None

def get_match_standings(pg, home_name: str, away_name: str) -> Dict[str, Any]:
    url = goto_standings_page(pg)
    if not url:
        return {}

    rows = pg.locator(".ui-table__body .ui-table__row")
    try:
        n = rows.count()
    except:
        n = 0
    if n == 0:
        return {}

    table = {}
    for i in range(n):
        r = rows.nth(i)
        rank_txt = text_clean(r.locator(".table__cell--rank .tableCellRank").first.text_content() or "")
        team_name = text_clean(r.locator(".table__cell--participant .tableCellParticipant__name").first.text_content() or "")
        pts_txt = text_clean(r.locator(".table__cell--value").last.text_content() or "")

        try:
            rank = int(rank_txt.strip().rstrip("."))
        except:
            rank = None
        try:
            pts = int(pts_txt)
        except:
            pts = None

        if team_name:
            table[team_name] = {"rank": rank, "pts": pts}

    h = text_clean(home_name)
    a = text_clean(away_name)

    home = next((v for k, v in table.items() if (h and (h in k or k in h))), None)
    away = next((v for k, v in table.items() if (a and (a in k or k in a))), None)

    return {
        "url": url,
        "home_rank": home["rank"] if home else None,
        "home_pts": home["pts"] if home else None,
        "away_rank": away["rank"] if away else None,
        "away_pts": away["pts"] if away else None,
    }


# =========================
# S3 utils
# =========================
def s3_client():
    return boto3.client("s3", region_name=(S3_REGION or None))

def s3_put_bytes(bucket: str, key: str, data: bytes, content_type: str) -> bool:
    s3 = s3_client()
    try:
        s3.put_object(Bucket=bucket, Key=key, Body=data, ContentType=content_type)
        log(f"✅ S3 put: s3://{bucket}/{key}")
        return True
    except ClientError as e:
        log(f"❌ S3 put failed: {e}")
        return False

def s3_get_bytes(bucket: str, key: str) -> Optional[bytes]:
    s3 = s3_client()
    try:
        obj = s3.get_object(Bucket=bucket, Key=key)
        return obj["Body"].read()
    except ClientError as e:
        code = e.response.get("Error", {}).get("Code", "")
        if code in ("NoSuchKey", "404", "NotFound"):
            return None
        log(f"❌ S3 get failed: {e}")
        return None

def collect_match_roots_on_current_results_page(page) -> List[str]:
    hrefs = set()

    # eventRowLink優先
    try:
        loc = page.locator("a.eventRowLink[href*='/match/']")
        n = loc.count()
        for i in range(n):
            h = loc.nth(i).get_attribute("href") or ""
            if "/match/" in h:
                hrefs.add(h)
    except:
        pass

    # fallback
    try:
        loc = page.locator("a[href*='/match/']")
        n = loc.count()
        for i in range(n):
            h = loc.nth(i).get_attribute("href") or ""
            if "/match/" in h:
                hrefs.add(h)
    except:
        pass

    out: List[str] = []
    for h in sorted(hrefs):
        if h.startswith("http"):
            out.append(h)
        elif h.startswith("/"):
            out.append("https://www.flashscore.co.jp" + h)
        else:
            out.append("https://www.flashscore.co.jp/" + h)
    return out

def collect_finished_match_roots_by_dates(date_keys: List[str]) -> Dict[str, List[str]]:
    out: Dict[str, List[str]] = {}

    with sync_playwright() as p:
        browser = p.chromium.launch(
            headless=HEADLESS,
            slow_mo=SLOW_MO_MS,
            args=["--no-sandbox", "--disable-dev-shm-usage", "--disable-gpu"],
        )
        ctx = browser.new_context(
            user_agent=USER_AGENT,
            locale=LOCALE,
            timezone_id=TIMEZONE_ID
        )
        ctx.set_default_timeout(15000)
        ctx.set_default_navigation_timeout(15000)
        setup_route_blocking(ctx)
        page = ctx.new_page()

        log("🌐 Flashscore football RESULTS を開きます...")

        ok = goto_results_tab(page)
        if not ok:
            try: browser.close()
            except: pass
            return {}

        # cursor_date は「dayPickerが示す日付」から推定するのが本当は理想ですが、
        # まずは JST 今日として進めます（あなたの画面だと 28/02 土 になっているので一致しやすい）
        cursor_date = jst_today_date()
        log(f"📅 [DATE] initial picker text='{get_day_picker_text(page)}' cursor_date(assumed)={cursor_date}")

        for dk in date_keys:
            target_date = parse_iso_date(dk)
            log(f"\n📅 [DATE] move to {dk} (dd/mm={fmt_ddmm(target_date)})")

            cursor_date = goto_results_date_by_arrows(page, cursor_date, target_date)
            kill_consent_banners(page)

            # その日ページでさらに一覧を展開
            click_show_more_results(page, max_load_more_clicks=MAX_LOAD_MORE_CLICKS)

            roots = collect_match_roots_on_current_results_page(page)
            out[dk] = roots
            log(f"✅ [RESULTS] {dk} collected roots: {len(roots)}")

        try: browser.close()
        except: pass

    return out

# =========================
# FIN JSON (target mids)
# =========================
FIN_JSON_BUCKET = "aws-s3-outputs-csv"
FIN_JSON_KEY    = "fin/b008_fin_getting_data.json"

def _collect_match_ids_from_obj(obj: Any) -> List[str]:
    if obj is None:
        return []
    if isinstance(obj, str):
        return [obj.strip()] if obj.strip() else []
    if isinstance(obj, list):
        out = []
        for x in obj:
            out.extend(_collect_match_ids_from_obj(x))
        return out
    if isinstance(obj, dict):
        for k in ("matchId", "mid", "match_id", "gameId"):
            v = obj.get(k)
            if isinstance(v, str) and v.strip():
                return [v.strip()]
        out = []
        for v in obj.values():
            out.extend(_collect_match_ids_from_obj(v))
        return out
    return []

def _as_date_key(s: str) -> Optional[str]:
    """ '2026-02-25' を date key として採用。ダメなら None """
    if not isinstance(s, str):
        return None
    s = s.strip()
    if re.fullmatch(r"\d{4}-\d{2}-\d{2}", s):
        return s
    return None

def _extract_mid_value(obj: Any) -> Optional[str]:
    if isinstance(obj, str) and obj.strip():
        return obj.strip()
    if isinstance(obj, dict):
        for k in ("matchId", "mid", "match_id", "gameId"):
            v = obj.get(k)
            if isinstance(v, str) and v.strip():
                return v.strip()
    return None

def _extract_date_value(obj: Any) -> Optional[str]:
    """ dictの中から日付らしきキーを拾う """
    if not isinstance(obj, dict):
        return None
    for k in ("date", "matchDate", "match_date", "ymd", "day"):
        v = obj.get(k)
        dk = _as_date_key(v) if isinstance(v, str) else None
        if dk:
            return dk
    return None

def load_target_mids_by_date_from_s3() -> Dict[str, List[str]]:
    """
    返り値: {"2026-02-25": ["mid1","mid2",...], ...}
    JSON形状の例:
      A) {"2026-02-25":["mid1","mid2"], "2026-02-26":[...]}
      B) [{"date":"2026-02-25","matchId":"mid1"}, ...]
      C) [["2026-02-25","mid1"], ["2026-02-25","mid2"]]
    """
    b = s3_get_bytes(FIN_JSON_BUCKET, FIN_JSON_KEY)
    if not b:
        log(f"❌ [FIN] not found: s3://{FIN_JSON_BUCKET}/{FIN_JSON_KEY}")
        return {}

    try:
        obj = json.loads(b.decode("utf-8"))
    except Exception as e:
        log(f"❌ [FIN] json parse failed: {e}")
        return {}

    out: Dict[str, List[str]] = {}

    # A) date-keyed dict
    if isinstance(obj, dict) and any(_as_date_key(k) for k in obj.keys()):
        for k, v in obj.items():
            dk = _as_date_key(k)
            if not dk:
                continue
            mids: List[str] = []
            if isinstance(v, list):
                for it in v:
                    mid = _extract_mid_value(it) or (it.strip() if isinstance(it, str) else None)
                    if mid:
                        mids.append(mid)
            elif isinstance(v, dict):
                mid = _extract_mid_value(v)
                if mid:
                    mids.append(mid)
            out[dk] = list(dict.fromkeys(mids))
        log(f"✅ [FIN] loaded date-keyed dict: days={len(out)} total={sum(len(v) for v in out.values())}")
        return out

    # B/C) list
    if isinstance(obj, list):
        for it in obj:
            # C) pair
            if isinstance(it, (list, tuple)) and len(it) >= 2:
                dk = _as_date_key(it[0] if isinstance(it[0], str) else "")
                mid = _extract_mid_value(it[1])
                if dk and mid:
                    out.setdefault(dk, []).append(mid)
                continue

            # B) object
            if isinstance(it, dict):
                dk = _extract_date_value(it)
                mid = _extract_mid_value(it)
                if dk and mid:
                    out.setdefault(dk, []).append(mid)
                continue

        for dk in list(out.keys()):
            out[dk] = list(dict.fromkeys([m for m in out[dk] if m]))
        log(f"✅ [FIN] loaded list: days={len(out)} total={sum(len(v) for v in out.values())}")
        return out

    # fallback（最悪：日付が取れない→当日扱いで全mid）
    mids = []
    if isinstance(obj, dict):
        mid = _extract_mid_value(obj)
        if mid:
            mids.append(mid)
    log("⚠️ [FIN] could not infer dates; fallback to today only")
    jst_today = (datetime.datetime.utcnow() + datetime.timedelta(hours=9)).date().strftime("%Y-%m-%d")
    return {jst_today: list(dict.fromkeys(mids))}

# =========================
# SEQMAP (S3)
# =========================
SEQMAP: Dict[str, int] = {}

def load_seqmap_from_s3():
    global SEQMAP
    b = s3_get_bytes(S3_BUCKET_OUTPUTS, SEQMAP_S3_KEY)
    if not b:
        SEQMAP = {}
        log(f"🆕 [SEQ] S3に既存なし: s3://{S3_BUCKET_OUTPUTS}/{SEQMAP_S3_KEY}")
        return
    try:
        SEQMAP = pickle.loads(b) or {}
        log(f"🔁 [SEQ] S3から読み込み: 件数={len(SEQMAP)}")
    except Exception as e:
        log(f"⚠️ [SEQ] S3 load失敗: {e}")
        SEQMAP = {}

def save_seqmap_to_s3():
    try:
        data = pickle.dumps(SEQMAP)
        ok = s3_put_bytes(
            S3_BUCKET_OUTPUTS,
            SEQMAP_S3_KEY,
            data,
            content_type="application/octet-stream"
        )
        if ok:
            log(f"💾 [SEQ] S3へ保存: 件数={len(SEQMAP)}")
    except Exception as e:
        log(f"⚠️ [SEQ] S3 save失敗: {e}")


# =========================
# row -> CSV bytes, key builder
# =========================
def row_dict_to_csv_bytes(row: Dict[str, Any], include_header: bool = True) -> bytes:
    buf = io.StringIO()
    w = csv.writer(buf, lineterminator="\n")
    if include_header:
        w.writerow(HEADER)
    w.writerow([row.get(col, "") for col in HEADER])
    return buf.getvalue().encode("utf-8")

def _normalize_prefix(prefix: str) -> str:
    if not prefix:
        return ""
    return prefix if prefix.endswith("/") else (prefix + "/")

def build_row_s3_key(target_date: str, mid: str, seq: int) -> str:
    ts = datetime.datetime.utcnow().strftime("%Y%m%dT%H%M%SZ")
    prefix = _normalize_prefix(S3_PREFIX)
    return f"{prefix}{target_date}/mid={mid}/seq={seq:06d}_{ts}.csv"

def upload_row_csv_to_s3(row: Dict[str, Any], mid: str, seq: int, output_date: Optional[str] = None) -> bool:
    # output_date を優先（例: "2026-02-25"）
    target_date = output_date or datetime.date.today().strftime("%Y-%m-%d")
    key = build_row_s3_key(target_date, mid, seq)
    body = row_dict_to_csv_bytes(row, include_header=INCLUDE_HEADER_IN_EACH_ROW)
    return s3_put_bytes(
        S3_BUCKET_OUTPUTS,
        key,
        body,
        content_type="text/csv; charset=utf-8"
    )

def upload_matched_urls_json_to_s3(matched: List[str]) -> bool:
    target_date = datetime.date.today().strftime("%Y-%m-%d")
    prefix = _normalize_prefix(S3_PREFIX)
    key = f"{prefix}{target_date}/matched_mid_urls_{datetime.datetime.utcnow().strftime('%Y%m%dT%H%M%SZ')}.json"
    body = json.dumps(matched, ensure_ascii=False, indent=2).encode("utf-8")
    return s3_put_bytes(S3_BUCKET_OUTPUTS, key, body, content_type="application/json; charset=utf-8")


# =========================
# Results列挙（終了済）
# =========================
def goto_results_tab(page) -> bool:
    kill_consent_banners(page)

    ok = safe_goto(page, "https://www.flashscore.co.jp/football/", timeout_ms=45000, tag="FOOTBALL")
    if not ok:
        return False
    kill_consent_banners(page)

    if is_bot_wall(page):
        log("🧱 [RESULTS] BOT wall on football page")
        return False

    # ① タブクリック優先（日本語UI想定）
    tab_candidates = [
        "div.filters__tab:has-text('結果')",
        "div.filters__tab:has-text('RESULTS')",
        "div.filters__tab:has-text('Results')",
        "a:has-text('結果')",
        "button:has-text('結果')",
    ]
    clicked = False
    for sel in tab_candidates:
        try:
            loc = page.locator(sel).first
            if loc.count():
                loc.scroll_into_view_if_needed(timeout=1500)
                loc.click(timeout=3000, force=True)
                clicked = True
                break
        except:
            pass

    # ② フォールバック：hash直叩き
    if not clicked:
        ok = safe_goto(page, "https://www.flashscore.co.jp/football/#/results", timeout_ms=45000, tag="RESULTS")
        if not ok:
            return False

    kill_consent_banners(page)

    # SPA描画待ち（networkidleは効かないことがあるので軽く待つ）
    page.wait_for_timeout(1500)
    kill_consent_banners(page)

    # dayPicker は visible で待つと落ちるので attached でOK
    try:
        page.wait_for_selector("[data-testid='wcl-dayPicker']", timeout=30000, state="attached")
        page.wait_for_selector("[data-testid='wcl-dayPickerButton']", timeout=30000, state="attached")
        page.wait_for_selector("button[data-day-picker-arrow='prev']", timeout=30000, state="attached")
        page.wait_for_selector("button[data-day-picker-arrow='next']", timeout=30000, state="attached")
        log(f"✅ [RESULTS] dayPicker attached. text='{get_day_picker_text(page)}'")
        return True
    except Exception as e:
        log(f"⚠️ [RESULTS] dayPicker not found: {type(e).__name__}: {e}")
        log(f"[DEBUG] url={page.url}")
        try:
            html_head = (page.content() or "")[:1200]
            log("[DEBUG] html_head=" + html_head.replace("\n", " ")[:1200])
        except:
            pass
        return False

def click_show_more_results(page, max_load_more_clicks: int):
    for _ in range(max_load_more_clicks):
        kill_consent_banners(page)
        page.wait_for_timeout(350)

        btn_candidates = [
            "text=もっと表示",
            "text=さらに表示",
            "text=Show more matches",
            "text=Show more",
            "button.event__more",
            ".event__more",
            ".event__more--static",
        ]
        clicked = False
        for sel in btn_candidates:
            try:
                b = page.locator(sel).first
                if b.count() and b.is_visible(timeout=900):
                    b.scroll_into_view_if_needed(timeout=900)
                    b.click(timeout=2500, force=True)
                    clicked = True
                    break
            except:
                pass
        if not clicked:
            break

def collect_finished_match_links(max_load_more_clicks: int) -> List[str]:
    out: List[str] = []

    with sync_playwright() as p:
        browser = p.chromium.launch(
            headless=HEADLESS,
            slow_mo=SLOW_MO_MS,
            args=["--no-sandbox", "--disable-dev-shm-usage", "--disable-gpu"],
        )
        ctx = browser.new_context(
            user_agent=USER_AGENT,
            locale=LOCALE,
            timezone_id=TIMEZONE_ID
        )
        ctx.set_default_timeout(15000)
        ctx.set_default_navigation_timeout(15000)
        setup_route_blocking(ctx)
        page = ctx.new_page()

        log("🌐 Flashscore football を開きます...")
        ok = safe_goto(page, "https://www.flashscore.co.jp/football/", timeout_ms=45000, tag="FOOTBALL")
        if not ok:
            log("❌ footballページが開けませんでした")
            try: browser.close()
            except: pass
            return []

        goto_results_tab(page)

        try:
            page.wait_for_selector("a[href*='/match/']", timeout=20000)
        except:
            pass

        click_show_more_results(page, MAX_LOAD_MORE_CLICKS)

        hrefs = set()
        # eventRowLink優先
        try:
            loc = page.locator("a.eventRowLink[href*='/match/']")
            n = loc.count()
            for i in range(n):
                h = loc.nth(i).get_attribute("href") or ""
                if "/match/" in h:
                    hrefs.add(h)
        except:
            pass

        # fallback
        try:
            loc = page.locator("a[href*='/match/']")
            n = loc.count()
            for i in range(n):
                h = loc.nth(i).get_attribute("href") or ""
                if "/match/" in h:
                    hrefs.add(h)
        except:
            pass

        for h in sorted(hrefs):
            if h.startswith("http"):
                out.append(h)
            elif h.startswith("/"):
                out.append("https://www.flashscore.co.jp" + h)
            else:
                out.append("https://www.flashscore.co.jp/" + h)

        log(f"✅ [RESULTS] collected match links: {len(out)}")

        try: browser.close()
        except: pass

    return out


# =========================
# midリンク突合（終了済ページ内）
# =========================
def find_matching_mid_url_on_page(pg, target_mids_set: set) -> Optional[str]:
    kill_consent_banners(pg)

    # まずURL自体にmidが付いていて一致するならそれでOK
    cur_mid = extract_mid(pg.url) or ""
    if cur_mid and cur_mid in target_mids_set:
        return pg.url

    # ページ内の a[href*="mid="] を走査
    try:
        links = pg.locator("a[href*='mid=']")
        n = links.count()
    except:
        n = 0

    for i in range(n):
        try:
            href = links.nth(i).get_attribute("href") or ""
            mid = extract_mid(href) or ""
            if mid and (mid in target_mids_set):
                if href.startswith("http"):
                    return href
                if href.startswith("/"):
                    return "https://www.flashscore.co.jp" + href
                return "https://www.flashscore.co.jp/" + href
        except:
            pass

    return None


# =========================
# Worker：1試合処理（終了済）
# =========================
def process_one_match_in_worker(match_root_url: str, target_mids: List[str]) -> Dict[str, Any]:
    """
    match_root_url: Results一覧から拾った /match/... （mid無しでもOK）
    target_mids: S3 JSON の matchId(mid) 集合（親で pending を渡す）
    """
    result: Dict[str, Any] = {"ok": False, "url": match_root_url}
    target_set = set(target_mids or [])

    with sync_playwright() as p:
        browser = p.chromium.launch(
            headless=HEADLESS,
            slow_mo=SLOW_MO_MS,
            args=["--no-sandbox", "--disable-dev-shm-usage", "--disable-gpu"],
        )
        ctx = browser.new_context(
            user_agent=USER_AGENT,
            locale=LOCALE,
            timezone_id=TIMEZONE_ID
        )
        ctx.set_default_timeout(15000)
        ctx.set_default_navigation_timeout(15000)
        setup_route_blocking(ctx)
        pg = ctx.new_page()

        try:
            log("🧩 [WORKER] open finished match root")
            ok = safe_goto(pg, match_root_url, timeout_ms=NAV_TIMEOUT_MS, tag="MATCH(ROOT)")
            if not ok:
                return {"ok": False, "url": match_root_url, "error": "match_goto_failed"}

            kill_consent_banners(pg)
            if is_bot_wall(pg):
                return {"ok": False, "url": match_root_url, "error": "bot_wall"}

            # midリンク突合
            mid_url = find_matching_mid_url_on_page(pg, target_set)
            if not mid_url:
                return {"ok": False, "url": match_root_url, "error": "no_matching_mid_link"}

            log(f"🎯 [WORKER] matched mid link -> {mid_url}")
            ok = safe_goto(pg, mid_url, timeout_ms=NAV_TIMEOUT_MS, tag="MATCH(MID)")
            if not ok:
                return {"ok": False, "url": match_root_url, "mid_url": mid_url, "error": "mid_goto_failed"}

            kill_consent_banners(pg)
            if is_bot_wall(pg):
                return {"ok": False, "url": match_root_url, "mid_url": mid_url, "error": "bot_wall_after_mid"}

            mid = extract_mid(pg.url) or ""
            rb = RowBuilder(HEADER, match_tag=mid)
            rb.put("試合ID", mid)
            rb.put("試合リンク文字列", pg.url)

            # teams/scores/time
            log("🔎 [WORKER] read teams/scores/time")
            home, away = get_home_away_names(pg)
            hs, aw = get_scores(pg)
            ttxt = get_match_time_text(pg)

            rb.put("ホームチーム", home)
            rb.put("アウェーチーム", away)
            rb.put("ホームスコア", hs)
            rb.put("アウェースコア", aw)
            rb.put("試合時間", ttxt)

            # meta
            meta = get_match_meta(pg)
            country = (meta.get("国") or "").strip()
            league  = (meta.get("リーグ") or "").strip()

            if country and league:
                meta_category = f"{country}: {league}"
            elif league:
                meta_category = league
            elif country:
                meta_category = country
            else:
                meta_category = ""  # 最後まで取れなかった場合のみ空

            rb.put("試合国及びカテゴリ", meta_category)
            rb.put("スコア時間", meta.get("取得時刻", ""))

            if meta.get("開催地"):
                rb.put("スタジアム", meta.get("開催地"))
            if meta.get("収容人数"):
                rb.put("収容人数", meta.get("収容人数"))
            if meta.get("観客"):
                rb.put("観客数", meta.get("観客"))
            if meta.get("レフェリー"):
                rb.put("審判名", meta.get("レフェリー"))

            # standings
            log("📌 [WORKER] standings")
            st = get_match_standings(pg, home, away)
            if st:
                if st.get("home_rank") is not None:
                    rb.put("ホーム順位", st.get("home_rank"))
                if st.get("away_rank") is not None:
                    rb.put("アウェー順位", st.get("away_rank"))

            # stats（加工なし）
            log("📈 [WORKER] scrape stats")
            stats_raw = scrape_stats_raw(pg)
            canon_pairs = normalize_stats_raw_to_canon(stats_raw)

            verify_stats_mapping("CANON", canon_pairs)
            apply_stats_to_row(rb, canon_pairs)

            # 通番は親で付与
            rb.put("通番", "")
            rb.put("ソート用秒", parse_live_time_to_seconds(rb.d.get("試合時間", "")))

            verify_row_filled(rb.d)

            result.update({
                "ok": True,
                "mid": mid,
                "mid_url": pg.url,
                "category": meta_category,
                "row": rb.d,
            })
            return result

        except Exception as e:
            result["error"] = f"{type(e).__name__}: {e}"
            result["trace"] = traceback.format_exc(limit=10)
            log(f"💥 [WORKER] exception: {result['error']}")
            log(result["trace"])
            return result

        finally:
            try: pg.close()
            except: pass
            try: browser.close()
            except: pass


def _worker_entry(match_root_url: str, target_mids: List[str], q: "mp.Queue"):
    res = process_one_match_in_worker(match_root_url, target_mids=target_mids)
    try:
        q.put(res)
    except:
        pass

def run_match_with_timeout(match_root_url: str, target_mids: List[str], timeout_sec: int = WORKER_TIMEOUT_SEC) -> Dict[str, Any]:
    q: mp.Queue = mp.Queue(maxsize=1)
    p = mp.Process(target=_worker_entry, args=(match_root_url, target_mids, q), daemon=True)
    p.start()
    p.join(timeout=timeout_sec)

    if p.is_alive():
        log(f"🧨 [TIMEOUT] 子プロセス強制終了: {match_root_url} ({timeout_sec}s)")
        try:
            p.terminate()
        except:
            pass
        p.join(3)
        return {"ok": False, "url": match_root_url, "error": f"timeout({timeout_sec}s)"}

    try:
        return q.get(timeout=2)
    except pyqueue.Empty:
        return {"ok": False, "url": match_root_url, "error": "no_result_from_worker"}

def load_targets_by_date_from_s3() -> Dict[str, List[Dict[str, str]]]:
    """
    return:
      {
        "2026-02-25": [
          {"matchId":"vmwaNZho", "matchUrl":"https://.../?mid=vmwaNZho"},
          {"matchId":"xxxx"}
        ],
        ...
      }
    """
    b = s3_get_bytes(FIN_JSON_BUCKET, FIN_JSON_KEY)
    if not b:
        log(f"❌ [FIN] not found: s3://{FIN_JSON_BUCKET}/{FIN_JSON_KEY}")
        return {}

    try:
        obj = json.loads(b.decode("utf-8"))
    except Exception as e:
        log(f"❌ [FIN] json parse failed: {e}")
        return {}

    out: Dict[str, List[Dict[str, str]]] = {}

    if isinstance(obj, dict):
        for dk, arr in obj.items():
            if not _as_date_key(dk):
                continue
            targets: List[Dict[str, str]] = []
            if isinstance(arr, list):
                for it in arr:
                    if isinstance(it, dict):
                        mid = (it.get("matchId") or it.get("mid") or "").strip()
                        url = (it.get("matchUrl") or it.get("url") or "").strip()
                        if mid:
                            t = {"matchId": mid}
                            if url:
                                t["matchUrl"] = url
                            targets.append(t)
                    elif isinstance(it, str) and it.strip():
                        targets.append({"matchId": it.strip()})
            out[dk] = list(dict.fromkeys([json.dumps(t, sort_keys=True, ensure_ascii=False) for t in targets]))
            # ↑ 一旦JSON文字列で重複除去 → 元に戻す
            out[dk] = [json.loads(s) for s in out[dk]]

        log(f"✅ [FIN] loaded targets: days={len(out)} total={sum(len(v) for v in out.values())}")
        return out

    log("⚠️ [FIN] JSON shape not supported (expected date-keyed dict).")
    return {}

# =========================
# Main
# =========================
def main():
    try:
        if FORCE_SPAWN:
            mp.set_start_method("spawn", force=True)
    except RuntimeError:
        pass

    log(f"ENV HEADLESS={os.environ.get('HEADLESS')} -> {HEADLESS}, SLOW_MO_MS={os.environ.get('SLOW_MO_MS')} -> {SLOW_MO_MS}")
    log(f"S3_BUCKET_OUTPUTS={S3_BUCKET_OUTPUTS}, S3_PREFIX='{S3_PREFIX}', SEQMAP_S3_KEY={SEQMAP_S3_KEY}")

    verify_header_and_stat_map()
    load_seqmap_from_s3()

    targets_by_date = load_targets_by_date_from_s3()
    if not targets_by_date:
        log("❌ targets_by_date が空です。終了します。")
        return

    date_keys = sorted(targets_by_date.keys())
    log(f"🗓️ target days = {len(date_keys)} : {date_keys[:10]}{'...' if len(date_keys)>10 else ''}")

    matched_mid_urls: List[str] = []

    for dk in date_keys:
        targets = targets_by_date.get(dk, [])
        if not targets:
            continue

        # 1) matchUrl があるものは、RESULTS列挙なしで直接処理（最速・安定）
        direct = [t for t in targets if t.get("matchUrl")]
        indirect = [t for t in targets if not t.get("matchUrl")]

        log("\n==============================")
        log(f"📌 DATE {dk}: direct={len(direct)} indirect={len(indirect)}")
        log("==============================")

        # --- direct ---
        for t in direct:
            mid = t["matchId"]
            url = t["matchUrl"]
            res = run_match_with_timeout(url, target_mids=[mid], timeout_sec=WORKER_TIMEOUT_SEC)
            if not res.get("ok"):
                log(f"⚠️ [DIRECT] fail mid={mid} err={res.get('error')}")
                continue

            row = res.get("row", {}) or {}
            mid_url = res.get("mid_url", "") or url

            # 通番確定（親のみ更新）
            last_seq = int(SEQMAP.get(mid, 0)) if mid else 0
            seq = last_seq + 1
            if mid:
                SEQMAP[mid] = seq

            row["通番"] = seq
            row["試合ID"] = mid

            ok = upload_row_csv_to_s3(row, mid=mid, seq=seq, output_date=dk)
            if not ok:
                log(f"❌ [DIRECT] upload failed mid={mid}")
                continue

            matched_mid_urls.append(mid_url)

        # --- indirect（midだけ）---
        if indirect:
            # この日付の pending mids
            pending = set([t["matchId"] for t in indirect if t.get("matchId")])
            if not pending:
                continue

            # RESULTS その日に移動して /match/... を列挙（あなたの実装を使う）
            roots_by_date = collect_finished_match_roots_by_dates([dk])
            roots = roots_by_date.get(dk, []) or []

            log(f"📄 [INDIRECT] roots={len(roots)} pending={len(pending)}")

            for match_root_url in roots:
                if STOP_WHEN_ALL_MIDS_FOUND and not pending:
                    log(f"✅ [INDIRECT] pending empty -> stop date={dk}")
                    break

                res = run_match_with_timeout(match_root_url, target_mids=list(pending), timeout_sec=WORKER_TIMEOUT_SEC)
                if not res.get("ok"):
                    continue

                mid = (res.get("mid", "") or "").strip()
                if not mid:
                    continue

                row = res.get("row", {}) or {}
                mid_url = res.get("mid_url", "") or ""

                if mid in pending:
                    pending.remove(mid)

                # 通番確定（親のみ更新）
                last_seq = int(SEQMAP.get(mid, 0)) if mid else 0
                seq = last_seq + 1
                if mid:
                    SEQMAP[mid] = seq

                row["通番"] = seq
                row["試合ID"] = mid

                ok = upload_row_csv_to_s3(row, mid=mid, seq=seq, output_date=dk)
                if not ok:
                    log(f"❌ [INDIRECT] upload failed mid={mid}")
                    continue

                if mid_url:
                    matched_mid_urls.append(mid_url)

            if pending:
                log(f"⚠️ [INDIRECT] not found mids (first 30): {sorted(list(pending))[:30]}")

    # matched urls をまとめてS3へ（任意）
    if matched_mid_urls:
        matched_mid_urls = list(dict.fromkeys(matched_mid_urls))
        upload_matched_urls_json_to_s3(matched_mid_urls)

    save_seqmap_to_s3()
    log("🎉 完了")


if __name__ == "__main__":
    main()
