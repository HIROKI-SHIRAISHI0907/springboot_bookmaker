package dev.application.analyze.bm_m007_bm_m016;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * テーブルマップUtilクラス
 * @author shiraishitoshio
 *
 */
public class TimeRangeFeatureTableMapUtil {

	/** テーブル情報Map */
	private static Map<Integer, String> TABLE_MAP;
	{
		Map<Integer, String> tableMap = new LinkedHashMap<Integer, String>();
		tableMap.put(1, "within_data");
		tableMap.put(2, "within_data_20minutes_home_all_league");
		tableMap.put(3, "within_data_20minutes_home_scored");
		tableMap.put(4, "within_data_20minutes_away_all_league");
		tableMap.put(5, "within_data_20minutes_away_scored");
		tableMap.put(6, "within_data_20minutes_same_scored");
		tableMap.put(7, "within_data_45minutes_home_all_league");
		tableMap.put(8, "within_data_45minutes_home_scored");
		tableMap.put(9, "within_data_45minutes_away_all_league");
		tableMap.put(10, "within_data_45minutes_away_scored");
		TABLE_MAP = Collections.unmodifiableMap(tableMap);
	}

	/** コンストラクタ生成禁止 */
	private TimeRangeFeatureTableMapUtil() {}

	/**
	 * テーブル情報マップを取得
	 * @param key キー
	 * @return テーブル情報のマップ
	 */
	public static String getTableMap(int key) {
		if (!TABLE_MAP.containsKey(key)) {
			return TABLE_MAP.get(key);
		}
		return null;
	}

}
