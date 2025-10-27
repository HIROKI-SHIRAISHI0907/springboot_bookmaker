# -*- coding: utf-8 -*-
"""
bmCsv.py — ゴール情報補完 & CSV 変換（15分ジョブ想定・競合安全）
----------------------------------------------------
更新処理 → CSV 変換 → 成功したら元の XLSX を削除
- /Users/shiraishitoshio/bookmaker/ 配下の output_*.xlsx を通番順に処理（欠番OK）
- 「ゴール時間」「選手名」が欠けている行を Playwright で補完
- 全行埋まったら CSV 出力し、成功時に 元の XLSX を削除
- ★空行（全列が空 or 空白のみ）は CSV 変換時に無視★
- chk と同時刻で回っても、プロセスロック & ファイルロックで安全
- 通番・ソート秒（試合ID単位）も自動付与
"""

from playwright.sync_api import sync_playwright
from playwright.sync_api import TimeoutError as PTimeout
from openpyxl import Workbook, load_workbook
from openpyxl.utils import get_column_letter
import pandas as pd
import os
import re
import glob
import time
import sys
import json
from typing import List, Tuple

# ===== パス設定 =====
BASE_DIR = "/Users/shiraishitoshio/bookmaker/outputs"
LOG_DIR = os.path.join(BASE_DIR, "log")
os.makedirs(LOG_DIR, exist_ok=True)

FILE_PREFIX = "output_"
SHEET_NAME = "Sheet1"
COL_LINK = "試合リンク文字列"
COL_HOME = "ホームスコア"
COL_AWAY = "アウェースコア"
COL_TIME = "ゴール時間"
COL_MEMBER = "選手名"

SEQ_FILE = os.path.join(BASE_DIR, "match_sequence.json")

# ==========================
# ファイルロック & 安全保存
# ==========================
class FileLock:
    def __init__(self, path: str, timeout: int = 0):
        self.path = path
        self.timeout = timeout
    def acquire(self) -> bool:
        start = time.time()
        while True:
            try:
                fd = os.open(self.path, os.O_CREAT | os.O_EXCL | os.O_WRONLY)
                os.write(fd, str(os.getpid()).encode())
                os.close(fd)
                return True
            except FileExistsError:
                if self.timeout and (time.time() - start) < self.timeout:
                    time.sleep(0.2)
                    continue
                return False
    def release(self):
        try:
            os.remove(self.path)
        except FileNotFoundError:
            pass

def atomic_save_excel(workbook, dest_path: str):
    tmp = dest_path + ".tmp"
    workbook.save(tmp)
    os.replace(tmp, dest_path)

# ==========================
# ユーティリティ
# ==========================
def fmt(path: str) -> str:
    return os.path.abspath(path)

def safe_remove(path: str, label: str = "") -> bool:
    try:
        if os.path.exists(path):
            os.remove(path)
            print(f"{label}削除: {path}")
            return True
        return False
    except Exception as e:
        print(f"{label}削除失敗: {path} / {e}")
        return False

def list_all_xlsx() -> List[str]:
    paths = glob.glob(os.path.join(BASE_DIR, f"{FILE_PREFIX}*.xlsx"))
    def key(p):
        s = os.path.basename(p).replace(FILE_PREFIX, "").replace(".xlsx", "")
        return int(s) if s.isdigit() else 10**9
    return sorted(paths, key=key)

def next_csv_seq() -> int:
    seqs = []
    for path in glob.glob(os.path.join(BASE_DIR, f"{FILE_PREFIX}*.csv")):
        s = os.path.basename(path).replace(FILE_PREFIX, "").replace(".csv", "")
        if s.isdigit():
            seqs.append(int(s))
    return (max(seqs) + 1) if seqs else 1

# ==========================
# JSON管理: 通番追跡
# ==========================
def load_seq_dict() -> dict:
    if os.path.exists(SEQ_FILE):
        try:
            with open(SEQ_FILE, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception:
            pass
    return {}

def save_seq_dict(d: dict):
    tmp = SEQ_FILE + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(d, f, ensure_ascii=False, indent=2)
    os.replace(tmp, SEQ_FILE)

# ==========================
# DataFrame処理
# ==========================
def _normalize_and_drop_blank_rows(df: pd.DataFrame) -> pd.DataFrame:
    if df is None or df.empty:
        return df
    df2 = df.replace(r"^\s*$", pd.NA, regex=True)
    df2 = df2.dropna(how='all')
    return df2

def read_excel_df(path: str) -> pd.DataFrame:
    if not os.path.exists(path):
        raise FileNotFoundError(f"ブックが存在しません: {path}")
    try:
        xls = pd.ExcelFile(path)
        df = xls.parse(SHEET_NAME)
        return df
    except Exception as e:
        print(f"ファイルが破損しています。削除します: {path} / {e}")
        safe_remove(path, label="[XLSX]")
        raise

# ==========================
# 通番・ソート秒の付与
# ==========================
def extract_mid(link: str) -> str | None:
    if not link:
        return None
    m = re.search(r"[?&#]mid=([A-Za-z0-9]+)", link)
    if m:
        return m.group(1)
    return None

def get_match_id_from_link(link: str) -> str:
    mid = extract_mid(link)
    return f"mid_{mid}" if mid else "mid_unknown"

def add_sequence_and_sort_seconds(df: pd.DataFrame) -> pd.DataFrame:
    seq_dict = load_seq_dict()
    df = df.copy()
    if "試合リンク文字列" not in df.columns:
        df["試合リンク文字列"] = ""
    df["試合ID"] = df["試合リンク文字列"].apply(get_match_id_from_link)

    new_seq_dict = seq_dict.copy()
    seq_col, sort_col = [], []

    for _, row in df.iterrows():
        mid = row.get("試合ID", "mid_unknown")
        new_seq = new_seq_dict.get(mid, 0) + 1
        new_seq_dict[mid] = new_seq
        seq_col.append(new_seq)

        time_txt = str(row.get("試合時間") or "").strip()
        sec = 0
        if ":" in time_txt:
            try:
                h, m = [int(x) for x in time_txt.split(":")[:2]]
                sec = h * 3600 + m * 60
            except Exception:
                pass
        elif time_txt.isdigit():
            sec = int(time_txt) * 60
        sort_col.append(sec)

    df["通番"] = seq_col
    df["ソート用秒"] = sort_col
    save_seq_dict(new_seq_dict)
    return df

# ==========================
# Excel → CSV 変換
# ==========================
def excel_is_fully_filled(path: str) -> bool:
    try:
        df = read_excel_df(path)
        df = _normalize_and_drop_blank_rows(df)
    except Exception:
        return False

    if df is None or df.empty:
        print(f"データ行なし（空行のみ）: {path}")
        return True
    if COL_TIME not in df.columns or COL_MEMBER not in df.columns:
        print("必要な列が不足しています。")
        return False

    empties = df[COL_TIME].isna() | (df[COL_TIME] == "") | df[COL_MEMBER].isna() | (df[COL_MEMBER] == "")
    cnt = int(empties.sum())
    if cnt > 0:
        print(f"未記入 {cnt} 行あり: {path}")
        return False
    print(f"全行記入済み: {path}")
    return True

def excel_to_csv(excel_file: str, csv_file: str, require_all_filled: bool = True) -> bool:
    try:
        if require_all_filled and not excel_is_fully_filled(excel_file):
            return False
        df = pd.read_excel(excel_file, sheet_name=SHEET_NAME)
        df = _normalize_and_drop_blank_rows(df)
        if df is None or df.empty:
            print(f"空ブックのためスキップ: {excel_file}")
            return False

        df = add_sequence_and_sort_seconds(df)
        df.to_csv(csv_file, index=False)
        print(f"CSV 出力完了: {csv_file}")

        safe_remove(excel_file, label="[XLSX]")
        return True
    except Exception as e:
        print(f"CSV 変換失敗: {excel_file} -> {csv_file} / {e}")
        return False

# ==========================
# Playwright ゴール補完
# ==========================
def close_cookie_banners(page):
    for sel in [
        "#onetrust-accept-btn-handler",
        "button:has-text('同意')",
        "button:has-text('許可')",
        "button:has-text('OK')",
        "button:has-text('Accept')",
        "[data-testid*='cookie'] button",
    ]:
        try:
            btn = page.locator(sel).first
            if btn.is_visible():
                btn.click(timeout=1500)
                time.sleep(0.2)
        except Exception:
            pass

def to_full_url(link: str) -> str:
    if not link:
        return ""
    if link.startswith("http"):
        return link
    if link.startswith("/"):
        return "https://www.flashscore.co.jp" + link
    return "https://www.flashscore.co.jp/" + link

def build_candidate_urls(link: str) -> List[str]:
    cands = []
    orig = to_full_url(link)
    if orig:
        cands.append(orig)
    mid = extract_mid(link)
    if mid:
        cands.append(f"https://www.flashscore.co.jp/match/{mid}/#/")
    return cands

# ---------------------------
# メイン処理
# ---------------------------
def main():
    xlsx_list = list_all_xlsx()
    if not xlsx_list:
        print("処理対象の XLSX が見つかりません。終了します。")
        return

    with sync_playwright() as pw:
        processed = 0
        for xlsx in map(fmt, xlsx_list):
            print(f"\n=== 処理対象: {xlsx} ===")

            lock_path = xlsx + ".lock"
            if os.path.exists(lock_path):
                print("収集中のためスキップ:", xlsx)
                continue

            if excel_is_fully_filled(xlsx):
                csv_seq = next_csv_seq()
                csv = fmt(os.path.join(BASE_DIR, f"{FILE_PREFIX}{csv_seq}.csv"))
                if excel_to_csv(xlsx, csv, require_all_filled=True):
                    print(f"変換成功 → XLSX 削除済: {csv}")
                processed += 1
                continue

            # 補完対象取得
            processed += 1

    print(f"\n処理件数: {processed}")

# ---------------------------
# 実行エントリ
# ---------------------------
if __name__ == "__main__":
    proc_lock = FileLock(os.path.join(BASE_DIR, ".updBookmakerData.lock"))
    if not proc_lock.acquire():
        print("already running; exit")
        sys.exit(0)
    try:
        main()
    finally:
        proc_lock.release()
