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
	    m.put(1,  "league_score_time_band_stats");
	    m.put(2,  "league_score_time_band_stats_split_score");
	    m.put(3,  "no_goal_match_stats");
	    m.put(4,  "within_data_20minutes_away_all_league");
	    m.put(5,  "within_data_20minutes_away_scored");
	    m.put(6,  "within_data_20minutes_same_scored");
	    m.put(7,  "within_data_45minutes_home_all_league");
	    m.put(8,  "within_data_45minutes_home_scored");
	    m.put(9,  "within_data_45minutes_away_all_league");
	    m.put(10, "within_data_45minutes_away_scored");
	    m.put(11, "match_classification_result");
	    m.put(12, "match_classification_result_count");
	    m.put(13, "score_based_feature_stats");
	    m.put(14, "team_monthly_score_summary");
	    m.put(15, "team_time_segment_shooting_stat");
	    m.put(16, "condition_result_data");
	    m.put(17, "calc_correlation");
	    m.put(18, "calc_correlation_ranking");
	    m.put(19, "stat_encryption");
	    m.put(20, "surface_overview");
	    m.put(21, "country_league_summary");
	    m.put(22, "within_data");
	    m.put(23, "each_team_score_based_feature_stats");
	    m.put(24, "score_based_feature_stats_history");
	    m.put(25, "each_team_score_based_feature_stats_history");
	    m.put(26, "data");
	    m.put(27, "stat_size_finalize_master");
	    m.put(28, "country_league_master");
	    m.put(29, "country_league_season_master");
	    m.put(30, "team_member_master");
	    m.put(31, "future_master");
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
