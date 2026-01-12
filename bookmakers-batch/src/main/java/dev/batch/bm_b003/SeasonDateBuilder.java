package dev.batch.bm_b003;

import java.util.ArrayList;
import java.util.List;

/**
 * 日付生成ビルダー
 * @author shiraishitoshio
 *
 */
public class SeasonDateBuilder {

	/** コンストラクタ生成禁止 */
	private SeasonDateBuilder() {}

	/**
	 * 年変換
	 * @param year
	 * @return
	 */
	public static String[] convertSeasonYear(String year) {
		// 戻り値[0] = 開始年, [1] = 終了年
		if (year == null || year.isEmpty()) {
			throw new IllegalArgumentException("seasonYear is null or empty");
		}

		if (year.length() == 9 && year.contains("/")) {
			// 2025/2026
			String[] years = year.split("/");
			return new String[] { years[0], years[1] };
		}

		if (year.length() == 4) {
			// 2025
			return new String[] { year, year };
		}

		throw new IllegalArgumentException("Invalid seasonYear format: " + year);
	}

	/**
	 * 日付構築
	 * @param year
	 * @param seasonDate
	 * @return
	 */
	public static String buildDate(String year, String seasonDate) {
	    if (seasonDate == null || seasonDate.isEmpty()) {
	        return null;
	    }

	    // 例: "4.10." , ".24.08" , "24.08" などから「数値」を抜き出す
	    String[] raw = seasonDate.split("\\.");
	    List<String> nums = new ArrayList<>();
	    for (String s : raw) {
	        if (s != null) {
	            s = s.trim();
	            if (!s.isEmpty()) nums.add(s);
	        }
	    }

	    if (nums.size() < 2) {
	        throw new IllegalArgumentException("Invalid seasonDate format: " + seasonDate);
	    }

	    // まずは「日.月」 or 「月.日」判定
	    int a = Integer.parseInt(nums.get(0));
	    int b = Integer.parseInt(nums.get(1));

	    int day;
	    int month;

	    if (a > 12) {
	        // a が 13以上なら a=日
	        day = a;
	        month = b;
	    } else if (b > 12) {
	        // b が 13以上なら b=日
	        day = b;
	        month = a;
	    } else {
	        // 両方12以下 → 日.月 とみなす（CSV実データ仕様）
	        day = a;
	        month = b;
	    }

	    String monthStr = String.format("%02d", month);
	    String dayStr   = String.format("%02d", day);

	    return year + "-" + monthStr + "-" + dayStr;
	}

}
