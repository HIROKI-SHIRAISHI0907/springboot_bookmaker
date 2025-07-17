package dev.application.analyze.bm_m019_bm_m020;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import dev.application.analyze.bm_m097.ExistsUpdCsvInfo;
import dev.common.constant.UniairConst;
import dev.common.makecsv.MakeCsv;
import dev.common.util.ContainsCountryLeagueUtil;

/**
 * 分類モードにおける国,リーグのデータ群からCSVを作成する
 * @author shiraishitoshio
 *
 */
public class MakeClassifyScoreDataCsv {

	/**
	 * 実行メソッド
	 * @param updCsvFlg CSV更新フラグ
	 */
	public void execute(boolean updCsvFlg) {

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

		// data
		//List<String> selectList = UniairColumnMapUtil.getKeyMap(UniairConst.BM_M001);
		List<String> selectList = null;
		String[] selDetaList = new String[selectList.size()];
		for (int i = 0; i < selectList.size(); i++) {
			selDetaList[i] = selectList.get(i);
		}

		// type_of_country_league_data
		String[] selTypeOfCountryLeagueDataList = new String[2];
		selTypeOfCountryLeagueDataList[0] = "country";
		selTypeOfCountryLeagueDataList[1] = "league";

		// classify_result_data
		String[] selClassifyResultDataList = new String[3];
		selClassifyResultDataList[0] = "data_category";
		selClassifyResultDataList[1] = "home_team_name";
		selClassifyResultDataList[2] = "away_team_name";

		// classify_result_data_detail
		String[] selClassifyResultDataDetailList = new String[1];
		selClassifyResultDataDetailList[0] = "classify_mode";

		// データ取得
		for (int csv_id = 1; csv_id <= cnt; csv_id++) {
//			SqlMainLogic select = new SqlMainLogic();
//			List<List<String>> selectResultList = null;
//			try {
//				String where = "id = '" + csv_id + "'";
//				selectResultList = select.executeSelect(null, UniairConst.BM_M006,
//						selTypeOfCountryLeagueDataList, where, null, "1");
//			} catch (Exception e) {
//				System.err.println("within_data select err: tableId = BM_M006"
//						+ ", id = " + csv_id + ", " + e);
//			}

			List<List<String>> selectResultList = null;

			String country = selectResultList.get(0).get(0);
			String league = selectResultList.get(0).get(1);

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
			// classify_result_data_detailの分類モードを取得する
			for (List<String> classify_mode_list : selectResultList) {
				String classify_mode = classify_mode_list.get(0);

				// 更新CSVテーブルに存在したものは更新対象
				if (updCsvFlg) {
					List<List<String>> selectsList = ExistsUpdCsvInfo.chk(country, league,
							UniairConst.BM_M020, classify_mode);
					if (selectsList.isEmpty()) {
						continue;
					}
				}

				// classify_result_dataから分類モードと国及びカテゴリをキーにして国及びカテゴリ(ラウンド名)を取得する
				selectResultList = null;
				try {
					String where = "classify_mode = '" + classify_mode + "' and "
							+ "(data_category LIKE '" + country + "%' and "
							+ "data_category LIKE '%" + league + "%')";
					selectResultList = select.executeSelect(null, UniairConst.BM_M019,
							selClassifyResultDataList, where, null, null);
				} catch (Exception e) {
					System.err.println("within_data select err: tableId = BM_M019"
							+ ", classify_mode = '" + classify_mode + "' and "
							+ "(data_category LIKE '" + country + "%' and "
							+ "data_category LIKE '%" + league + "%'" + e);
				}
				// 国及びデータカテゴリで重複分を削除する
				// 重複を取り除く
				Set<List<String>> set = new LinkedHashSet<>();
				for (List<String> list : selectResultList) {
					set.add(list); // リスト内の要素をセットに追加
				}

				// セットの内容を新しいリストに変換
				List<List<String>> dataCategoryList = new ArrayList<>();
				for (List<String> item : set) {
					dataCategoryList.add(item);
				}

				// 国及びデータカテゴリをキーにしてdataから必要なデータを取得する
				List<List<String>> dataAllList = new ArrayList<List<String>>();
				for (List<String> list : dataCategoryList) {
					String data_category = list.get(0);
					String home_team = list.get(1);
					String away_team = list.get(2);

					System.out.println("MakeClassifyScoreDataCsv data_category: " + data_category +
							", classify_mode: " + classify_mode);

					selectResultList = null;
					try {
						String where = "data_category = '" + data_category + "' and "
								+ "home_team_name = '" + home_team + "' and "
								+ "away_team_name = '" + away_team + "'";
						String sort = "seq ASC";
						selectResultList = select.executeSelect(null, UniairConst.BM_M001,
								selDetaList, where, sort, null);
					} catch (Exception e) {
						System.err.println("within_data select err: tableId = BM_M001"
								+ ", data_category = " + data_category + ", err: " + e);
						continue;
					}

					List<String> timesList = new ArrayList<>();
					for (List<String> lists : selectResultList) {
						if (!timesList.contains(lists.get(3))) {
							timesList.add(lists.get(3));
							dataAllList.add(lists);
						}
					}
				}

				// データがない場合は出力しない
				if (dataAllList.isEmpty()) {
					continue;
				}

				// CSV作成
				MakeCsv makeCsv = new MakeCsv();
				if ("-1".equals(classify_mode)) {
					classify_mode = "16";
				}
				String file = "/Users/shiraishitoshio/bookmaker/python_analytics/classify_score/" + country + "-"
						+ league
						+ "-" + classify_mode + ".csv";
				makeCsv.execute(file, UniairConst.BM_M001, null, dataAllList);
			}
		}
	}
}
