package dev.application.analyze.common.util;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import dev.common.constant.UniairConst;


/**
 * トランケート管理Util
 * @author shiraishitoshio
 *
 */
public class TableTruncateUtil {

	/**
	 * DB項目,テーブル名Mapping
	 */
	public static final Map<String, String> TABLE_MAP;
	static {
		Map<String, String> ListMstDetailMap = new LinkedHashMap<>();
		ListMstDetailMap.put(UniairConst.BM_M006, "type_of_country_league_data");
		ListMstDetailMap.put(UniairConst.BM_M007, "within_data");
		ListMstDetailMap.put(UniairConst.BM_M008, "within_data_20minutes_home_scored");
		ListMstDetailMap.put(UniairConst.BM_M009, "within_data_20minutes_away_scored");
		ListMstDetailMap.put(UniairConst.BM_M010, "within_data_20minutes_same_scored");
		ListMstDetailMap.put(UniairConst.BM_M011, "within_data_45minutes_home_scored");
		ListMstDetailMap.put(UniairConst.BM_M012, "within_data_45minutes_away_scored");
		ListMstDetailMap.put(UniairConst.BM_M013, "within_data_20minutes_home_all_league");
		ListMstDetailMap.put(UniairConst.BM_M014, "within_data_20minutes_away_all_league");
		ListMstDetailMap.put(UniairConst.BM_M015, "within_data_45minutes_home_all_league");
		ListMstDetailMap.put(UniairConst.BM_M016, "within_data_45minutes_away_all_league");
		ListMstDetailMap.put(UniairConst.BM_M017, "within_data_scored_counter");
		ListMstDetailMap.put(UniairConst.BM_M018, "within_data_scored_counter_detail");
		ListMstDetailMap.put(UniairConst.BM_M019, "classify_result_data");
		ListMstDetailMap.put(UniairConst.BM_M020, "classify_result_data_detail");
		ListMstDetailMap.put(UniairConst.BM_M098, "file_chk_tmp");
		ListMstDetailMap.put(UniairConst.BM_M099, "file_chk");
		TABLE_MAP = Collections.unmodifiableMap(ListMstDetailMap);
	}

	/**
	 * コンストラクタ生成禁止
	 */
	private TableTruncateUtil() {}

	/**
	 * テーブルレコードをTruncateする
	 */
	public static void executeTruncate() {
		SqlMainLogic sqlMainLogic = new SqlMainLogic();
		for (Map.Entry<String, String> keySub : TABLE_MAP.entrySet()) {
			try {
				sqlMainLogic.executeTruncate(null, keySub.getKey());
			} catch (Exception e) {
				System.err.println("TableTruncateUtil truncate err: tableId = " + keySub.getKey() + ", " + e);
			}
		}
	}

}
