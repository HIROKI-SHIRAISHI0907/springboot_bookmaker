package dev.application.analyze.bm_m007_bm_m016;

import java.util.Map;
import java.util.OptionalInt;

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
	public static final Map<Integer, String> TABLE_MAP = Map.of(
			1, "time_range_feature_main",
			2, "time_range_feature_scored",
			3, "time_range_feature_all_league",
			4, "within_data_20minutes_away_all_league",
			5, "within_data_20minutes_away_scored",
			6, "within_data_20minutes_same_scored",
			7, "within_data_45minutes_home_all_league",
			8, "within_data_45minutes_home_scored",
			9, "within_data_45minutes_away_all_league",
			10, "within_data_45minutes_away_scored");

	/** コンストラクタ生成禁止 */
	private TimeRangeFeatureCommonUtil() {
	}

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

	/**
	 * 末尾の「-<数値>」を OptionalInt で返す
	 * @param key
	 * @return
	 */
	public static OptionalInt extractTrailingSeq(String key) {
		int i = key.lastIndexOf('-');
		if (i < 0 || i == key.length() - 1)
			return OptionalInt.empty();
		String tail = key.substring(i + 1);
		try {
			return OptionalInt.of(Integer.parseInt(tail));
		} catch (NumberFormatException e) {
			return OptionalInt.empty();
		}
	}

}
