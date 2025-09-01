package dev.application.main.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * TruncateTestクラス
 * @author shiraishitoshio
 *
 */
public class TruncateUtil {

	/** テーブル情報Map */
	public static final Map<Integer, String> TABLE_MAP = createTableMap();

	private static Map<Integer, String> createTableMap() {
	    Map<Integer, String> m = new LinkedHashMap<>();
	    m.put(1,  "within_data_scored_counter");
	    m.put(2,  "within_data_scored_counter_detail");
	    m.put(3,  "zero_score_data");
	    m.put(4,  "within_data_20minutes_away_all_league");
	    m.put(5,  "within_data_20minutes_away_scored");
	    m.put(6,  "within_data_20minutes_same_scored");
	    m.put(7,  "within_data_45minutes_home_all_league");
	    m.put(8,  "within_data_45minutes_home_scored");
	    m.put(9,  "within_data_45minutes_away_all_league");
	    m.put(10, "within_data_45minutes_away_scored");
	    m.put(11, "average_feature_data");
	    m.put(12, "average_statistics_data");
	    m.put(13, "average_statistics_data_detail");
	    m.put(14, "classify_result_data");
	    m.put(15, "classify_result_data_detail");
	    m.put(16, "collect_range_score");
	    m.put(17, "collect_scoring_standard_data");
	    m.put(18, "condition_result_data");
	    m.put(19, "correlation_data");
	    m.put(20, "correlation_ranking_data");
	    m.put(21, "game_statistics_detail_data");
	    m.put(22, "scoring_playstyle_past_data");
	    m.put(23, "stat_encryption");
	    m.put(24, "surface_overview");
	    m.put(25, "type_of_country_league_data");
	    m.put(26, "within_data");
	    m.put(27, "within_data_20minutes_home_all_league");
	    m.put(28, "within_data_20minutes_home_scored");
	    return Collections.unmodifiableMap(m);
	}

	/** コンストラクタ生成禁止 */
	private TruncateUtil() {
	}

	/**
	 * テーブル情報マップを取得
	 * @param key キー
	 * @return テーブル情報のマップ
	 */
	public static String getTableMap(int key) {
		if (TABLE_MAP.containsKey(key)) {
			return TABLE_MAP.get(key);
		}
		return null;
	}
}
