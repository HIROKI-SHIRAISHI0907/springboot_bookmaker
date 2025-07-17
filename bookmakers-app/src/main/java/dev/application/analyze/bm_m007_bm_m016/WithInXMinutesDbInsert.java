package dev.application.analyze.bm_m007_bm_m016;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.application.analyze.bm_m097.ExistsUpdCsvInfo;
import dev.application.analyze.common.util.ExecuteMainUtil;
import dev.common.constant.UniairConst;
import dev.common.entity.BookDataEntity;
import dev.common.exception.BusinessException;


/**
 * within_data_Xminutesに登録するロジック
 * @author shiraishitoshio
 *
 */
public class WithInXMinutesDbInsert {

	/**
	 * 除去リスト
	 */
	private static final List<String> EXCLUSIVE_LIST;;
	static {
		List<String> list = new ArrayList<>();
		list.add("seq");
		list.add("condition_result_data_seq_id");
		list.add("time");
		list.add("home_score");
		list.add("away_score");
		list.add("home_rank");
		list.add("away_rank");
		list.add("home_team_name");
		list.add("away_team_name");
		list.add("game_team_category");
		list.add("weather");
		list.add("temparature");
		list.add("humid");
		list.add("record_time");
		list.add("judge_member");
		list.add("home_manager");
		list.add("away_manager");
		list.add("home_formation");
		list.add("away_formation");
		list.add("studium");
		list.add("audience");
		list.add("capacity");
		list.add("home_max_getting_scorer");
		list.add("away_max_getting_scorer");
		list.add("home_max_getting_scorer_game_situation");
		list.add("away_max_getting_scorer_game_situation");
		list.add("notice_flg");
		list.add("goal_time");
		list.add("goal_team_member");
		list.add("judge");
		list.add("home_team_style");
		list.add("away_team_style");
		list.add("probablity");
		list.add("prediction_score_time");
		EXCLUSIVE_LIST = Collections.unmodifiableList(list);
	}

	/**
	 * 除去リスト
	 */
	private static final Map<String, String> CONVERT_MAP;
	static {
		Map<String, String> map = new LinkedHashMap<>();
		map.put("home_ball_possesion", "home_donation");
		map.put("away_ball_possesion", "away_donation");
		map.put("home_shoot_blocked", "home_block_shoot");
		map.put("away_shoot_blocked", "away_block_shoot");
		map.put("home_corner_kick", "home_corner");
		map.put("away_corner_kick", "away_corner");
		map.put("home_off_side", "home_offside");
		map.put("away_off_side", "away_offside");
		CONVERT_MAP = Collections.unmodifiableMap(map);
	}

	/**
	 * 分割リスト
	 */
	private static final List<String> SPLIT_LIST;
	static {
		List<String> list = new ArrayList<>();
		list.add("home_pass_count");
		list.add("away_pass_count");
		list.add("home_final_third_pass_count");
		list.add("away_final_third_pass_count");
		list.add("home_cross_count");
		list.add("away_cross_count");
		list.add("home_tackle_count");
		list.add("away_tackle_count");
		SPLIT_LIST = Collections.unmodifiableList(list);
	}

	/**
	 * リスト
	 */
	public static final List<String> WITHIN_CONTAINS_MAP;
	static {
		List<String> tableIdList = new ArrayList<String>();
		tableIdList.add(UniairConst.BM_M008);
		tableIdList.add(UniairConst.BM_M009);
		tableIdList.add(UniairConst.BM_M010);
		tableIdList.add(UniairConst.BM_M011);
		tableIdList.add(UniairConst.BM_M012);
		WITHIN_CONTAINS_MAP = Collections.unmodifiableList(tableIdList);
	}

	/**
	 * リスト
	 */
	public static final List<String> WITHIN_CONTAINS_MAP2;
	static {
		List<String> tableIdList = new ArrayList<String>();
		tableIdList.add(UniairConst.BM_M013);
		tableIdList.add(UniairConst.BM_M014);
		tableIdList.add(UniairConst.BM_M015);
		tableIdList.add(UniairConst.BM_M016);
		WITHIN_CONTAINS_MAP2 = Collections.unmodifiableList(tableIdList);
	}

	/**
	 * 論理リスト
	 */
	List<String> ronriList;

	/**
	 * 物理リスト
	 */
	List<String> butsuriList;

	/**
	 * コンストラクタ
	 */
	public WithInXMinutesDbInsert() {
		// 特徴量に当たる論理名を取得
		ronriList = UniairColumnMapUtil.getWhichKeyValueMap(UniairConst.BM_M001, "連番");
		// 特徴量に当たる物理名を取得
		butsuriList = UniairColumnMapUtil.getWhichKeyValueMap(UniairConst.BM_M001, "seq");
	}

	/**
	 * 登録メソッド
	 * @param conditionList 登録リスト
	 * @param search 探索数
	 * @param flg 登録テーブル判断フラグ
	 * @throws IllegalAccessException
	 * @throws IllegalArgumentException
	 */
	public void execute(List<BookDataEntity> conditionList, Integer search,
			String flg)
			throws IllegalArgumentException, IllegalAccessException {

		// 国,カテゴリを導出
		String data_category = conditionList.get(0).getGameTeamCategory();
		List<String> data_category_list = ExecuteMainUtil.getCountryLeagueByRegex(data_category);
		String country = data_category_list.get(0);
		String league = data_category_list.get(1);

		// 試合時間範囲導出
		String game_time = conditionList.get(0).getTime();
		String time_range = ExecuteMainUtil.classifyMatchTime(game_time);

		// 特徴量の選定(数値化できているものとできていないものを分類)
		Map<String, String> featureMap = new LinkedHashMap<String, String>();
		Field[] fields = conditionList.get(0).getClass().getDeclaredFields();
		for (Field field : fields) {
			field.setAccessible(true);
			// フィールド名とフィールド内の値取得
			String feature_name = field.getName();
			// フィールド名をスネーク方式に変更
			String snake = ExecuteMainUtil.convertToSnakeCase(feature_name);

			// 除去リストに入っていたらスキップ
			if (chkExclusive(snake)) {
				continue;
			}

			// 変換マップに入っていたら変換
			snake = chkConvert(snake);

			// 分割リストに入っていたら分割
			String ronri = ExecuteMainUtil.getFeatureRonriField(snake, ronriList, butsuriList);
			String feature_value = (String) field.get(conditionList.get(0));
			if ("".equals(feature_value) || feature_value == null) {
				continue;
			}

			if (chkSplit(snake)) {
				List<String> split_value = ExecuteMainUtil.splitGroup(feature_value);
				if (!"".equals(split_value.get(0))) {
					featureMap.put(ronri + "_割合", split_value.get(0));
				}
				if (!"".equals(split_value.get(1))) {
					featureMap.put(ronri + "_成功", split_value.get(1));
				}
				if (!"".equals(split_value.get(2))) {
					featureMap.put(ronri + "_試行", split_value.get(2));
				}
			} else {
				featureMap.put(ronri, feature_value);
			}
		}

		// 試合時間範囲とホームスコア,アウェースコアからテーブルIDを導出
		List<String> tableIdList = new ArrayList<String>();
		if ("20minutes_home".equals(flg)) {
			tableIdList.add(UniairConst.BM_M008);
			tableIdList.add(UniairConst.BM_M013);
		} else if ("20minutes_away".equals(flg)) {
			tableIdList.add(UniairConst.BM_M009);
			tableIdList.add(UniairConst.BM_M014);
		} else if ("20minutes_same".equals(flg)) {
			tableIdList.add(UniairConst.BM_M010);
		} else if ("45minutes_home".equals(flg)) {
			tableIdList.add(UniairConst.BM_M011);
			tableIdList.add(UniairConst.BM_M015);
		} else if ("45minutes_away".equals(flg)) {
			tableIdList.add(UniairConst.BM_M012);
			tableIdList.add(UniairConst.BM_M016);
		}

		for (String tableId : tableIdList) {
			// mapに保存した特徴量と値について,値から閾値を算出して該当数,探索数も導出
			List<String> selectList = UniairColumnMapUtil.getKeyMap(tableId);
			String[] selList = new String[selectList.size()];
			for (int i = 0; i < selectList.size(); i++) {
				selList[i] = selectList.get(i);
			}

			for (Map.Entry<String, String> feaEntry : featureMap.entrySet()) {
				String thresHold = ExecuteMainUtil.checkNumberTypeAndFloor(feaEntry.getValue());
				// テーブルIDを元にwhere句を返す
				String where = tableListContainsChkReturnWhere(tableId, country, league,
						time_range, feaEntry.getKey(), thresHold);

				List<List<String>> selectResultList = null;
				SqlMainLogic select = new SqlMainLogic();
				try {
					selectResultList = select.executeSelect(null, tableId, selList,
							where, null, "1");
				} catch (Exception e) {
					System.err.println("within_data_xminutes insert err: " + tableId + ", " + e);
				}

				// 空でない場合,更新
				if (!selectResultList.isEmpty()) {
					UpdateWrapper updateWrapper = new UpdateWrapper();
					List<String> list = selectResultList.get(0);
					String result = tableListContainsChk(tableId);
					int tar = 0;
					if ("within_each_league".equals(result)) {
						tar = Integer.parseInt(list.get(6)) + 1;
					} else if ("within_all_league".equals(result)) {
						tar = Integer.parseInt(list.get(4)) + 1;
					}
					String target = String.valueOf(tar);
					StringBuilder sqlBuilder = new StringBuilder();
					// 更新日時も連結
					sqlBuilder.append(" target = '" + target + "' ,");
					sqlBuilder.append(" update_time = '" + DateUtil.getSysDate() + "'");
					// 決定した判定結果に更新
					updateWrapper.updateExecute(tableId, where,
							sqlBuilder.toString());

					// upd_csv_infoに登録
					List<List<String>> resultList = ExistsUpdCsvInfo.chk(country, league,
							tableId, null);
					if (resultList.isEmpty()) {
						try {
							ExistsUpdCsvInfo.insert(country, league, tableId, "");
						} catch (Exception e) {
							System.err.println("ExistsUpdCsvInfo err: tableId = " + tableId + ", err: " + e);
						}
					}
					// 新規登録
				} else {
					// 特徴量と閾値を導出
					List<?> insertList = tableListContainsChkReturnEntity(tableId, country, league,
							time_range, feaEntry.getKey(), thresHold);
					CsvRegisterImpl csvRegisterImpl = new CsvRegisterImpl();
					try {
						csvRegisterImpl.executeInsert(tableId,
								insertList, 1, insertList.size());
					} catch (Exception e) {
						System.err.println("within_data insert err execute: " + e);
					}
				}
			}

		}
	}

	/**
	 * 除去リストに入っている項目かをチェック
	 * @param exclusive
	 * @return boolean
	 */
	private boolean chkExclusive(String exclusive) {
		return (EXCLUSIVE_LIST.contains(exclusive)) ? true : false;
	}

	/**
	 * 変換マップに入っている項目かをチェック
	 * @param convert
	 * @return boolean
	 */
	private String chkConvert(String convert) {
		return (CONVERT_MAP.containsKey(convert)) ? CONVERT_MAP.get(convert) : convert;
	}

	/**
	 * 分割リストに入っている項目かをチェック
	 * @param split
	 * @return boolean
	 */
	private boolean chkSplit(String split) {
		return (SPLIT_LIST.contains(split)) ? true : false;
	}

	/**
	 * テーブルIDを確認し検索用where句を確定させるメソッド
	 * @param tableId
	 * @param country
	 * @param league
	 * @param time_range
	 * @param feature
	 * @param thresHold
	 * @return
	 */
	private String tableListContainsChk(String tableId) {
		if (WITHIN_CONTAINS_MAP.contains(tableId)) {
			return "within_each_league";
		} else if (WITHIN_CONTAINS_MAP2.contains(tableId)) {
			return "within_all_league";
		}
		throw new BusinessException("", "", "", "存在しないテーブルID: tableListContainsChk");
	}

	/**
	 * テーブルIDを確認し検索用where句を確定させるメソッド
	 * @param tableId
	 * @param country
	 * @param league
	 * @param time_range
	 * @param feature
	 * @param thresHold
	 * @return
	 */
	private String tableListContainsChkReturnWhere(String tableId,
			String country, String league, String time_range,
			String feature, String thresHold) {
		if (WITHIN_CONTAINS_MAP.contains(tableId)) {
			return "country = '" + country + "' and "
					+ "category = '" + league + "' and "
					+ "time_range = '" + time_range + "' and "
					+ "feature = '" + feature + "' and "
					+ "threshold = '" + thresHold + "'";
		} else if (WITHIN_CONTAINS_MAP2.contains(tableId)) {
			return "time_range = '" + time_range + "' and "
					+ "feature = '" + feature + "' and "
					+ "threshold = '" + thresHold + "'";
		}
		throw new BusinessException("", "", "", "存在しないテーブルID: tableListContainsChkReturnWhere");
	}

	/**
	 * テーブルIDを確認し登録用Entityを確定させるメソッド
	 * @param tableId
	 * @param country
	 * @param league
	 * @param time_range
	 * @param feature
	 * @param thresHold
	 * @return
	 */
	private List<?> tableListContainsChkReturnEntity(String tableId,
			String country, String league, String time_range,
			String feature, String thresHold) {
		if (WITHIN_CONTAINS_MAP.contains(tableId)) {
			List<TimeRangeFeatureStatsEachLeagueEntity> insertList = new ArrayList<TimeRangeFeatureStatsEachLeagueEntity>();
			TimeRangeFeatureStatsEachLeagueEntity withinDataXMinutesEntity = new TimeRangeFeatureStatsEachLeagueEntity();
			withinDataXMinutesEntity.setCountry(country);
			withinDataXMinutesEntity.setLeague(league);
			withinDataXMinutesEntity.setTimeRange(time_range);
			withinDataXMinutesEntity.setFeature(feature);
			withinDataXMinutesEntity.setThresHold(thresHold);
			withinDataXMinutesEntity.setTarget("1");
			withinDataXMinutesEntity.setSearch("");
			withinDataXMinutesEntity.setRatio("");
			insertList.add(withinDataXMinutesEntity);
			return insertList;
		} else if (WITHIN_CONTAINS_MAP2.contains(tableId)) {
			List<TimeRangeFeatureStatsAllLeagueEntity> insertList = new ArrayList<TimeRangeFeatureStatsAllLeagueEntity>();
			TimeRangeFeatureStatsAllLeagueEntity withinDataXMinutesAllLeagueEntity = new TimeRangeFeatureStatsAllLeagueEntity();
			withinDataXMinutesAllLeagueEntity.setTimeRange(time_range);
			withinDataXMinutesAllLeagueEntity.setFeature(feature);
			withinDataXMinutesAllLeagueEntity.setThresHold(thresHold);
			withinDataXMinutesAllLeagueEntity.setTarget("1");
			withinDataXMinutesAllLeagueEntity.setSearch("");
			withinDataXMinutesAllLeagueEntity.setRatio("");
			insertList.add(withinDataXMinutesAllLeagueEntity);
			return insertList;
		}
		throw new BusinessException("", "", "", "存在しないテーブルID: tableListContainsChkReturnEntity");
	}
}
