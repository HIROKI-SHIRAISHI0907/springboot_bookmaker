package dev.application.analyze.bm_m007_bm_m016;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CommonUtilクラス
 * @author shiraishitoshio
 *
 */
public class TimeRangeFeatureCommonUtil {

	/** updateMap */
	public static String UPDATEMAP = "updateMap";

	/** registerMap */
	public static String REGISTERMAP = "registerMap";

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
	private TimeRangeFeatureCommonUtil() {}

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

	/**
	 * チームデータ分割
	 * @param mapKey キー
	 * @return
	 */
	public static TimeRangeFeatureOutputDTO splitTeamKey(String mapKey) {
		String[] key = mapKey.split("-");
		TimeRangeFeatureOutputDTO dto = new TimeRangeFeatureOutputDTO();
		if (key.length >= 4) {
			dto.setCountry(key[0]);
			dto.setLeague(key[1]);
			dto.setHome(key[2]);
			dto.setAway(key[3]);
			if (key.length > 4)
				dto.setSeq1(key[4]);
		}
		return dto;
	}

}
