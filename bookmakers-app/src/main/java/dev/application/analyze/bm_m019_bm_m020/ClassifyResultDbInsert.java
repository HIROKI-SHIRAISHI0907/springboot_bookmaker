package dev.application.analyze.bm_m019_bm_m020;

import java.util.ArrayList;
import java.util.List;

import dev.application.analyze.bm_m097.ExistsUpdCsvInfo;
import dev.application.analyze.common.util.ExecuteMainUtil;
import dev.common.constant.UniairConst;
import dev.common.util.DateUtil;


/**
 * classify_resultに登録するロジック
 * @author shiraishitoshio
 *
 */
public class ClassifyResultDbInsert {

	/**
	 * 登録メソッド
	 * @param insertEntities
	 * @param classify_explaination
	 * @param file_name
	 */
	public void execute(List<MatchClassificationResultEntity> insertEntities,
			String classify_explaination, String file_name) {

		CsvRegisterImpl csvRegisterImpl = new CsvRegisterImpl();
		try {
			csvRegisterImpl.executeInsert(UniairConst.BM_M019,
					insertEntities, 1, insertEntities.size());
		} catch (Exception e) {
			System.err.println("classify_result_deta insert err execute: " + e);
		}

		List<String> data_key_list = ExecuteMainUtil.getCountryLeagueByRegex(insertEntities.get(0).getDataCategory());
		String country = data_key_list.get(0);
		String league = data_key_list.get(1);

		String searchWhere = "country = '" + country + "' and "
				+ "league = '" + league + "'";

		List<String> select20List = UniairColumnMapUtil.getKeyMap(UniairConst.BM_M020);
		String[] sel20List = new String[select20List.size()];
		for (int i = 0; i < select20List.size(); i++) {
			sel20List[i] = select20List.get(i);
		}

		List<List<String>> selectResultList = null;
		SqlMainLogic select = new SqlMainLogic();
		try {
			selectResultList = select.executeSelect(null, UniairConst.BM_M020, sel20List,
					searchWhere, null, "1");
		} catch (Exception e) {
			System.err.println("classify_result_detail_deta select err searchData: " + e);
		}

		if (selectResultList.isEmpty()) {
			MatchClassificationResultCountEntity classifyResultDataDetailEntity = new MatchClassificationResultCountEntity();
			classifyResultDataDetailEntity.setCountry(country);
			classifyResultDataDetailEntity.setLeague(league);
			classifyResultDataDetailEntity.setCount("0");
			for (int classify = 1; classify <= ClassifyScoreAISubLogic.SCORE_CLASSIFICATION_ALL_MAP.size()
					- 1; classify++) {
				List<MatchClassificationResultCountEntity> detailEntities = new ArrayList<MatchClassificationResultCountEntity>();
				classifyResultDataDetailEntity.setClassifyMode(String.valueOf(classify));
				detailEntities.add(classifyResultDataDetailEntity);
				try {
					csvRegisterImpl.executeInsert(UniairConst.BM_M020,
							detailEntities, 1, 1);
				} catch (Exception e) {
					System.err.println("classify_result_detail_deta insert err execute: " + e);
				}
			}
			// 条件対象外用
			List<MatchClassificationResultCountEntity> detailEntities = new ArrayList<MatchClassificationResultCountEntity>();
			classifyResultDataDetailEntity.setClassifyMode(String.valueOf(-1));
			detailEntities.add(classifyResultDataDetailEntity);
			try {
				csvRegisterImpl.executeInsert(UniairConst.BM_M020,
						detailEntities, 1, 1);
			} catch (Exception e) {
				System.err.println("classify_result_detail_deta insert err execute: " + e);
			}
		}

		// 更新
		String classify_mode = insertEntities.get(0).getClassifyMode();
		if ("".equals(classify_mode)) {
			System.err.println("classify_mode empty: classify_mode = " + classify_mode);
			return;
		}

		System.out.println("classify_mode: " + classify_mode);

		String where = "country = '" + country + "' and "
				+ "league = '" + league + "' and "
				+ "classify_mode = '" + classify_mode + "'";

		selectResultList = null;
		try {
			selectResultList = select.executeSelect(null, UniairConst.BM_M020, sel20List,
					where, null, "1");
		} catch (Exception e) {
			System.err.println("classify_result_deta select err update searchData: " + e);
		}

		// 空でない場合,更新
		if (!selectResultList.isEmpty()) {
			UpdateWrapper updateWrapper = new UpdateWrapper();
			int count = Integer.parseInt(selectResultList.get(0).get(4)) + 1;
			StringBuilder sqlBuilder = new StringBuilder();
			// file_nameが空で無い場合は, カンマ連結で登録
			if (!"".equals(file_name)) {
				String befUpdFile = selectResultList.get(0).get(5);
				if (befUpdFile != null) {
					befUpdFile += ("," + file_name);
					sqlBuilder.append(" remarks = '" + befUpdFile + "' ,");
				} else {
					sqlBuilder.append(" remarks = '" + file_name + "' ,");
				}
			} else {
				sqlBuilder.append(" remarks = '" + file_name + "' ,");
			}
			// 更新日時も連結
			sqlBuilder.append(" count = '" + count + "' ,");
			sqlBuilder.append(" update_time = '" + DateUtil.getSysDate() + "'");
			// 決定した判定結果に更新
			updateWrapper.updateExecute(UniairConst.BM_M020, where,
					sqlBuilder.toString());

			// upd_csv_infoに登録
			List<List<String>> resultList = ExistsUpdCsvInfo.chk(country, league,
					UniairConst.BM_M020, classify_mode);
			if (resultList.isEmpty()) {
				try {
					ExistsUpdCsvInfo.insert(country, league, UniairConst.BM_M020, classify_mode);
				} catch (Exception e) {
					System.err.println("ExistsUpdCsvInfo err: tableId = BM_M020, err: " + e);
				}
			}
		} else {
			System.err.println("更新に失敗しました: where = " + where);
		}

	}

}
