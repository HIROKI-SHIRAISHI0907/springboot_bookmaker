package dev.application.analyze.bm_m019_bm_m020;

import java.util.List;

import dev.application.analyze.bm_m097.ExistsUpdCsvInfo;
import dev.common.constant.UniairConst;
import dev.common.makeall.MakeAnythingFile;
import dev.common.util.ContainsCountryLeagueUtil;


/**
 * classify_result_data_detailテーブルcsv作成
 * @author shiraishitoshio
 *
 */
public class ClassifyScoreMakeCsvHelperLogic {

	/**
	 * 実行メソッド
	 * @param updCsvFlg CSV更新フラグ
	 * @throws Exception
	 */
	public void execute(boolean updCsvFlg) throws Exception {

		// レコード件数を取得する
		BookDataSelectWrapper selectWrapper = new BookDataSelectWrapper();
		int cnt = -1;
		try {
			cnt = selectWrapper.executeCountSelect(UniairConst.BM_M006, null);
		} catch (Exception e) {
			return;
		}

		List<String> select6List = UniairColumnMapUtil.getKeyMap(UniairConst.BM_M006);
		String[] sel6List = new String[select6List.size()];
		for (int i = 0; i < select6List.size(); i++) {
			sel6List[i] = select6List.get(i);
		}

		List<String> select20List = UniairColumnMapUtil.getKeyMap(UniairConst.BM_M020);
		String[] sel20List = new String[select20List.size() - 2];
		for (int i = 1; i < select20List.size() - 1; i++) {
			sel20List[i - 1] = select20List.get(i);
		}

		int file_id = 1;
		for (int id = 1; id <= cnt; id++) {
			String searchWhere = "id = '" + id + "'";

			List<List<String>> selectResultList = null;
			SqlMainLogic select = new SqlMainLogic();
			try {
				selectResultList = select.executeSelect(null, UniairConst.BM_M006, sel6List,
						searchWhere, null, "1");
			} catch (Exception e) {
				System.err.println("err searchData: " + e);
			}

			String country = selectResultList.get(0).get(1);
			String category = selectResultList.get(0).get(2);

			if (!ContainsCountryLeagueUtil.containsCountryLeague(country, category)) {
				continue;
			}

			// 更新CSVテーブルに存在したものは更新対象
			if (updCsvFlg) {
				List<List<String>> selectsList = ExistsUpdCsvInfo.chk(country, category,
						UniairConst.BM_M020, null);
				if (selectsList.isEmpty()) {
					continue;
				}
			}

			System.out.println("ClassifyScoreMakeCsvHelperLogic country: " + country + ", category: " + category +
					", id: " + id);

			String csvWhere = "country = '" + country + "' and "
					+ "league = '" + category + "'";

			selectResultList = null;
			try {
				selectResultList = select.executeSelect(null, UniairConst.BM_M020, sel20List,
						csvWhere, null, null);
			} catch (Exception e) {
				System.err.println("classify_result_detail_deta select err searchData: " + e);
			}

			String file_name = "classify_" + file_id;

			if (!selectResultList.isEmpty()) {
				MakeAnythingFile makeAnythingFile = new MakeAnythingFile();
				makeAnythingFile.execute(UniairConst.BM_M020, ".csv", file_name, selectResultList);
				file_id++;
			}
		}

	}

}
