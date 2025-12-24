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

# --- メイン ---
def main():
   if os.path.exists(B001_JSON_PATH):
       os.remove(B001_JSON_PATH)

if __name__ == "__main__":
    asyncio.run(main())