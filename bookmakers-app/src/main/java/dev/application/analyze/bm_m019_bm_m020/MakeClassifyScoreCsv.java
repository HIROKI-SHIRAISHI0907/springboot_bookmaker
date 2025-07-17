package dev.application.analyze.bm_m019_bm_m020;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.common.constant.ClassifyScoreAIConst;
import dev.common.makecsv.MakeCsv;
import dev.common.util.ContainsCountryLeagueUtil;

/**
 * 分類モードにおける集計数のCSVを作成する
 * @author shiraishitoshio
 *
 */
public class MakeClassifyScoreCsv {

	/**
	 * DB項目,テーブル名Mapping
	 */
	public static final Map<Integer, String> SCORE_CLASSIFICATION_ALL_MAP;
	static {
		HashMap<Integer, String> SCORE_CLASSIFICATION_MAP = new LinkedHashMap<>();
		SCORE_CLASSIFICATION_MAP.put(1, ClassifyScoreAIConst.HOME_SCORED_WITHIN_20_NEXT_SCORE_BEFORE_HALF);
		SCORE_CLASSIFICATION_MAP.put(4, ClassifyScoreAIConst.AWAY_SCORED_WITHIN_20_NEXT_SCORE_BEFORE_HALF);
		SCORE_CLASSIFICATION_MAP.put(7, ClassifyScoreAIConst.HOME_SCORED_BETWEEN_20_AND_45_NEXT_SCORE_BEFORE_HALF);
		SCORE_CLASSIFICATION_MAP.put(10, ClassifyScoreAIConst.AWAY_SCORED_BETWEEN_20_AND_45_NEXT_SCORE_BEFORE_HALF);
		SCORE_CLASSIFICATION_MAP.put(2, ClassifyScoreAIConst.HOME_SCORED_WITHIN_20_NEXT_SCORE_AFTER_HALF);
		SCORE_CLASSIFICATION_MAP.put(5, ClassifyScoreAIConst.AWAY_SCORED_WITHIN_20_NEXT_SCORE_AFTER_HALF);
		SCORE_CLASSIFICATION_MAP.put(8, ClassifyScoreAIConst.HOME_SCORED_BETWEEN_20_AND_45_NEXT_SCORE_AFTER_HALF);
		SCORE_CLASSIFICATION_MAP.put(11,
				ClassifyScoreAIConst.AWAY_SCORED_BETWEEN_20_AND_45_NEXT_SCORE_AFTER_HALF);
		SCORE_CLASSIFICATION_MAP.put(13, ClassifyScoreAIConst.NO_GOAL_FIRST_HALF_NEXT_HOME_SCORE);
		SCORE_CLASSIFICATION_MAP.put(14, ClassifyScoreAIConst.NO_GOAL_FIRST_HALF_NEXT_AWAY_SCORE);
		SCORE_CLASSIFICATION_MAP.put(3, ClassifyScoreAIConst.HOME_SCORED_WITHIN_20_NO_FURTHER_GOAL);
		SCORE_CLASSIFICATION_MAP.put(6, ClassifyScoreAIConst.AWAY_SCORED_WITHIN_20_NO_FURTHER_GOAL);
		SCORE_CLASSIFICATION_MAP.put(9, ClassifyScoreAIConst.HOME_SCORED_BETWEEN_20_AND_45_NO_FURTHER_GOAL);
		SCORE_CLASSIFICATION_MAP.put(12,
				ClassifyScoreAIConst.AWAY_SCORED_BETWEEN_20_AND_45_NO_FURTHER_GOAL);
		SCORE_CLASSIFICATION_MAP.put(15, ClassifyScoreAIConst.NO_GOAL);
		SCORE_CLASSIFICATION_MAP.put(-1, ClassifyScoreAIConst.EXCEPT_FOR_CONDITION);
		SCORE_CLASSIFICATION_ALL_MAP = Collections.unmodifiableMap(SCORE_CLASSIFICATION_MAP);
	}

	/**
	 * 実行
	 */
	public void execute() {

//		BookDataSelectWrapper selectWrapper = new BookDataSelectWrapper();
//		// レコード件数を取得する
//		int cnt = -1;
//		try {
//			cnt = selectWrapper.executeCountSelect(UniairConst.BM_M006, null);
//		} catch (Exception e) {
//			return;
//		}
//
//		if (cnt == -1) {
//			return;
//		}

		int cnt = 0;

		// ヘッダー作成
		List<String> headerList = new ArrayList<String>();
		Map<Integer, String> map = SCORE_CLASSIFICATION_ALL_MAP;
		Iterator<Integer> itMapKey = map.keySet().iterator();
		while (itMapKey.hasNext()) {
			Integer defaultKey = (Integer) itMapKey.next();
			headerList.add(String.valueOf(defaultKey));
		}

		// type_of_country_league_data
		String[] selTypeOfCountryLeagueDataList = new String[3];
		selTypeOfCountryLeagueDataList[0] = "country";
		selTypeOfCountryLeagueDataList[1] = "league";
		selTypeOfCountryLeagueDataList[2] = "csv_count";

		// classify_result_data_detail
		String[] selClassifyResultDataDetailList = new String[2];
		selClassifyResultDataDetailList[0] = "classify_mode";
		selClassifyResultDataDetailList[1] = "count";

		List<List<String>> countAllList = new ArrayList<List<String>>();

		// データ取得
		for (int csv_id = 1; csv_id <= cnt; csv_id++) {
//			SqlMainLogic select = new SqlMainLogic();
//			List<List<String>> selectResultList = null;
//			try {
//				String where = "id = '" + csv_id + "'";
//				selectResultList = select.executeSelect(null, UniairConst.BM_M006,
//						selTypeOfCountryLeagueDataList, where, null, "1");
//
//			} catch (Exception e) {
//				System.err.println("within_data select err: tableId = BM_M006"
//						+ ", id = " + csv_id + ", " + e);
//			}

			List<List<String>> selectResultList = null;

			String country = selectResultList.get(0).get(0);
			String league = selectResultList.get(0).get(1);
			String csv_count = selectResultList.get(0).get(2);

			if (!ContainsCountryLeagueUtil.containsCountryLeague(country, league)) {
				continue;
			}

			// 国,リーグをキーに分類モードと件数を取得
			selectResultList = null;
//			try {
//				String where = "country = '" + country + "' and league = '" + league + "'";
//				selectResultList = select.executeSelect(null, UniairConst.BM_M020,
//						selClassifyResultDataDetailList, where, null, null);
//			} catch (Exception e) {
//				System.err.println("within_data select err: tableId = BM_M020"
//						+ ", country = " + country + ", league = " + league + ", err: " + e);
//			}

			// 取得した分類モードと件数の組み合わせをSCORE_CLASSIFICATION_ALL_MAPのkeyに設定している分類モードの順番に並び替える
			List<List<String>> returnList = exchangeClassifyModeCount(selectResultList, headerList);

			// 件数のみ並び替えた順にデータリストに格納
			List<String> countList = new ArrayList<>();
			for (List<String> combination : returnList) {
				if (combination.get(1) != null && !combination.get(1).isBlank() && !"".equals(combination.get(1))) {
					countList.add(combination.get(1));
				}
			}

			// サイズが違う場合エラー
			if (headerList.size() != countList.size()) {
				System.err.println("headerList countList size err: "
						+ ", headerList size: " + headerList.size() + ", countList size: " + countList.size());
			}

			// 国とリーグを格納する用のリスト
			List<String> returnConvList = new ArrayList<>();
			returnConvList.add(country + ":" + league);
			returnConvList.addAll(countList);

			// 件数リストの最後にcsv_countを付与する
			returnConvList.add(csv_count);

			// 全体データリストにaddする
			countAllList.add(returnConvList);
		}

		// 国とリーグヘッダーを格納する用のリスト
		List<String> headerAllList = new ArrayList<String>();
		headerAllList.add("国:リーグ");
		headerAllList.addAll(headerList);

		// ヘッダーの最後にSUM文字を付与する
		headerAllList.add("SUM");

		// CSV作成
		MakeCsv makeCsv = new MakeCsv();
		String file = "/Users/shiraishitoshio/bookmaker/python_analytics/classify_score/scoretime_classify_count.csv";
		makeCsv.execute(file, null, headerAllList, countAllList);
	}

	/**
	 * 指定した分類モードの順に入れ替える
	 * @param list リスト(classify_mode, countの組み合わせの件数分のリスト)
	 * @param headerClassifyMode 分類モードをヘッダーにしたリスト
	 * @return
	 */
	private List<List<String>> exchangeClassifyModeCount(
			List<List<String>> list, List<String> headerClassifyMode) {
		List<List<String>> returnList = new ArrayList<List<String>>();
		// headerClassifyModeが全て回るまで繰り返す。
		for (String sortClassifyMode : headerClassifyMode) {
			for (List<String> combination : list) {
				// MAPに保存している分類モードが交換前リストに格納している分類モード,件数の組み合わせの分類モードと一致する場合
				// その組み合わせをreturnListにaddする。
				if (sortClassifyMode.equals(combination.get(0))) {
					returnList.add(combination);
					break;
				}
			}
		}
		return returnList;
	}
}
