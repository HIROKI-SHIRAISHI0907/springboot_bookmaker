package dev.application.analyze.bm_m017_bm_m018;

import java.util.ArrayList;
import java.util.List;

import dev.common.constant.UniairConst;
import dev.common.util.DateUtil;


/**
 * within_data_scoredに登録するロジック
 * @author shiraishitoshio
 *
 */
public class WithInCounterDbInsert {

	/**
	 * 登録メソッド
	 * @param dataKeyList
	 * @param scoreList
	 * @param time
	 * @throws IllegalAccessException
	 * @throws IllegalArgumentException
	 */
	public void execute(List<String> dataKeyList,
			List<String> scoreList,
			String time,
			String hometime,
			String awaytime) throws IllegalArgumentException, IllegalAccessException {
		List<String> select17List = UniairColumnMapUtil.getKeyMap(UniairConst.BM_M017);
		String[] sel17List = new String[select17List.size()];
		for (int i = 0; i < select17List.size(); i++) {
			sel17List[i] = select17List.get(i);
		}

		List<String> select18List = UniairColumnMapUtil.getKeyMap(UniairConst.BM_M018);
		String[] sel18List = new String[select18List.size()];
		for (int i = 0; i < select18List.size(); i++) {
			sel18List[i] = select18List.get(i);
		}

		String country = dataKeyList.get(0);
		String league = dataKeyList.get(1);
		String home_score = scoreList.get(0);
		String away_score = scoreList.get(1);
		String sum_score = String.valueOf(Integer.parseInt(home_score) +
				Integer.parseInt(away_score));
		String time_area = time;
		String home_time_area = hometime;
		String away_time_area = awaytime;

		// すでに登録されたものがあるか
		String chkWhere = "country = '" + country + "' and "
				+ "league = '" + league + "' and "
				+ "sum_score_value = '" + sum_score + "'";

		List<List<String>> selectResultList = null;
		List<LeagueScoreTimeBandStatsEntity> chkConditionList = new ArrayList<LeagueScoreTimeBandStatsEntity>();
		SqlMainLogic select = new SqlMainLogic();
		try {
			selectResultList = select.executeSelect(null, UniairConst.BM_M017, sel17List,
					chkWhere, null, null);
			if (!selectResultList.isEmpty()) {
				// Entityにマッピングする
				for (List<String> list : selectResultList) {
					LeagueScoreTimeBandStatsEntity mapSelectDestination = mappingSelectEntity(list);
					chkConditionList.add(mapSelectDestination);
				}
			}
		} catch (Exception e) {
			System.err.println("within_data_scored_counter already select err searchData: " + e);
		}

		// 過去に登録されたtime_range_dataが全部含まれているか
		boolean chkFlg = false;
		String id = "";
		if (!chkConditionList.isEmpty()) {
			for (LeagueScoreTimeBandStatsEntity entity : chkConditionList) {
				String range_area = entity.getTimeRangeArea();
				if (time_area.contains(range_area)) {
					chkFlg = true;
					id = entity.getId();
				}
			}
		}

		if (!chkFlg) {
			String where = "country = '" + country + "' and "
					+ "league = '" + league + "' and "
					+ "sum_score_value = '" + sum_score + "' and "
					+ "time_range_area = '" + time_area + "'";

			selectResultList = null;
			List<LeagueScoreTimeBandStatsEntity> conditionList = new ArrayList<LeagueScoreTimeBandStatsEntity>();
			try {
				selectResultList = select.executeSelect(null, UniairConst.BM_M017, sel17List,
						where, null, "1");
				if (!selectResultList.isEmpty()) {
					// Entityにマッピングする
					for (List<String> list : selectResultList) {
						LeagueScoreTimeBandStatsEntity mapSelectDestination = mappingSelectEntity(list);
						conditionList.add(mapSelectDestination);
					}
				}
			} catch (Exception e) {
				System.err.println("within_data_scored_counter select err searchData: " + e);
			}

			// 空でない場合,更新
			if (!conditionList.isEmpty()) {
				UpdateWrapper updateWrapper = new UpdateWrapper();
				int tar = Integer.parseInt(conditionList.get(0).getTarget()) + 1;
				String target = String.valueOf(tar);
				StringBuilder sqlBuilder = new StringBuilder();
				// 更新日時も連結
				sqlBuilder.append(" target = '" + target + "' ,");
				sqlBuilder.append(" update_time = '" + DateUtil.getSysDate() + "'");
				// 決定した判定結果に更新
				updateWrapper.updateExecute(UniairConst.BM_M017, where,
						sqlBuilder.toString());
				// 新規登録
			} else {
				List<LeagueScoreTimeBandStatsEntity> insertList = new ArrayList<LeagueScoreTimeBandStatsEntity>();
				LeagueScoreTimeBandStatsEntity withinDataScoredCounterEntity = new LeagueScoreTimeBandStatsEntity();
				withinDataScoredCounterEntity.setCountry(country);
				withinDataScoredCounterEntity.setLeague(league);
				withinDataScoredCounterEntity.setSumScoreValue(sum_score);
				withinDataScoredCounterEntity.setTimeRangeArea(time);
				withinDataScoredCounterEntity.setTarget("1");
				insertList.add(withinDataScoredCounterEntity);
				CsvRegisterImpl csvRegisterImpl = new CsvRegisterImpl();
				try {
					csvRegisterImpl.executeInsert(UniairConst.BM_M017,
							insertList, 1, 1);
				} catch (Exception e) {
					System.err.println("within_data_scored_counter insert err execute: " + e);
				}
			}
			// 更新するのみ
		} else {
			String where = "id = '" + id + "'";
			UpdateWrapper updateWrapper = new UpdateWrapper();
			StringBuilder sqlBuilder = new StringBuilder();
			// 更新日時も連結
			sqlBuilder.append(" time_range_area = '" + time_area + "' ,");
			sqlBuilder.append(" update_time = '" + DateUtil.getSysDate() + "'");
			// 決定した判定結果に更新
			updateWrapper.updateExecute(UniairConst.BM_M017, where,
					sqlBuilder.toString());
		}

		// すでに登録されたものがあるか
		String chkDetailWhere = "country = '" + country + "' and "
				+ "league = '" + league + "' and "
				+ "home_score_value = '" + home_score + "' and "
				+ "away_score_value = '" + away_score + "'";

		selectResultList = null;
		List<LeagueScoreTimeBandStatsSplitScoreEntity> chkDetailConditionList = new ArrayList<LeagueScoreTimeBandStatsSplitScoreEntity>();
		try {
			selectResultList = select.executeSelect(null, UniairConst.BM_M018, sel18List,
					chkDetailWhere, null, null);
			if (!selectResultList.isEmpty()) {
				// Entityにマッピングする
				for (List<String> list : selectResultList) {
					LeagueScoreTimeBandStatsSplitScoreEntity mapSelectDestination = mappingSelectDetailEntity(list);
					chkDetailConditionList.add(mapSelectDestination);
				}
			}
		} catch (Exception e) {
			System.err.println("within_data_scored_counter_detail already select err searchData: " + e);
		}

		// 過去に登録されたtime_range_dataが全部含まれているか
		boolean chkDetailFlg = false;
		String idDetail = "";
		if (!chkConditionList.isEmpty()) {
			for (LeagueScoreTimeBandStatsSplitScoreEntity entity : chkDetailConditionList) {
				String home_range_area = entity.getHomeTimeRangeArea();
				String away_range_area = entity.getAwayTimeRangeArea();
				if (home_time_area.contains(home_range_area) ||
						away_time_area.contains(away_range_area)) {
					chkDetailFlg = true;
					idDetail = entity.getId();
				}
			}
		}

		if (!chkDetailFlg) {
			String where = "country = '" + country + "' and "
					+ "league = '" + league + "' and "
					+ "home_score_value = '" + home_score + "' and "
					+ "away_score_value = '" + away_score + "' and "
					+ "home_time_range_area = '" + home_time_area + "' and "
					+ "away_time_range_area = '" + away_time_area + "'";

			selectResultList = null;
			List<LeagueScoreTimeBandStatsSplitScoreEntity> condition2List = new ArrayList<LeagueScoreTimeBandStatsSplitScoreEntity>();
			try {
				selectResultList = select.executeSelect(null, UniairConst.BM_M018, sel18List,
						where, null, "1");
				if (!selectResultList.isEmpty()) {
					// Entityにマッピングする
					for (List<String> list : selectResultList) {
						LeagueScoreTimeBandStatsSplitScoreEntity mapSelectDestination = mappingSelectDetailEntity(list);
						condition2List.add(mapSelectDestination);
					}
				}
			} catch (Exception e) {
				System.err.println("within_data_scored_counter_detail select err searchData: " + e);
			}

			// 空でない場合,更新
			if (!condition2List.isEmpty()) {
				UpdateWrapper updateWrapper = new UpdateWrapper();
				int tar = Integer.parseInt(condition2List.get(0).getTarget()) + 1;
				String target = String.valueOf(tar);
				StringBuilder sqlBuilder = new StringBuilder();
				// 更新日時も連結
				sqlBuilder.append(" target = '" + target + "' ,");
				sqlBuilder.append(" update_time = '" + DateUtil.getSysDate() + "'");
				// 決定した判定結果に更新
				updateWrapper.updateExecute(UniairConst.BM_M018, where,
						sqlBuilder.toString());
				// 新規登録
			} else {
				List<LeagueScoreTimeBandStatsSplitScoreEntity> insertList = new ArrayList<LeagueScoreTimeBandStatsSplitScoreEntity>();
				LeagueScoreTimeBandStatsSplitScoreEntity withinDataScoredCounterDetailEntity = new LeagueScoreTimeBandStatsSplitScoreEntity();
				withinDataScoredCounterDetailEntity.setCountry(country);
				withinDataScoredCounterDetailEntity.setLeague(league);
				withinDataScoredCounterDetailEntity.setHomeScoreValue(home_score);
				withinDataScoredCounterDetailEntity.setAwayScoreValue(away_score);
				withinDataScoredCounterDetailEntity.setHomeTimeRangeArea(home_time_area);
				withinDataScoredCounterDetailEntity.setAwayTimeRangeArea(away_time_area);
				withinDataScoredCounterDetailEntity.setTarget("1");
				insertList.add(withinDataScoredCounterDetailEntity);
				try {
					CsvRegisterImpl csvRegisterImpl = new CsvRegisterImpl();
					csvRegisterImpl.executeInsert(UniairConst.BM_M018,
							insertList, 1, 1);
				} catch (Exception e) {
					System.err.println("within_data_scored_counter_detail insert err execute: " + e);
				}
			}
			// 更新するのみ
		} else {
			String where = "id = '" + idDetail + "'";
			UpdateWrapper updateWrapper = new UpdateWrapper();
			StringBuilder sqlBuilder = new StringBuilder();
			// 更新日時も連結
			sqlBuilder.append(" home_time_range_area = '" + home_time_area + "' ,");
			sqlBuilder.append(" away_time_range_area = '" + away_time_area + "' ,");
			sqlBuilder.append(" update_time = '" + DateUtil.getSysDate() + "'");
			// 決定した判定結果に更新
			updateWrapper.updateExecute(UniairConst.BM_M018, where,
					sqlBuilder.toString());
		}

		// type_of_country_league_data
		String[] selTypeOfCountryLeagueDataList = new String[4];
		selTypeOfCountryLeagueDataList[0] = "id";
		selTypeOfCountryLeagueDataList[1] = "country";
		selTypeOfCountryLeagueDataList[2] = "league";
		selTypeOfCountryLeagueDataList[3] = "csv_count";

		// WithInDataCounterを一括で更新
		String[] selWithInDataCounterList = new String[4];
		selWithInDataCounterList[0] = "id";
		selWithInDataCounterList[1] = "country";
		selWithInDataCounterList[2] = "league";
		selWithInDataCounterList[3] = "target";

		// search, ratio更新
		UpdateWrapper updateWrapper = new UpdateWrapper();
		for (int chk = 0; chk <= 1; chk++) {
			String tableId = (chk == 0) ? UniairConst.BM_M017 : UniairConst.BM_M018;

			BookDataSelectWrapper selectWrapper = new BookDataSelectWrapper();
			// レコード件数を取得する
			int cnt = -1;
			try {
				cnt = selectWrapper.executeCountSelect(UniairConst.BM_M006, null);
			} catch (Exception e) {
				return;
			}

			for (int seq = 1; seq <= cnt; seq++) {
				//System.out.println("seq: " + seq + ", tableId: " + tableId);
				// type_of_country_league_dataからcountry,league,csv_countを読み込み
				selectResultList = null;
				try {
					String where = "id = '" + seq + "'";
					selectResultList = select.executeSelect(null, UniairConst.BM_M006,
							selTypeOfCountryLeagueDataList, where, null, null);
				} catch (Exception e) {
					System.err.println("within_data_scored_counter select err: tableId = " + tableId
							+ ", id = " + seq + ", " + e);
				}
				int csv_count = Integer.parseInt(selectResultList.get(0).get(3));

				List<List<String>> selectResultSubList = null;
				try {
					String whereSub = "country = '" + selectResultList.get(0).get(1) + "' and "
							+ "league = '" + selectResultList.get(0).get(2) + "'";
					selectResultSubList = select.executeSelect(null, tableId,
							selWithInDataCounterList, whereSub, null, null);
				} catch (Exception e) {
					System.err.println("within_data_scored_counter select err: tableId = " + tableId
							+ " seq = " + seq + ", " + e);
				}

				// 取得したIdにのみ更新をかける
				if (!selectResultSubList.isEmpty()) {
					for (List<String> selsList : selectResultSubList) {
						int target = Integer.parseInt(selsList.get(3));
						String upWhere = "id = '" + selsList.get(0) + "'";
						String ratio = String.format("%.1f", ((float) target / csv_count) * 100) + "%";
						StringBuilder sqlBuilder = new StringBuilder();
						sqlBuilder.append(" search = '" + csv_count + "' ,");
						sqlBuilder.append(" ratio = '" + ratio + "' ,");
						sqlBuilder.append(" update_time = '" + DateUtil.getSysDate() + "'");
						// 決定した判定結果に更新
						updateWrapper.updateExecute(tableId, upWhere,
								sqlBuilder.toString());
					}
				}
			}
		}

	}

	/**
	 * ListからDTOにマッピングをかける
	 * @param mapSource list構造
	 * @return WithinDataScoredCounterEntity DTO
	 * @throws Exception
	 */
	private LeagueScoreTimeBandStatsEntity mappingSelectEntity(List<String> parts) throws Exception {
		LeagueScoreTimeBandStatsEntity mappingDto = new LeagueScoreTimeBandStatsEntity();
		mappingDto.setId(parts.get(0));
		mappingDto.setCountry(parts.get(1));
		mappingDto.setLeague(parts.get(2));
		mappingDto.setSumScoreValue(parts.get(3));
		mappingDto.setTimeRangeArea(parts.get(4));
		mappingDto.setTarget(parts.get(5));
		mappingDto.setSearch(parts.get(6));
		mappingDto.setRatio(parts.get(7));
		return mappingDto;
	}

	/**
	 * ListからDTOにマッピングをかける
	 * @param mapSource list構造
	 * @return WithinDataScoredCounterDetailEntity DTO
	 * @throws Exception
	 */
	private LeagueScoreTimeBandStatsSplitScoreEntity mappingSelectDetailEntity(List<String> parts) throws Exception {
		LeagueScoreTimeBandStatsSplitScoreEntity mappingDto = new LeagueScoreTimeBandStatsSplitScoreEntity();
		mappingDto.setId(parts.get(0));
		mappingDto.setCountry(parts.get(1));
		mappingDto.setLeague(parts.get(2));
		mappingDto.setHomeScoreValue(parts.get(3));
		mappingDto.setAwayScoreValue(parts.get(4));
		mappingDto.setHomeTimeRangeArea(parts.get(5));
		mappingDto.setAwayTimeRangeArea(parts.get(6));
		mappingDto.setTarget(parts.get(7));
		mappingDto.setSearch(parts.get(8));
		mappingDto.setRatio(parts.get(9));
		return mappingDto;
	}

}
