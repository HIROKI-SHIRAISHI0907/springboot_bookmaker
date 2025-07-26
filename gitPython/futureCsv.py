import sys
import glob
import os
import pandas as pd
from playwright.sync_api import sync_playwright

def get_existing_xlsx_seqs():
    xlsx_files = glob.glob("/Users/shiraishitoshio/bookmaker/future_*.xlsx")
    seqs = []

    for path in xlsx_files:
        try:
            basename = os.path.basename(path)
            if not basename.startswith("future_") or not basename.endswith(".xlsx"):
                continue
            num = int(basename.replace("future_", "").replace(".xlsx", ""))
            seqs.append(num)
        except ValueError:
            continue

    if not seqs:
        print("変換対象の future_*.xlsx ファイルが見つかりません。")
        return []

    print("対象ファイル数:", len(seqs))
    return sorted(seqs)

def convertMaxSeq():
    maxSeq = 1
    for path in glob.glob("/Users/shiraishitoshio/bookmaker/future_*.csv"):
        try:
            basename = os.path.basename(path)
            num = int(basename.replace("future_", "").replace(".csv", ""))
            if maxSeq < num:
                maxSeq = num
        except ValueError:
            continue
    print("最大の変換csv通番:", maxSeq)
    return maxSeq + 1

def loadFileAndGetLink(seq, updChkCount):
    file_name = f"future_{seq}.xlsx"
    file_path = f"/Users/shiraishitoshio/bookmaker/{file_name}"

    try:
        df = pd.read_excel(file_path)
        df["連番"] = range(1, len(df) + 1)
    except Exception as e:
        print(f"ファイル読み込み失敗 or 破損: {file_name}")
        if os.path.exists(file_path):  # ✅ ここを追加
            os.remove(file_path)
        return [], [], [], [], file_path, updChkCount

    required_cols = {"試合リンク文字列", "ホームスコア", "アウェースコア"}
    if not required_cols.issubset(df.columns):
        print(f"必要なカラムが不足しています: {file_name}")
        return [], [], [], [], file_path, updChkCount

    seq_list, link_list, home_score_list, away_score_list = [], [], [], []

    for _, row in df.iterrows():
        if pd.isna(row["試合リンク文字列"]) or pd.isna(row["ホームスコア"]) or pd.isna(row["アウェースコア"]):
            continue
        seq_list.append(row["連番"])
        link_list.append(row["試合リンク文字列"])
        home_score_list.append(row["ホームスコア"])
        away_score_list.append(row["アウェースコア"])

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
        file_name = f"future_{seq}.xlsx"
        file_path = f"/Users/shiraishitoshio/bookmaker/{file_name}"

        if os.path.exists(file_path):
            max_seq = convertMaxSeq()
            output_csv_path = f"/Users/shiraishitoshio/bookmaker/future_{max_seq}.csv"
            excel_to_csv(file_path, output_csv_path)

        seq_list, link_list, home_score_list, away_score_list, file_path, updChkCount = loadFileAndGetLink(seq, updChkCount)

        if seq_list:
            print("対象データが見つかりました:", file_path)

        print(f"{updChkCount}/{len(seq_list_all)}番目")

        if updChkCount >= 50:
            break
