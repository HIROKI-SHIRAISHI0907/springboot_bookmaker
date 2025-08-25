import sys
import glob
import os
import pandas as pd
from playwright.sync_api import sync_playwright

EXCEL_BASE_DIR = "/Users/shiraishitoshio/bookmaker/"

def get_existing_xlsx_seqs():
    xlsx_files = glob.glob(f"{EXCEL_BASE_DIR}team_member_*.xlsx")
    seqs = []

    for path in xlsx_files:
        try:
            basename = os.path.basename(path)
            if not basename.startswith("team_member_") or not basename.endswith(".xlsx"):
                continue
            num = int(basename.replace("team_member_", "").replace(".xlsx", ""))
            seqs.append(num)
        except ValueError:
            continue

    if not seqs:
        print("変換対象の team_member_*.xlsx ファイルが見つかりません。")
        return []

    print("対象ファイル数:", len(seqs))
    return sorted(seqs)

def convertMaxSeq():
    maxSeq = 1
    for path in glob.glob(f"{EXCEL_BASE_DIR}team_member_*.csv"):
        try:
            basename = os.path.basename(path)
            num = int(basename.replace("team_member_", "").replace(".csv", ""))
            if maxSeq < num:
                maxSeq = num
        except ValueError:
            continue
    print("最大の変換csv通番:", maxSeq)
    return maxSeq + 1

def loadFileAndGetLink(seq, updChkCount):
    file_name = f"team_member_{seq}.xlsx"
    file_path = f"{EXCEL_BASE_DIR}{file_name}"

    try:
        df = pd.read_excel(file_path)
        df["連番"] = range(1, len(df) + 1)
    except Exception as e:
        print(f"ファイル読み込み失敗 or 破損: {file_name}")
        if os.path.exists(file_path):  # ✅ ここを追加
            os.remove(file_path)
        return [], [], [], [], file_path, updChkCount

    required_cols = {"国", "リーグ", "所属チーム", "選手名", "ポジション"}
    if not required_cols.issubset(df.columns):
        print(f"必要なカラムが不足しています: {file_name}")
        return [], [], [], [], file_path, updChkCount

    seq_list, country_list, league_list, belong_list, mem_list, position_list = [], [], [], [], [], []

    for _, row in df.iterrows():
        if pd.isna(row["国"]) or pd.isna(row["リーグ"]) or pd.isna(row["所属チーム"]) or pd.isna(row["選手名"]) or pd.isna(row["ポジション"]):
            continue
        seq_list.append(row["連番"])
        country_list.append(row["国"])
        league_list.append(row["リーグ"])
        belong_list.append(row["所属チーム"])
        mem_list.append(row["選手名"])
        position_list.append(row["ポジション"])

    updChkCount += 1

    if not seq_list:
        print("対象更新データが存在しません。", file_path)

    return seq_list, link_list, home_score_list, away_score_list, file_path, updChkCount

def excel_to_csv(excel_file, csv_file):
    try:
        df = pd.read_excel(excel_file)
        df.to_csv(csv_file, index=False)
        os.remove(excel_file)
    except Exception as e:
        print("Excel -> CSV変換失敗:", e)

# ----------------------
# メイン処理
# ----------------------
with sync_playwright() as playwright:
    updChkCount = 0
    seq_list_all = get_existing_xlsx_seqs()

    if not seq_list_all:
        sys.exit("変換対象が存在しないため終了します。")

    for seq in seq_list_all:
        file_name = f"team_member_{seq}.xlsx"
        file_path = f"{EXCEL_BASE_DIR}{file_name}"

        if os.path.exists(file_path):
            max_seq = convertMaxSeq()
            output_csv_path = f"{EXCEL_BASE_DIR}team_member_{max_seq}.csv"
            excel_to_csv(file_path, output_csv_path)

        seq_list, link_list, home_score_list, away_score_list, file_path, updChkCount = loadFileAndGetLink(seq, updChkCount)

        if seq_list:
            print("対象データが見つかりました:", file_path)

        print(f"{updChkCount}/{len(seq_list_all)}番目")

        if updChkCount >= 50:
            break
