package dev.application.common.logic;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import dev.application.analyze.bm_m026.AverageStatisticsDetailEntity;
import dev.application.analyze.bm_m027.AverageStatisticsCsvTmpDataEntity;
import dev.application.analyze.common.entity.ThresHoldEntity;
import dev.application.analyze.common.util.ExecuteMainUtil;
import dev.common.constant.UniairConst;
import dev.common.exception.SystemException;
import dev.common.util.DateUtil;

/**
 * プレースタイル等を決定するための閾値決定ロジック
 * @author shiraishitoshio
 *
 */
public class DecidePlayStyleAndThresHoldLogic {

	/**
	 * 件数
	 */
	private static final int COUNTER = 52;

	/**
	 * NOSCORE(0-0)
	 */
	private static final String NO_SCORE = "0-0";

	/**
	 * 全体データ
	 */
	private static final String ALL_DATA = "ALL";

	/**
	 * 前半データ
	 */
	private static final String FIRST_DATA = "1st";

	/**
	 * 後半データ
	 */
	private static final String SECOND_DATA = "2nd";

	/**
	 * フラグ(スコア単位)
	 */
	private static final String EACH_SCORE = "1";

	/**
	 * フラグ(全データ単位)
	 */
	private static final String ALL_SCORE = "2";

	/**
	 * フラグ(前半,後半単位)
	 */
	private static final String HALF_SCORE = "3";

	/**
	 * フラグ(前半)
	 */
	private static final String FIRST_HALF_SCORE = "4";

	/**
	 * フラグ(後半)
	 */
	private static final String SECOND_HALF_SCORE = "5";

	/**
	 * 実行
	 * @param entityList
	 * @param file
	 * @throws IllegalAccessException
	 * @throws IllegalArgumentException
	 */
	public void execute(List<ThresHoldEntity> entityList, String file)
			throws IllegalArgumentException, IllegalAccessException {

		// 取得したデータリストについて最終スコアを確認する
		ThresHoldEntity returnMaxEntity = ExecuteMainUtil.getMaxSeqEntities(entityList);
		// 国,リーグ
		String[] data_category = ExecuteMainUtil.splitLeagueInfo(returnMaxEntity.getDataCategory());
		String country = data_category[0];
		String league = data_category[1];

		// 欠け値が存在するのを防ぐため最後のデータを取得する
		ThresHoldEntity allEntityList = entityList.get(entityList.size() - 1);

		String situation = (Integer.parseInt(returnMaxEntity.getHomeScore()) == 0
				&& Integer.parseInt(returnMaxEntity.getAwayScore()) == 0) ? AverageStatisticsSituationConst.NOSCORE
						: AverageStatisticsSituationConst.SCORE;

		// 各スコアごとに最小値,最大値,平均,標準偏差導出
		int maxHomeScore = Integer.parseInt(returnMaxEntity.getHomeScore());
		int maxAwayScore = Integer.parseInt(returnMaxEntity.getAwayScore());
		// 最大の値を持つスコアになるまでその値同士で平均と標準偏差を出し続ける
		for (int homeScore = 0; homeScore <= maxHomeScore; homeScore++) {
			for (int awayScore = 0; awayScore <= maxAwayScore; awayScore++) {
				// indexloopに書かれているスコア状態(得点がある試合は0-0を除く)単位で平均と標準偏差を導出する
				if (AverageStatisticsSituationConst.SCORE.equals(situation) &&
						homeScore == 0 && awayScore == 0) {
					continue;
				}

				// 存在しないスコアはスキップ
				if (!existsScore(homeScore, awayScore, entityList)) {
					continue;
				}

				// 連結スコア
				String connectScore = String.valueOf(homeScore) + "-" + String.valueOf(awayScore);

				System.out.println("score: " + connectScore);

				// 共通ロジック
				//commonCountryLeagueLogic(country, league, connectScore, situation,
				//		entityList, allEntityList, file, EACH_SCORE, null);

				// チーム単位での共通ロジック
				for (int i = 1; i <= 2; i++) {
					String team = "";
					if (i == 1) {
						team = allEntityList.getHomeTeamName();
					} else {
						team = allEntityList.getAwayTeamName();
					}
					commonCountryLeagueTeamLogic(country, league, team, connectScore, situation,
							entityList, allEntityList, file, EACH_SCORE, null);

					// csv保管データ
					registerCsvTmpData(country, league, team, connectScore);
				}
			}
		}

		System.out.println("score: ALLSCORE");

		// 得点無得点全体データ
		//commonCountryLeagueLogic(country, league, ALL_DATA, "",
		//		entityList, allEntityList, file, ALL_SCORE, null);

		// チーム単位での共通ロジック
		for (int i = 1; i <= 2; i++) {
			String team = "";
			if (i == 1) {
				team = allEntityList.getHomeTeamName();
			} else {
				team = allEntityList.getAwayTeamName();
			}
			commonCountryLeagueTeamLogic(country, league, team, ALL_DATA, "",
					entityList, allEntityList, file, ALL_SCORE, null);
		}

		System.out.println("score: NOSCORE");

		// 無得点(0-0)のみの全体データ
		if (AverageStatisticsSituationConst.NOSCORE.equals(situation)) {
			commonCountryLeagueLogic(country, league, NO_SCORE, situation,
					entityList, allEntityList, file, EACH_SCORE, null);

			// チーム単位での共通ロジック
			for (int i = 1; i <= 2; i++) {
				String team = "";
				if (i == 1) {
					team = allEntityList.getHomeTeamName();
				} else {
					team = allEntityList.getAwayTeamName();
				}
				commonCountryLeagueTeamLogic(country, league, team, NO_SCORE, situation,
						entityList, allEntityList, file, EACH_SCORE, null);

				// csv保管データ
				registerCsvTmpData(country, league, team, "0-0");
			}
		}

		// 前後半データ(得点時と無得点で集計)
		for (int i = 1; i <= 2; i++) {
			String halfFlg = (i == 1) ? FIRST_HALF_SCORE : SECOND_HALF_SCORE;
			String flgScore = (i == 1) ? FIRST_DATA : SECOND_DATA;
			if (AverageStatisticsSituationConst.NOSCORE.equals(situation)) {
				//commonCountryLeagueLogic(country, league, flgScore, situation,
				//		entityList, allEntityList, file, HALF_SCORE, halfFlg);

				// チーム単位での共通ロジック
				for (int j = 1; j <= 2; j++) {
					String team = "";
					if (j == 1) {
						team = allEntityList.getHomeTeamName();
					} else {
						team = allEntityList.getAwayTeamName();
					}
					commonCountryLeagueTeamLogic(country, league, team, flgScore, situation,
							entityList, allEntityList, file, HALF_SCORE, null);
				}

			} else {
				//commonCountryLeagueLogic(country, league, flgScore, situation,
				//		entityList, allEntityList, file, HALF_SCORE, halfFlg);

				// チーム単位での共通ロジック
				for (int j = 1; j <= 2; j++) {
					String team = "";
					if (j == 1) {
						team = allEntityList.getHomeTeamName();
					} else {
						team = allEntityList.getAwayTeamName();
					}
					commonCountryLeagueTeamLogic(country, league, team, flgScore, situation,
							entityList, allEntityList, file, HALF_SCORE, null);
				}
			}
		}

	}

	/**
	 * 共通ロジック
	 * @param country 国
	 * @param league リーグ
	 * @param connectScore 連結スコア
	 * @param situation 状況
	 * @param entityList entityList
	 * @param allEntityList allEntityList
	 * @param file ファイル名
	 * @param flg 導出フラグ
	 * @param halfFlg ハーフタイムフラグ
	 * @throws IllegalArgumentException
	 * @throws IllegalAccessException
	 */
	private void commonCountryLeagueLogic(String country, String league, String connectScore, String situation,
			List<ThresHoldEntity> entityList, ThresHoldEntity allEntityList, String file,
			String flg, String halfFlg)
			throws IllegalArgumentException, IllegalAccessException {
		// DBに登録済みか
		AverageStatisticsOutputDTO averageStatisticsOutputDTO = getTeamStaticsData(country, league,
				null, connectScore, UniairConst.BM_M023);
		// 更新フラグ取得,更新通番取得
		boolean updAllFlg = averageStatisticsOutputDTO.isUpdFlg();
		String updId = averageStatisticsOutputDTO.getUpdId();
		List<List<String>> allData = averageStatisticsOutputDTO.getSelectList();

		// 既存のリスト
		List<ThresHoldEntity> filteredList = null;
		if (EACH_SCORE.equals(flg)) {
			filteredList = entityList.stream()
					.filter(entity -> connectScore.equals(entity.getHomeScore() + "-"
							+ entity.getAwayScore()))
					.collect(Collectors.toList());
		} else if (ALL_SCORE.equals(flg)) {
			filteredList = entityList;
		} else if (HALF_SCORE.equals(flg)) {
			// ハーフタイムの通番を特定
			String halfTimeSeq = findHalfTimeSeq(entityList);
			// ハーフタイム前の試合時間のデータをフィルタリング（通番が半分より小さいもの）
			if (FIRST_HALF_SCORE.equals(halfFlg)) {
				filteredList = entityList.stream()
						.filter(entity -> entity.getSeq().compareTo(halfTimeSeq) < 0) // 通番がハーフタイムより前
						.collect(Collectors.toList());
			} else if (SECOND_HALF_SCORE.equals(halfFlg)) {
				filteredList = entityList.stream()
						.filter(entity -> entity.getSeq().compareTo(halfTimeSeq) > 0) // 通番がハーフタイムより後
						.collect(Collectors.toList());
			}
		}

		// 空の場合終了
		if (filteredList == null || filteredList.isEmpty()) {
			return;
		}

		// 比較メソッドを呼び出し最大値,最小値を導出する。
		List<String> minData = new ArrayList<>();
		List<String> maxData = new ArrayList<>();
		List<FieldMapping> mappings = StatMapping.createFieldMappings();
		for (ThresHoldEntity entity : filteredList) {
			if (updAllFlg) {
				minData = getSplitStringData(allData, 0);
				maxData = getSplitStringData(allData, 1);
			} else {
				// 最小値,最大値,平均,標準偏差を求めるフィールド数*2のリストを保持する
				for (int i = 0; i < COUNTER; i++) {
					minData.add("5000.0");
				}
				for (int i = 0; i < COUNTER; i++) {
					maxData.add("-5000.0");
				}
			}

			for (FieldMapping mapping : mappings) {
				int index = mapping.getIndex();
				String currentMin = minData.get(index);
				String currentMax = maxData.get(index);
				String value = mapping.getGetter().apply(entity);
				minData.set(index, compareMin(currentMin, value));
				maxData.set(index, compareMax(currentMax, value));
				//				System.out.println("entityNum: " + entityNum + ", index: " + index);
			}
		}

		List<String> aveData = new ArrayList<>();
		List<String> sigmaData = new ArrayList<>();
		List<Integer> counter = new ArrayList<>();
		if (updAllFlg) {
			aveData = getSplitStringData(allData, 2);
			sigmaData = getSplitStringData(allData, 3);
			counter = getSplitIntegerData(allData);
			// 平均*件数を計算しておく
			aveData = calcAveSum(aveData, counter);
		} else {
			// 平均,標準偏差,件数の初期化
			int i = 0;
			Field[] fields = allEntityList.getClass().getDeclaredFields();
			for (Field field : fields) {
				field.setAccessible(true);
				if ((i >= 0 && i <= 9) ||
						i >= 62) {
					i++;
					continue;
				} else {
					String feature_value = (String) field.get(allEntityList);
					if (feature_value.contains("/")) {
						aveData.add("0.0% (0.0/0.0)");
					} else if (feature_value.contains("%")) {
						aveData.add("0.00%");
					} else {
						aveData.add("0.0");
					}
					i++;
				}
			}
			int j = 0;
			for (Field field : fields) {
				field.setAccessible(true);
				if ((j >= 0 && j <= 9) ||
						j >= 62) {
					j++;
					continue;
				} else {
					String feature_value = (String) field.get(allEntityList);
					if (feature_value.contains("/")) {
						sigmaData.add("0.00% (0.0/0.0)");
					} else if (feature_value.contains("%")) {
						sigmaData.add("0.00%");
					} else {
						sigmaData.add("0.0");
					}
					j++;
				}
			}
			for (int k = 0; k < COUNTER; k++) {
				counter.add(0);
			}
		}

		// 特徴量のスコア最小値データ,特徴量のスコア最大値データ
		String featureScoreMinData = null;
		String featureScoreMaxData = null;
		if (updAllFlg) {
			featureScoreMinData = getSplitFeatureStringData(allData, 5);
			featureScoreMaxData = getSplitFeatureStringData(allData, 6);
		} else {
			featureScoreMinData = "150'";
			featureScoreMaxData = "0'";
		}

		// 特徴量のスコア平均値データ,特徴量のスコア標準偏差データ
		String featureScoreMeanData = null;
		String featureScoreSigmaData = null;
		int featureScoreCounter = 0;
		if (updAllFlg) {
			featureScoreMeanData = getSplitFeatureStringData(allData, 7);
			featureScoreSigmaData = getSplitFeatureStringData(allData, 8);
			featureScoreCounter = getSplitFeatureIntegerData(allData);
			// 平均*件数を計算しておく
			featureScoreMeanData = calcFeatureAveSum(featureScoreMeanData, featureScoreCounter);
		} else {
			featureScoreMeanData = "0'";
			featureScoreSigmaData = "0'";
		}

		// 最大値,最小値を設定する
		List<StatSummary> statList = initInstance();
		for (int i = 0; i < statList.size(); i++) {
			statList.get(i).setMin(minData.get(i));
			statList.get(i).setMax(maxData.get(i));
		}

		for (ThresHoldEntity entity : filteredList) {
			// indexと一致するレコードの特徴量を計算対象とし平均を出すための和を導出する
			for (FieldMapping mapping : mappings) {
				int index = mapping.getIndex();
				String currentSum = aveData.get(index);
				String value = mapping.getGetter().apply(entity);
				AverageStatisticsOutputDTO outputDTO = sumOfAverage(currentSum, value,
						counter.get(index), file);
				aveData.set(index, outputDTO.getSum());
				counter.set(index, outputDTO.getCounter());
			}
		}

		// 平均を導出する(3分割データの場合それぞれで平均を導出する)
		for (int ind = 0; ind < aveData.size(); ind++) {
			if (aveData.get(ind).contains("/")) {
				List<String> connOrigin = new ArrayList<>();
				List<String> splitAveData = ExecuteMainUtil.splitGroup(aveData.get(ind));
				for (int threeind = 0; threeind < splitAveData.size(); threeind++) {
					String origin = splitAveData.get(threeind);
					if (origin.contains("%")) {
						origin = origin.replace("%", "");
					}
					if (counter.get(ind) != 0) {
						connOrigin.add(String.valueOf(Double.parseDouble(origin) / counter.get(ind)));
					} else {
						connOrigin.add("0.0");
						connOrigin.add("0.0");
						connOrigin.add("0.0");
					}
				}
				aveData.set(ind,
						String.format("%.2f", Double.parseDouble(connOrigin.get(0))) + "% ("
								+ String.format("%.2f", Double.parseDouble(connOrigin.get(1))) + "/" +
								String.format("%.2f", Double.parseDouble(connOrigin.get(2))) + ")");
			} else {
				if (counter.get(ind) != 0) {
					String remarks = "";
					String ave = aveData.get(ind);
					if (ave.contains("%")) {
						remarks = "%";
						ave = ave.replace("%", "");
					}
					aveData.set(ind,
							String.format("%.2f", (Double.parseDouble(ave) / counter.get(ind))) + remarks);
				}
			}
			//			System.out.println("ind: " + ind);
		}

		// 標準偏差を導出する
		for (ThresHoldEntity entity : filteredList) {
			for (FieldMapping mapping : mappings) {
				int index = mapping.getIndex();
				String currentSigma = sigmaData.get(index);
				String value = mapping.getGetter().apply(entity);
				AverageStatisticsOutputDTO outputDTO = sumOfSigma(currentSigma, value,
						aveData.get(index), file);
				sigmaData.set(index, outputDTO.getSigmaSum());
			}
		}
		// 3分割データの場合それぞれで標準偏差を導出する
		for (int ind = 0; ind < sigmaData.size(); ind++) {
			if (sigmaData.get(ind).contains("/")) {
				List<String> connOrigin = new ArrayList<>();
				List<String> splitSigmaData = ExecuteMainUtil.splitGroup(sigmaData.get(ind));
				for (int threeind = 0; threeind < splitSigmaData.size(); threeind++) {
					String origin = splitSigmaData.get(threeind);
					if (origin.contains("%")) {
						origin = origin.replace("%", "");
					}
					if (counter.get(ind) != 0) {
						connOrigin.add(String.valueOf(
								Math.sqrt(Double.parseDouble(origin) / counter.get(ind))));
					} else {
						connOrigin.add("0.0");
						connOrigin.add("0.0");
						connOrigin.add("0.0");
					}
				}
				sigmaData.set(ind,
						String.format("%.2f", Double.parseDouble(connOrigin.get(0))) + "% ("
								+ String.format("%.2f", Double.parseDouble(connOrigin.get(1))) + "/" +
								String.format("%.2f", Double.parseDouble(connOrigin.get(2))) + ")");
			} else {
				if (counter.get(ind) != 0) {
					String remarks = "";
					String sigma = sigmaData.get(ind);
					if (sigma.contains("%")) {
						remarks = "%";
						sigma = sigma.replace("%", "");
					}
					sigmaData.set(ind, String.format("%.2f",
							Math.sqrt(Double.parseDouble(sigma) / counter.get(ind))) + remarks);
				}
			}
		}

		// 統計データがなく,初期値から変化がなかった場合,できるだけ大きい値にする
		for (int i = 0; i < aveData.size(); i++) {
			String afMin = minData.get(i);
			String afMax = maxData.get(i);
			String afAve = aveData.get(i);
			String afSigma = sigmaData.get(i);
			if (afMin.equals("5000.00") && afMax.equals("-5000.00") &&
					(!afAve.contains("10000") || !afSigma.contains("10000"))) {
				if (afAve.contains("%")) {
					afAve = afAve.replace("0.00%", "10000.00%");
					afAve = afAve.replace("0.0% (0.0/0.0)", "10000.0% (10000.0/10000.0)");
				} else {
					afAve = afAve.replaceAll("\\b0\\.0\\b", "10000.0");
				}
				if (afSigma.contains("%")) {
					afSigma = afSigma.replace("0.00%", "10000.00%");
					afSigma = afSigma.replace("0.0% (0.0/0.0)", "10000.0% (10000.0/10000.0)");
				} else {
					afSigma = afSigma.replaceAll("\\b0\\.0\\b", "10000.0");
				}
			}
			aveData.set(i, afAve);
			sigmaData.set(i, afSigma);
		}

		//統計リストに格納
		for (int i = 0; i < statList.size(); i++) {
			statList.get(i).setMean(aveData.get(i));
			statList.get(i).setSigma(sigmaData.get(i));
			statList.get(i).setCount(counter.get(i));
		}

		// 特徴量の最小値,最大値を取得
		for (ThresHoldEntity entity : filteredList) {
			featureScoreMinData = timeCompareMin(featureScoreMinData, entity.getTimes());
			featureScoreMaxData = timeCompareMax(featureScoreMaxData, entity.getTimes());
		}
		featureScoreMinData = String.format("%.1f",
				Double.parseDouble(featureScoreMinData.replace("'", ""))) + "'";
		featureScoreMaxData = String.format("%.1f",
				Double.parseDouble(featureScoreMaxData.replace("'", ""))) + "'";

		// 特徴量の平均値,標準偏差を取得
		for (ThresHoldEntity entity : filteredList) {
			AverageStatisticsOutputDTO outDto = timeSumOfAverage(featureScoreMeanData,
					entity.getTimes(), featureScoreCounter);
			featureScoreMeanData = outDto.getSum();
			featureScoreCounter = outDto.getCounter();
		}
		if (featureScoreCounter != 0) {
			featureScoreMeanData = String.format("%.1f",
					Double.parseDouble(featureScoreMeanData.replace("'", "")) /
							featureScoreCounter)
					+ "'";
		}

		for (ThresHoldEntity entity : filteredList) {
			AverageStatisticsOutputDTO outDto = timeSumOfSigma(featureScoreSigmaData, entity.getTimes(),
					featureScoreMeanData);
			featureScoreSigmaData = outDto.getSigmaSum();
		}
		if (featureScoreCounter != 0) {
			featureScoreSigmaData = String.format("%.1f",
					Math.sqrt(Double.parseDouble(featureScoreSigmaData.replace("'", "")) /
							featureScoreCounter))
					+ "'";
		}

		//統計リストに格納
		for (int i = 0; i < statList.size(); i++) {
			statList.get(i).setFeatureTimeMin(featureScoreMinData);
			statList.get(i).setFeatureTimeMax(featureScoreMaxData);
			statList.get(i).setFeatureTimeMean(featureScoreMeanData);
			statList.get(i).setFeatureTimeSigma(featureScoreSigmaData);
			statList.get(i).setFeatureCount(featureScoreCounter);
		}

		// insert(すでに登録済みの場合はupdate)
		registerTeamStaticsData(country, league, situation, connectScore, statList, updAllFlg, updId);
	}

	/**
	 * 共通ロジック
	 * @param country 国
	 * @param league リーグ
	 * @param team チーム
	 * @param connectScore 連結スコア
	 * @param situation 状況
	 * @param entityList entityList
	 * @param allEntityList allEntityList
	 * @param file ファイル名
	 * @param flg 導出フラグ
	 * @param halfFlg ハーフタイムフラグ
	 * @throws IllegalArgumentException
	 * @throws IllegalAccessException
	 */
	private void commonCountryLeagueTeamLogic(String country, String league, String team,
			String connectScore, String situation,
			List<ThresHoldEntity> entityList, ThresHoldEntity allEntityList, String file,
			String flg, String halfFlg)
			throws IllegalArgumentException, IllegalAccessException {
		// DBに登録済みか
		AverageStatisticsOutputDTO averageStatisticsOutputDTO = getTeamStaticsData(country, league,
				team, connectScore, UniairConst.BM_M026);
		// 更新フラグ取得,更新通番取得
		boolean updAllFlg = averageStatisticsOutputDTO.isUpdFlg();
		String updId = averageStatisticsOutputDTO.getUpdId();
		List<List<String>> allData = averageStatisticsOutputDTO.getSelectList();

		// 既存のリスト
		List<ThresHoldEntity> filteredList = null;
		if (EACH_SCORE.equals(flg)) {
			filteredList = entityList.stream()
					.filter(entity -> connectScore.equals(entity.getHomeScore() + "-"
							+ entity.getAwayScore()))
					.collect(Collectors.toList());
		} else if (ALL_SCORE.equals(flg)) {
			filteredList = entityList;
		} else if (HALF_SCORE.equals(flg)) {
			// ハーフタイムの通番を特定
			String halfTimeSeq = findHalfTimeSeq(entityList);
			// ハーフタイム前の試合時間のデータをフィルタリング（通番が半分より小さいもの）
			if (FIRST_HALF_SCORE.equals(halfFlg)) {
				filteredList = entityList.stream()
						.filter(entity -> entity.getSeq().compareTo(halfTimeSeq) < 0) // 通番がハーフタイムより前
						.collect(Collectors.toList());
			} else if (SECOND_HALF_SCORE.equals(halfFlg)) {
				filteredList = entityList.stream()
						.filter(entity -> entity.getSeq().compareTo(halfTimeSeq) > 0) // 通番がハーフタイムより後
						.collect(Collectors.toList());
			}
		}

		// 空の場合終了
		if (filteredList == null || filteredList.isEmpty()) {
			return;
		}

		// 比較メソッドを呼び出し最大値,最小値を導出する。
		List<String> minData = new ArrayList<>();
		List<String> maxData = new ArrayList<>();
		List<FieldMapping> mappings = StatMapping.createFieldMappings();
		for (ThresHoldEntity entity : filteredList) {
			if (updAllFlg) {
				minData = getSplitStringData(allData, 0);
				maxData = getSplitStringData(allData, 1);
			} else {
				// 最小値,最大値,平均,標準偏差を求めるフィールド数*2のリストを保持する
				for (int i = 0; i < COUNTER; i++) {
					minData.add("5000.0");
				}
				for (int i = 0; i < COUNTER; i++) {
					maxData.add("-5000.0");
				}
			}

			for (FieldMapping mapping : mappings) {
				int index = mapping.getIndex();
				String currentMin = minData.get(index);
				String currentMax = maxData.get(index);
				String value = mapping.getGetter().apply(entity);
				minData.set(index, compareMin(currentMin, value));
				maxData.set(index, compareMax(currentMax, value));
				//				System.out.println("entityNum: " + entityNum + ", index: " + index);
			}
		}

		List<String> aveData = new ArrayList<>();
		List<String> sigmaData = new ArrayList<>();
		List<Integer> counter = new ArrayList<>();
		if (updAllFlg) {
			aveData = getSplitStringData(allData, 2);
			sigmaData = getSplitStringData(allData, 3);
			counter = getSplitIntegerData(allData);
			// 平均*件数を計算しておく
			aveData = calcAveSum(aveData, counter);
		} else {
			// 平均,標準偏差,件数の初期化
			int i = 0;
			Field[] fields = allEntityList.getClass().getDeclaredFields();
			for (Field field : fields) {
				field.setAccessible(true);
				if ((i >= 0 && i <= 9) ||
						i >= 62) {
					i++;
					continue;
				} else {
					String feature_value = (String) field.get(allEntityList);
					if (feature_value.contains("/")) {
						aveData.add("0.0% (0.0/0.0)");
					} else if (feature_value.contains("%")) {
						aveData.add("0.00%");
					} else {
						aveData.add("0.0");
					}
					i++;
				}
			}
			int j = 0;
			for (Field field : fields) {
				field.setAccessible(true);
				if ((j >= 0 && j <= 9) ||
						j >= 62) {
					j++;
					continue;
				} else {
					String feature_value = (String) field.get(allEntityList);
					if (feature_value.contains("/")) {
						sigmaData.add("0.00% (0.0/0.0)");
					} else if (feature_value.contains("%")) {
						sigmaData.add("0.00%");
					} else {
						sigmaData.add("0.0");
					}
					j++;
				}
			}
			for (int k = 0; k < COUNTER; k++) {
				counter.add(0);
			}
		}

		// 特徴量のスコア最小値データ,特徴量のスコア最大値データ
		String featureScoreMinData = null;
		String featureScoreMaxData = null;
		if (updAllFlg) {
			featureScoreMinData = getSplitFeatureStringData(allData, 5);
			featureScoreMaxData = getSplitFeatureStringData(allData, 6);
		} else {
			featureScoreMinData = "150'";
			featureScoreMaxData = "0'";
		}

		// 特徴量のスコア平均値データ,特徴量のスコア標準偏差データ
		String featureScoreMeanData = null;
		String featureScoreSigmaData = null;
		int featureScoreCounter = 0;
		if (updAllFlg) {
			featureScoreMeanData = getSplitFeatureStringData(allData, 7);
			featureScoreSigmaData = getSplitFeatureStringData(allData, 8);
			featureScoreCounter = getSplitFeatureIntegerData(allData);
			// 平均*件数を計算しておく
			featureScoreMeanData = calcFeatureAveSum(featureScoreMeanData, featureScoreCounter);
		} else {
			featureScoreMeanData = "0'";
			featureScoreSigmaData = "0'";
		}

		// 最大値,最小値を設定する
		List<StatSummary> statList = initInstance();
		for (int i = 0; i < statList.size(); i++) {
			statList.get(i).setMin(minData.get(i));
			statList.get(i).setMax(maxData.get(i));
		}

		for (ThresHoldEntity entity : filteredList) {
			// indexと一致するレコードの特徴量を計算対象とし平均を出すための和を導出する
			for (FieldMapping mapping : mappings) {
				int index = mapping.getIndex();
				String currentSum = aveData.get(index);
				String value = mapping.getGetter().apply(entity);
				AverageStatisticsOutputDTO outputDTO = sumOfAverage(currentSum, value,
						counter.get(index), file);
				aveData.set(index, outputDTO.getSum());
				counter.set(index, outputDTO.getCounter());
			}
		}

		// 平均を導出する(3分割データの場合それぞれで平均を導出する)
		for (int ind = 0; ind < aveData.size(); ind++) {
			if (aveData.get(ind).contains("/")) {
				List<String> connOrigin = new ArrayList<>();
				List<String> splitAveData = ExecuteMainUtil.splitGroup(aveData.get(ind));
				for (int threeind = 0; threeind < splitAveData.size(); threeind++) {
					String origin = splitAveData.get(threeind);
					if (origin.contains("%")) {
						origin = origin.replace("%", "");
					}
					if (counter.get(ind) != 0) {
						connOrigin.add(String.valueOf(Double.parseDouble(origin) / counter.get(ind)));
					} else {
						connOrigin.add("0.0");
						connOrigin.add("0.0");
						connOrigin.add("0.0");
					}
				}
				aveData.set(ind,
						String.format("%.2f", Double.parseDouble(connOrigin.get(0))) + "% ("
								+ String.format("%.2f", Double.parseDouble(connOrigin.get(1))) + "/" +
								String.format("%.2f", Double.parseDouble(connOrigin.get(2))) + ")");
			} else {
				if (counter.get(ind) != 0) {
					String remarks = "";
					String ave = aveData.get(ind);
					if (ave.contains("%")) {
						remarks = "%";
						ave = ave.replace("%", "");
					}
					aveData.set(ind,
							String.format("%.2f", (Double.parseDouble(ave) / counter.get(ind))) + remarks);
				}
			}
			//			System.out.println("ind: " + ind);
		}

		// 標準偏差を導出する
		for (ThresHoldEntity entity : filteredList) {
			for (FieldMapping mapping : mappings) {
				int index = mapping.getIndex();
				String currentSigma = sigmaData.get(index);
				String value = mapping.getGetter().apply(entity);
				AverageStatisticsOutputDTO outputDTO = sumOfSigma(currentSigma, value,
						aveData.get(index), file);
				sigmaData.set(index, outputDTO.getSigmaSum());
			}
		}
		// 3分割データの場合それぞれで標準偏差を導出する
		for (int ind = 0; ind < sigmaData.size(); ind++) {
			if (sigmaData.get(ind).contains("/")) {
				List<String> connOrigin = new ArrayList<>();
				List<String> splitSigmaData = ExecuteMainUtil.splitGroup(sigmaData.get(ind));
				for (int threeind = 0; threeind < splitSigmaData.size(); threeind++) {
					String origin = splitSigmaData.get(threeind);
					if (origin.contains("%")) {
						origin = origin.replace("%", "");
					}
					if (counter.get(ind) != 0) {
						connOrigin.add(String.valueOf(
								Math.sqrt(Double.parseDouble(origin) / counter.get(ind))));
					} else {
						connOrigin.add("0.0");
						connOrigin.add("0.0");
						connOrigin.add("0.0");
					}
				}
				sigmaData.set(ind,
						String.format("%.2f", Double.parseDouble(connOrigin.get(0))) + "% ("
								+ String.format("%.2f", Double.parseDouble(connOrigin.get(1))) + "/" +
								String.format("%.2f", Double.parseDouble(connOrigin.get(2))) + ")");
			} else {
				if (counter.get(ind) != 0) {
					String remarks = "";
					String sigma = sigmaData.get(ind);
					if (sigma.contains("%")) {
						remarks = "%";
						sigma = sigma.replace("%", "");
					}
					sigmaData.set(ind, String.format("%.2f",
							Math.sqrt(Double.parseDouble(sigma) / counter.get(ind))) + remarks);
				}
			}
		}

		// 統計データがなく,初期値から変化がなかった場合,できるだけ大きい値にする
		for (int i = 0; i < aveData.size(); i++) {
			String afMin = minData.get(i);
			String afMax = maxData.get(i);
			String afAve = aveData.get(i);
			String afSigma = sigmaData.get(i);
			if (afMin.equals("5000.00") && afMax.equals("-5000.00") &&
					(!afAve.contains("10000") || !afSigma.contains("10000"))) {
				if (afAve.contains("%")) {
					afAve = afAve.replace("0.00%", "10000.00%");
					afAve = afAve.replace("0.0% (0.0/0.0)", "10000.0% (10000.0/10000.0)");
				} else {
					afAve = afAve.replaceAll("\\b0\\.0\\b", "10000.0");
				}
				if (afSigma.contains("%")) {
					afSigma = afSigma.replace("0.00%", "10000.00%");
					afSigma = afSigma.replace("0.0% (0.0/0.0)", "10000.0% (10000.0/10000.0)");
				} else {
					afSigma = afSigma.replaceAll("\\b0\\.0\\b", "10000.0");
				}
			}
			aveData.set(i, afAve);
			sigmaData.set(i, afSigma);
		}

		//統計リストに格納
		for (int i = 0; i < statList.size(); i++) {
			statList.get(i).setMean(aveData.get(i));
			statList.get(i).setSigma(sigmaData.get(i));
			statList.get(i).setCount(counter.get(i));
		}

		// 特徴量の最小値,最大値を取得
		for (ThresHoldEntity entity : filteredList) {
			featureScoreMinData = timeCompareMin(featureScoreMinData, entity.getTimes());
			featureScoreMaxData = timeCompareMax(featureScoreMaxData, entity.getTimes());
		}
		featureScoreMinData = String.format("%.1f",
				Double.parseDouble(featureScoreMinData.replace("'", ""))) + "'";
		featureScoreMaxData = String.format("%.1f",
				Double.parseDouble(featureScoreMaxData.replace("'", ""))) + "'";

		// 特徴量の平均値,標準偏差を取得
		for (ThresHoldEntity entity : filteredList) {
			AverageStatisticsOutputDTO outDto = timeSumOfAverage(featureScoreMeanData,
					entity.getTimes(), featureScoreCounter);
			featureScoreMeanData = outDto.getSum();
			featureScoreCounter = outDto.getCounter();
		}
		if (featureScoreCounter != 0) {
			featureScoreMeanData = String.format("%.1f",
					Double.parseDouble(featureScoreMeanData.replace("'", "")) /
							featureScoreCounter)
					+ "'";
		}

		for (ThresHoldEntity entity : filteredList) {
			AverageStatisticsOutputDTO outDto = timeSumOfSigma(featureScoreSigmaData, entity.getTimes(),
					featureScoreMeanData);
			featureScoreSigmaData = outDto.getSigmaSum();
		}
		if (featureScoreCounter != 0) {
			featureScoreSigmaData = String.format("%.1f",
					Math.sqrt(Double.parseDouble(featureScoreSigmaData.replace("'", "")) /
							featureScoreCounter))
					+ "'";
		}

		//統計リストに格納
		for (int i = 0; i < statList.size(); i++) {
			statList.get(i).setFeatureTimeMin(featureScoreMinData);
			statList.get(i).setFeatureTimeMax(featureScoreMaxData);
			statList.get(i).setFeatureTimeMean(featureScoreMeanData);
			statList.get(i).setFeatureTimeSigma(featureScoreSigmaData);
			statList.get(i).setFeatureCount(featureScoreCounter);
		}

		// insert(すでに登録済みの場合はupdate)
		registerTeamStaticsDetailData(country, league, team, situation, connectScore, statList, updAllFlg, updId);
	}

	/**
	 * 比較して小さい方をreturn
	 * @param origin
	 * @param comp
	 * @return
	 */
	private String compareMin(String origin, String comp) {
		String remarks = "";
		// 3分割データの場合成功数が最も小さいものを比較する
		List<String> splitOrigin = null;
		List<String> splitComp = null;
		boolean threeOriFlg = false;
		boolean threeComFlg = false;
		if (origin.contains("/")) {
			splitOrigin = ExecuteMainUtil.splitGroup(origin);
			if (splitOrigin != null && splitOrigin.size() == 3) {
				origin = splitOrigin.get(1);
			}
			threeOriFlg = true;
		}
		if (comp.contains("/")) {
			splitComp = ExecuteMainUtil.splitGroup(comp);
			if (splitComp != null && splitComp.size() == 3) {
				comp = splitComp.get(1);
			}
			threeComFlg = true;
		}

		// 空文字の場合はoriginを返却
		if ("".contains(comp)) {
			if (threeOriFlg) {
				return splitOrigin.get(0) + " (" + splitOrigin.get(1)
						+ "/" + splitOrigin.get(2) + ")";
			}
			if (origin.contains("%")) {
				remarks = "%";
				origin = origin.replace("%", "");
			}
			return String.format("%.2f", Double.parseDouble(origin)) + remarks;
		}

		if (origin.contains("%")) {
			remarks = "%";
			origin = origin.replace("%", "");
		}
		if (comp.contains("%")) {
			remarks = "%";
			comp = comp.replace("%", "");
		}
		double originDouble = 0.0;
		double compDouble = 0.0;
		try {
			originDouble = Double.parseDouble(origin);
			compDouble = Double.parseDouble(comp);
		} catch (NumberFormatException e) {
			System.err.println("compareMin NumberFormatException: " + origin + ", " + comp);
			e.printStackTrace(); // ここでエラースタックトレースを表示
		} catch (Exception e) {
			System.err.println("compareMin originDouble: " + origin + ", compDouble: " + comp + ", err: " + e);
		}
		if (originDouble > compDouble) {
			origin = comp;
			if (threeComFlg) {
				return splitComp.get(0) + " (" + splitComp.get(1)
						+ "/" + splitComp.get(2) + ")";
			}
		}
		if (threeOriFlg && splitOrigin != null) {
			return splitOrigin.get(0) + " (" + splitOrigin.get(1)
					+ "/" + splitOrigin.get(2) + ")";
		}
		return String.format("%.2f", Double.parseDouble(origin)) + remarks;
	}

	/**
	 * 比較して大きい方をreturn
	 * @param origin
	 * @param comp
	 * @return
	 */
	private String compareMax(String origin, String comp) {
		String remarks = "";
		// 3分割データの場合は成功数が最も多いものを比較する
		List<String> splitOrigin = null;
		List<String> splitComp = null;
		boolean threeOriFlg = false;
		boolean threeComFlg = false;
		if (origin.contains("/")) {
			splitOrigin = ExecuteMainUtil.splitGroup(origin);
			if (splitOrigin != null && splitOrigin.size() == 3) {
				origin = splitOrigin.get(1);
			}
			threeOriFlg = true;
		}
		if (comp.contains("/")) {
			splitComp = ExecuteMainUtil.splitGroup(comp);
			if (splitComp != null && splitComp.size() == 3) {
				comp = splitComp.get(1);
			}
			threeComFlg = true;
		}

		// 空文字の場合はoriginを返却
		if ("".contains(comp)) {
			if (threeOriFlg) {
				return splitOrigin.get(0) + " (" + splitOrigin.get(1)
						+ "/" + splitOrigin.get(2) + ")";
			}
			if (origin.contains("%")) {
				remarks = "%";
				origin = origin.replace("%", "");
			}
			return String.format("%.2f", Double.parseDouble(origin)) + remarks;
		}

		if (origin.contains("%")) {
			remarks = "%";
			origin = origin.replace("%", "");
		}
		if (comp.contains("%")) {
			remarks = "%";
			comp = comp.replace("%", "");
		}
		double originDouble = 0.0;
		double compDouble = 0.0;
		try {
			originDouble = Double.parseDouble(origin);
			compDouble = Double.parseDouble(comp);
		} catch (NumberFormatException e) {
			System.err.println("compareMax NumberFormatException: " + origin + ", " + comp);
			e.printStackTrace(); // ここでエラースタックトレースを表示
		} catch (Exception e) {
			System.err.println("compareMax originDouble: " + origin + ", compDouble: " + comp + ", err: " + e);
		}
		if (originDouble < compDouble) {
			origin = comp;
			if (threeComFlg) {
				return splitComp.get(0) + " (" + splitComp.get(1)
						+ "/" + splitComp.get(2) + ")";
			}
		}
		if (threeOriFlg && splitOrigin != null) {
			return splitOrigin.get(0) + " (" + splitOrigin.get(1)
					+ "/" + splitOrigin.get(2) + ")";
		}
		return String.format("%.2f", Double.parseDouble(origin)) + remarks;
	}

	/**
	 * 比較して小さい方をreturn
	 * @param origin
	 * @param comp
	 * @param format
	 * @return
	 */
	private String timeCompareMin(String origin, String comp) {
		double origins = ExecuteMainUtil.convertToMinutes(origin);
		double comps = ExecuteMainUtil.convertToMinutes(comp);
		// シングルクウォーテーションを付与して返却
		if (origins > comps) {
			origins = comps;
		}
		return String.valueOf(origins) + "'";
	}

	/**
	 * 比較して大きい方をreturn
	 * @param origin
	 * @param comp
	 * @param format
	 * @return
	 */
	private String timeCompareMax(String origin, String comp) {
		double origins = ExecuteMainUtil.convertToMinutes(origin);
		double comps = ExecuteMainUtil.convertToMinutes(comp);
		// シングルクウォーテーションを付与して返却
		if (origins < comps) {
			origins = comps;
		}
		return String.valueOf(origins) + "'";
	}

	/**
	 * 数字を合計する(値がない場合は何もしない)
	 * @param origin
	 * @param comp
	 * @return
	 */
	private AverageStatisticsOutputDTO sumOfAverage(String origin, String comp,
			int count, String file) {
		AverageStatisticsOutputDTO dto = new AverageStatisticsOutputDTO();
		String remarks = "";
		List<String> splitOrigin = null;
		List<String> splitComp = null;
		if ((origin == null || "".equals(origin)) ||
				(comp == null || "".equals(comp))) {
			dto.setSum(origin);
			dto.setCounter(count);
		} else {
			boolean oriThreeFlg = false;
			boolean comThreeFlg = false;
			if (origin.contains("/")) {
				//System.out.println("/ origin in: " + origin);
				splitOrigin = ExecuteMainUtil.splitGroup(origin);
				oriThreeFlg = true;
			}
			if (comp.contains("/")) {
				//System.out.println("/ comp in: " + comp);
				splitComp = ExecuteMainUtil.splitGroup(comp);
				comThreeFlg = true;
			}
			if (oriThreeFlg && comThreeFlg) {
				List<String> connOrigin = new ArrayList<>();
				for (int threeind = 0; threeind < splitOrigin.size(); threeind++) {
					String sigmaOrigin = splitOrigin.get(threeind);
					if (sigmaOrigin.contains("%")) {
						sigmaOrigin = sigmaOrigin.replace("%", "");
					}
					String sigmaComp = splitComp.get(threeind);
					if (sigmaComp.contains("%")) {
						sigmaComp = sigmaComp.replace("%", "");
					}
					double originDouble = Double.parseDouble(sigmaOrigin);
					double compDouble = Double.parseDouble(sigmaComp);
					connOrigin.add(String.valueOf(originDouble + compDouble));
				}
				//System.out.println("three sumOfAverage chk: " + connOrigin);
				origin = String.valueOf(connOrigin.get(0))
						+ "% (" + String.valueOf(connOrigin.get(1)) + "/" +
						String.valueOf(connOrigin.get(2)) + ")";
				dto.setSum(origin);
				dto.setCounter(count + 1);
			} else if (oriThreeFlg && !comThreeFlg) {
				dto.setSum(splitOrigin.get(0) + " (" + splitOrigin.get(1) + "/" + splitOrigin.get(2) + ")");
				dto.setCounter(count);
			} else if (!oriThreeFlg && comThreeFlg) {
				dto.setSum(splitComp.get(0) + " (" + splitComp.get(1) + "/" + splitComp.get(2) + ")");
				dto.setCounter(count + 1);
			} else {
				if (origin.contains("%")) {
					remarks = "%";
					origin = origin.replace("%", "");
				}
				if (comp.contains("%")) {
					remarks = "%";
					comp = comp.replace("%", "");
				}
				double originDouble = 0.0;
				double compDouble = 0.0;
				try {
					originDouble = Double.parseDouble(origin);
					compDouble = Double.parseDouble(comp);
					dto.setSum(String.valueOf(originDouble + compDouble) + remarks);
					if ("0.0".equals(comp) || "0".equals(comp)) {
						dto.setCounter(count);
					} else {
						dto.setCounter(count + 1);
					}
				} catch (NumberFormatException e) {
					System.err.println("sumOfAverage NumberFormatException: " + origin + ", " + comp);
					e.printStackTrace(); // ここでエラースタックトレースを表示
					dto.setSum(origin + remarks);
					dto.setCounter(count);
				} catch (Exception e) {
					System.err
							.println("sumOfAverage originDouble: " + origin + ", "
									+ "compDouble: " + comp + ", err: " + e);
					dto.setSum(origin + remarks);
					dto.setCounter(count);
				}
			}
		}
		return dto;
	}

	/**
	 * 数字を合計する(値がない場合は何もしない)
	 * @param origin
	 * @param comp
	 * @return
	 */
	private AverageStatisticsOutputDTO timeSumOfAverage(String origin, String comp,
			int count) {
		AverageStatisticsOutputDTO dto = new AverageStatisticsOutputDTO();
		double origins = ExecuteMainUtil.convertToMinutes(origin);
		double comps = ExecuteMainUtil.convertToMinutes(comp);
		if (comps == 0.0) {
			dto.setSum(String.valueOf(origins) + "'");
			dto.setCounter(count);
		} else {
			dto.setSum(String.valueOf(origins + comps) + "'");
			dto.setCounter(count + 1);
		}
		return dto;
	}

	/**
	 * 数字から平均を引いた2乗を合計する(値がない場合は何もしない)
	 * @param origin
	 * @param comp
	 * @param ave
	 * @return
	 */
	private AverageStatisticsOutputDTO sumOfSigma(String origin, String comp, String ave, String file) {
		AverageStatisticsOutputDTO dto = new AverageStatisticsOutputDTO();
		List<String> splitOrigin = null;
		List<String> splitComp = null;
		List<String> splitAve = null;
		boolean oriThreeFlg = false;
		boolean comThreeFlg = false;
		boolean aveThreeFlg = false;
		String remarks = "";
		if ((origin == null || "".equals(origin)) ||
				(comp == null || "".equals(comp))) {
			dto.setSigmaSum(origin);
		} else {
			if (origin.contains("/")) {
				//System.out.println("/ origin in: " + origin);
				splitOrigin = ExecuteMainUtil.splitGroup(origin);
				oriThreeFlg = true;
			}
			if (comp.contains("/")) {
				//System.out.println("/ comp in: " + comp);
				splitComp = ExecuteMainUtil.splitGroup(comp);
				comThreeFlg = true;
			}
			if (ave.contains("/")) {
				//System.out.println("/ ave in: " + ave);
				splitAve = ExecuteMainUtil.splitGroup(ave);
				aveThreeFlg = true;
			}
			if (oriThreeFlg && comThreeFlg && aveThreeFlg) {
				List<String> connOrigin = new ArrayList<>();
				for (int threeind = 0; threeind < splitOrigin.size(); threeind++) {
					String sigmaOrigin = splitOrigin.get(threeind);
					if (sigmaOrigin.contains("%")) {
						sigmaOrigin = sigmaOrigin.replace("%", "");
					}
					String sigmaComp = splitComp.get(threeind);
					if (sigmaComp.contains("%")) {
						sigmaComp = sigmaComp.replace("%", "");
					}
					String sigmaAve = splitAve.get(threeind);
					if (sigmaAve.contains("%")) {
						sigmaAve = sigmaAve.replace("%", "");
					}
					double originDouble = 0.0;
					double compDouble = 0.0;
					double aveDouble = 0.0;
					try {
						originDouble = Double.parseDouble(sigmaOrigin);
						compDouble = Double.parseDouble(sigmaComp);
						aveDouble = Double.parseDouble(sigmaAve);
						double result = originDouble += Math.pow((compDouble - aveDouble), 2);
						connOrigin.add(String.valueOf(result));
					} catch (NumberFormatException e) {
						System.err.println("sumOfSigma NumberFormatException: " + sigmaOrigin + ", " + sigmaComp + ", "
								+ sigmaAve);
						e.printStackTrace(); // ここでエラースタックトレースを表示
					} catch (Exception e) {
						System.err.println("sumOfSigma originDouble: " + sigmaOrigin + ", "
								+ "compDouble: " + sigmaComp + ", "
								+ "aveDouble: " + aveDouble + ", err: " + e);
					}
				}
				//System.out.println("three sumOfSigma chk: " + connOrigin);
				if (!connOrigin.isEmpty()) {
					origin = String.valueOf(connOrigin.get(0))
							+ "% (" + String.valueOf(connOrigin.get(1)) + "/" +
							String.valueOf(connOrigin.get(2)) + ")";
				}
				dto.setSigmaSum(origin);
			} else if (comThreeFlg && aveThreeFlg) {
				List<String> connOrigin = new ArrayList<>();
				for (int threeind = 0; threeind < splitComp.size(); threeind++) {
					String sigmaComp = splitComp.get(threeind);
					if (sigmaComp.contains("%")) {
						sigmaComp = sigmaComp.replace("%", "");
					}
					String sigmaAve = splitAve.get(threeind);
					if (sigmaAve.contains("%")) {
						sigmaAve = sigmaAve.replace("%", "");
					}
					double compDouble = 0.0;
					double aveDouble = 0.0;
					try {
						compDouble = Double.parseDouble(sigmaComp);
						aveDouble = Double.parseDouble(sigmaAve);
						connOrigin.add(String.valueOf(Math.pow((compDouble - aveDouble), 2)));
					} catch (NumberFormatException e) {
						System.err.println("sumOfSigma NumberFormatException: " + sigmaComp + ", " + aveDouble);
						e.printStackTrace(); // ここでエラースタックトレースを表示
					} catch (Exception e) {
						System.err.println("sumOfSigma compDouble: " + sigmaComp + ", "
								+ "aveDouble: " + aveDouble + ", err: " + e);
					}
				}
				//System.out.println("two sumOfSigma chk: " + connOrigin);
				if (!connOrigin.isEmpty()) {
					origin = String.valueOf(connOrigin.get(0))
							+ "% (" + String.valueOf(connOrigin.get(1)) + "/" +
							String.valueOf(connOrigin.get(2)) + ")";
				}
				dto.setSigmaSum(origin);
				// 3分割データと単一データが混じっている場合,基本的に単一データ側は成功数と試行数が不明であるパターンが多いため集計しない
			} else if (oriThreeFlg && !comThreeFlg) {
				//System.out.println("sumOfSigma skipします: " + origin + ", " + comp);
				dto.setSigmaSum(origin);
			} else {
				if (origin.contains("%")) {
					remarks = "%";
					origin = origin.replace("%", "");
				}
				if (comp.contains("%")) {
					remarks = "%";
					comp = comp.replace("%", "");
				}
				if (ave.contains("%")) {
					remarks = "%";
					ave = ave.replace("%", "");
				}
				double originDouble = 0.0;
				double compDouble = 0.0;
				double aveDouble = 0.0;
				try {
					originDouble = Double.parseDouble(origin);
					compDouble = Double.parseDouble(comp);
					aveDouble = Double.parseDouble(ave);
					originDouble += Math.pow((compDouble - aveDouble), 2);
					dto.setSigmaSum(String.valueOf(originDouble) + remarks);
				} catch (NumberFormatException e) {
					System.err.println("sumOfSigma NumberFormatException: " + origin + ", " + comp + ", " + ave);
					e.printStackTrace(); // ここでエラースタックトレースを表示
					dto.setSigmaSum(origin + remarks);
				} catch (Exception e) {
					System.err.println("sumOfSigma originDouble: " + origin + ", "
							+ "compDouble: " + comp + ", "
							+ "aveDouble: " + ave + ", err: " + e);
					dto.setSigmaSum(origin + remarks);
				}
			}
		}
		return dto;
	}

	/**
	 * 数字から平均を引いた2乗を合計する(値がない場合は何もしない)
	 * @param origin
	 * @param comp
	 * @param ave
	 * @return
	 */
	private AverageStatisticsOutputDTO timeSumOfSigma(String origin, String comp,
			String ave) {
		AverageStatisticsOutputDTO dto = new AverageStatisticsOutputDTO();
		double origins = ExecuteMainUtil.convertToMinutes(origin);
		double comps = ExecuteMainUtil.convertToMinutes(comp);
		double aves = ExecuteMainUtil.convertToMinutes(ave);
		if (comps == 0.0) {
			dto.setSigmaSum(String.valueOf(origins) + "'");
		} else {
			dto.setSigmaSum(String.valueOf(origins + Math.pow((comps - aves), 2)) + "'");
		}
		return dto;
	}

	/**
	 * インスタンス化する
	 * @param stat
	 * @return
	 */
	private List<StatSummary> initInstance() {
		List<StatSummary> statList = new ArrayList<>();
		for (int i = 0; i < COUNTER; i++) {
			statList.add(new StatSummary(null, null, null, null, 0, null, null, null, null, 0));
		}
		return statList;
	}

	/**
	 * 登録メソッド
	 * @param country 国
	 * @param league リーグ
	 * @param situation 得点状況
	 * @param connectScore スコア連結
	 * @param updFlg 更新フラグ
	 * @param seq 通番
	 */
	private void registerTeamStaticsData(String country, String league, String situation,
			String connectScore, List<StatSummary> statList, boolean updFlg, String id) {
		if (updFlg) {
			List<String> selectList = UniairColumnMapUtil.getKeyMap(UniairConst.BM_M023);
			String[] selDataList = new String[selectList.size()];
			for (int i = 0; i < selectList.size(); i++) {
				selDataList[i] = selectList.get(i);
			}
			// 1つずつstatListの値を取得する
			List<String> stat = collectStatSummaryValues(statList);
			StringBuilder sBuilder = new StringBuilder();
			for (int ind = 5; ind < COUNTER + 5; ind++) {
				if (sBuilder.toString().length() > 0) {
					sBuilder.append(", ");
				}
				String sta = stat.get(ind - 5);
				sta = sta.replace("'", "''");
				sBuilder.append(" " + selDataList[ind] + " = '" + sta + "'");
			}
			sBuilder.append(", update_time = '" + DateUtil.getSysDate() + "'");
			UpdateWrapper updateWrapper = new UpdateWrapper();

			String where = "id = '" + id + "'";
			updateWrapper.updateExecute(UniairConst.BM_M023, where,
					sBuilder.toString());
			System.out.println("BM_M023を更新しました。country: " + country + ", league: " + league +
					", score: " + connectScore);
		} else {
			List<AverageStatisticsEntity> insertEntities = new ArrayList<AverageStatisticsEntity>();
			AverageStatisticsEntity statSummaries = new AverageStatisticsEntity();
			statSummaries.setSituation(situation);
			statSummaries.setScore(connectScore);
			statSummaries.setCountry(country);
			statSummaries.setLeague(league);
			statSummaries.setHomeExpStat(statList.get(0)); // インデックス0: homeExpStat
			statSummaries.setAwayExpStat(statList.get(1)); // インデックス1: awayExpStat
			statSummaries.setHomeDonationStat(statList.get(2)); // インデックス2: homeDonationStat
			statSummaries.setAwayDonationStat(statList.get(3)); // インデックス3: awayDonationStat
			statSummaries.setHomeShootAllStat(statList.get(4)); // インデックス4: homeShootAllStat
			statSummaries.setAwayShootAllStat(statList.get(5)); // インデックス5: awayShootAllStat
			statSummaries.setHomeShootInStat(statList.get(6)); // インデックス6: homeShootInStat
			statSummaries.setAwayShootInStat(statList.get(7)); // インデックス7: awayShootInStat
			statSummaries.setHomeShootOutStat(statList.get(8)); // インデックス8: homeShootOutStat
			statSummaries.setAwayShootOutStat(statList.get(9)); // インデックス9: awayShootOutStat
			statSummaries.setHomeBlockShootStat(statList.get(10)); // インデックス10: homeBlockShootStat
			statSummaries.setAwayBlockShootStat(statList.get(11)); // インデックス11: awayBlockShootStat
			statSummaries.setHomeBigChanceStat(statList.get(12)); // インデックス12: homeBigChanceStat
			statSummaries.setAwayBigChanceStat(statList.get(13)); // インデックス13: awayBigChanceStat
			statSummaries.setHomeCornerStat(statList.get(14)); // インデックス14: homeCornerStat
			statSummaries.setAwayCornerStat(statList.get(15)); // インデックス15: awayCornerStat
			statSummaries.setHomeBoxShootInStat(statList.get(16)); // インデックス16: homeBoxShootInStat
			statSummaries.setAwayBoxShootInStat(statList.get(17)); // インデックス17: awayBoxShootInStat
			statSummaries.setHomeBoxShootOutStat(statList.get(18)); // インデックス18: homeBoxShootOutStat
			statSummaries.setAwayBoxShootOutStat(statList.get(19)); // インデックス19: awayBoxShootOutStat
			statSummaries.setHomeGoalPostStat(statList.get(20)); // インデックス20: homeGoalPostStat
			statSummaries.setAwayGoalPostStat(statList.get(21)); // インデックス21: awayGoalPostStat
			statSummaries.setHomeGoalHeadStat(statList.get(22)); // インデックス22: homeGoalHeadStat
			statSummaries.setAwayGoalHeadStat(statList.get(23)); // インデックス23: awayGoalHeadStat
			statSummaries.setHomeKeeperSaveStat(statList.get(24)); // インデックス24: homeKeeperSaveStat
			statSummaries.setAwayKeeperSaveStat(statList.get(25)); // インデックス25: awayKeeperSaveStat
			statSummaries.setHomeFreeKickStat(statList.get(26)); // インデックス26: homeFreeKickStat
			statSummaries.setAwayFreeKickStat(statList.get(27)); // インデックス27: awayFreeKickStat
			statSummaries.setHomeOffsideStat(statList.get(28)); // インデックス28: homeOffsideStat
			statSummaries.setAwayOffsideStat(statList.get(29)); // インデックス29: awayOffsideStat
			statSummaries.setHomeFoulStat(statList.get(30)); // インデックス30: homeFoulStat
			statSummaries.setAwayFoulStat(statList.get(31)); // インデックス31: awayFoulStat
			statSummaries.setHomeYellowCardStat(statList.get(32)); // インデックス32: homeYellowCardStat
			statSummaries.setAwayYellowCardStat(statList.get(33)); // インデックス33: awayYellowCardStat
			statSummaries.setHomeRedCardStat(statList.get(34)); // インデックス34: homeRedCardStat
			statSummaries.setAwayRedCardStat(statList.get(35)); // インデックス35: awayRedCardStat
			statSummaries.setHomeSlowInStat(statList.get(36)); // インデックス36: homeSlowInStat
			statSummaries.setAwaySlowInStat(statList.get(37)); // インデックス37: awaySlowInStat
			statSummaries.setHomeBoxTouchStat(statList.get(38)); // インデックス38: homeBoxTouchStat
			statSummaries.setAwayBoxTouchStat(statList.get(39)); // インデックス39: awayBoxTouchStat
			statSummaries.setHomePassCountStat(statList.get(40)); // インデックス40: homePassCountStat
			statSummaries.setAwayPassCountStat(statList.get(41)); // インデックス41: awayPassCountStat
			statSummaries.setHomeFinalThirdPassCountStat(statList.get(42)); // インデックス42: homeFinalThirdPassCountStat
			statSummaries.setAwayFinalThirdPassCountStat(statList.get(43)); // インデックス43: awayFinalThirdPassCountStat
			statSummaries.setHomeCrossCountStat(statList.get(44)); // インデックス44: homeCrossCountStat
			statSummaries.setAwayCrossCountStat(statList.get(45)); // インデックス45: awayCrossCountStat
			statSummaries.setHomeTackleCountStat(statList.get(46)); // インデックス46: homeTackleCountStat
			statSummaries.setAwayTackleCountStat(statList.get(47)); // インデックス47: awayTackleCountStat
			statSummaries.setHomeClearCountStat(statList.get(48)); // インデックス48: homeClearCountStat
			statSummaries.setAwayClearCountStat(statList.get(49)); // インデックス49: awayClearCountStat
			statSummaries.setHomeInterceptCountStat(statList.get(50)); // インデックス50: homeInterceptCountStat
			statSummaries.setAwayInterceptCountStat(statList.get(51)); // インデックス51: awayInterceptCountStat
			insertEntities.add(statSummaries);

			CsvRegisterImpl csvRegisterImpl = new CsvRegisterImpl();
			try {
				csvRegisterImpl.executeInsert(UniairConst.BM_M023,
						insertEntities, 1, insertEntities.size());
			} catch (Exception e) {
				System.err.println("average_statistics_data insert err execute: " + e);
			}
			System.out.println("BM_M023に登録しました。country: " + country + ", league: " + league +
					", score: " + connectScore);
		}
	}

	/**
	 * 登録メソッド
	 * @param country 国
	 * @param league リーグ
	 * @param team チーム
	 * @param situation 得点状況
	 * @param connectScore スコア連結
	 * @param updFlg 更新フラグ
	 * @param seq 通番
	 */
	private void registerTeamStaticsDetailData(String country, String league, String team, String situation,
			String connectScore, List<StatSummary> statList, boolean updFlg, String id) {
		if (updFlg) {
			List<String> selectList = UniairColumnMapUtil.getKeyMap(UniairConst.BM_M026);
			String[] selDataList = new String[selectList.size()];
			for (int i = 0; i < selectList.size(); i++) {
				selDataList[i] = selectList.get(i);
			}
			// 1つずつstatListの値を取得する
			List<String> stat = collectStatSummaryValues(statList);
			StringBuilder sBuilder = new StringBuilder();
			for (int ind = 5; ind < COUNTER + 5; ind++) {
				if (sBuilder.toString().length() > 0) {
					sBuilder.append(", ");
				}
				String sta = stat.get(ind - 5);
				sta = sta.replace("'", "''");
				sBuilder.append(" " + selDataList[ind + 1] + " = '" + sta + "'");
			}
			sBuilder.append(", update_time = '" + DateUtil.getSysDate() + "'");
			UpdateWrapper updateWrapper = new UpdateWrapper();

			String where = "id = '" + id + "'";
			updateWrapper.updateExecute(UniairConst.BM_M026, where,
					sBuilder.toString());
			System.out.println("BM_M026を更新しました。country: " + country + ", league: " + league +
					", team: " + team + ", score: " + connectScore);
		} else {
			List<AverageStatisticsDetailEntity> insertEntities = new ArrayList<AverageStatisticsDetailEntity>();
			AverageStatisticsDetailEntity statSummaries = new AverageStatisticsDetailEntity();
			statSummaries.setSituation(situation);
			statSummaries.setScore(connectScore);
			statSummaries.setCountry(country);
			statSummaries.setLeague(league);
			statSummaries.setTeam(team);
			statSummaries.setHomeExpStat(statList.get(0)); // インデックス0: homeExpStat
			statSummaries.setAwayExpStat(statList.get(1)); // インデックス1: awayExpStat
			statSummaries.setHomeDonationStat(statList.get(2)); // インデックス2: homeDonationStat
			statSummaries.setAwayDonationStat(statList.get(3)); // インデックス3: awayDonationStat
			statSummaries.setHomeShootAllStat(statList.get(4)); // インデックス4: homeShootAllStat
			statSummaries.setAwayShootAllStat(statList.get(5)); // インデックス5: awayShootAllStat
			statSummaries.setHomeShootInStat(statList.get(6)); // インデックス6: homeShootInStat
			statSummaries.setAwayShootInStat(statList.get(7)); // インデックス7: awayShootInStat
			statSummaries.setHomeShootOutStat(statList.get(8)); // インデックス8: homeShootOutStat
			statSummaries.setAwayShootOutStat(statList.get(9)); // インデックス9: awayShootOutStat
			statSummaries.setHomeBlockShootStat(statList.get(10)); // インデックス10: homeBlockShootStat
			statSummaries.setAwayBlockShootStat(statList.get(11)); // インデックス11: awayBlockShootStat
			statSummaries.setHomeBigChanceStat(statList.get(12)); // インデックス12: homeBigChanceStat
			statSummaries.setAwayBigChanceStat(statList.get(13)); // インデックス13: awayBigChanceStat
			statSummaries.setHomeCornerStat(statList.get(14)); // インデックス14: homeCornerStat
			statSummaries.setAwayCornerStat(statList.get(15)); // インデックス15: awayCornerStat
			statSummaries.setHomeBoxShootInStat(statList.get(16)); // インデックス16: homeBoxShootInStat
			statSummaries.setAwayBoxShootInStat(statList.get(17)); // インデックス17: awayBoxShootInStat
			statSummaries.setHomeBoxShootOutStat(statList.get(18)); // インデックス18: homeBoxShootOutStat
			statSummaries.setAwayBoxShootOutStat(statList.get(19)); // インデックス19: awayBoxShootOutStat
			statSummaries.setHomeGoalPostStat(statList.get(20)); // インデックス20: homeGoalPostStat
			statSummaries.setAwayGoalPostStat(statList.get(21)); // インデックス21: awayGoalPostStat
			statSummaries.setHomeGoalHeadStat(statList.get(22)); // インデックス22: homeGoalHeadStat
			statSummaries.setAwayGoalHeadStat(statList.get(23)); // インデックス23: awayGoalHeadStat
			statSummaries.setHomeKeeperSaveStat(statList.get(24)); // インデックス24: homeKeeperSaveStat
			statSummaries.setAwayKeeperSaveStat(statList.get(25)); // インデックス25: awayKeeperSaveStat
			statSummaries.setHomeFreeKickStat(statList.get(26)); // インデックス26: homeFreeKickStat
			statSummaries.setAwayFreeKickStat(statList.get(27)); // インデックス27: awayFreeKickStat
			statSummaries.setHomeOffsideStat(statList.get(28)); // インデックス28: homeOffsideStat
			statSummaries.setAwayOffsideStat(statList.get(29)); // インデックス29: awayOffsideStat
			statSummaries.setHomeFoulStat(statList.get(30)); // インデックス30: homeFoulStat
			statSummaries.setAwayFoulStat(statList.get(31)); // インデックス31: awayFoulStat
			statSummaries.setHomeYellowCardStat(statList.get(32)); // インデックス32: homeYellowCardStat
			statSummaries.setAwayYellowCardStat(statList.get(33)); // インデックス33: awayYellowCardStat
			statSummaries.setHomeRedCardStat(statList.get(34)); // インデックス34: homeRedCardStat
			statSummaries.setAwayRedCardStat(statList.get(35)); // インデックス35: awayRedCardStat
			statSummaries.setHomeSlowInStat(statList.get(36)); // インデックス36: homeSlowInStat
			statSummaries.setAwaySlowInStat(statList.get(37)); // インデックス37: awaySlowInStat
			statSummaries.setHomeBoxTouchStat(statList.get(38)); // インデックス38: homeBoxTouchStat
			statSummaries.setAwayBoxTouchStat(statList.get(39)); // インデックス39: awayBoxTouchStat
			statSummaries.setHomePassCountStat(statList.get(40)); // インデックス40: homePassCountStat
			statSummaries.setAwayPassCountStat(statList.get(41)); // インデックス41: awayPassCountStat
			statSummaries.setHomeFinalThirdPassCountStat(statList.get(42)); // インデックス42: homeFinalThirdPassCountStat
			statSummaries.setAwayFinalThirdPassCountStat(statList.get(43)); // インデックス43: awayFinalThirdPassCountStat
			statSummaries.setHomeCrossCountStat(statList.get(44)); // インデックス44: homeCrossCountStat
			statSummaries.setAwayCrossCountStat(statList.get(45)); // インデックス45: awayCrossCountStat
			statSummaries.setHomeTackleCountStat(statList.get(46)); // インデックス46: homeTackleCountStat
			statSummaries.setAwayTackleCountStat(statList.get(47)); // インデックス47: awayTackleCountStat
			statSummaries.setHomeClearCountStat(statList.get(48)); // インデックス48: homeClearCountStat
			statSummaries.setAwayClearCountStat(statList.get(49)); // インデックス49: awayClearCountStat
			statSummaries.setHomeInterceptCountStat(statList.get(50)); // インデックス50: homeInterceptCountStat
			statSummaries.setAwayInterceptCountStat(statList.get(51)); // インデックス51: awayInterceptCountStat
			insertEntities.add(statSummaries);

			CsvRegisterImpl csvRegisterImpl = new CsvRegisterImpl();
			try {
				csvRegisterImpl.executeInsert(UniairConst.BM_M026,
						insertEntities, 1, insertEntities.size());
			} catch (Exception e) {
				System.err.println("average_statistics_data_detail insert err execute: " + e);
			}
			System.out.println("BM_M026に登録しました。country: " + country + ", league: " + league +
					", team: " + team + ", score: " + connectScore);
		}
	}

	/**
	 * 取得メソッド
	 * @param country 国
	 * @param league リーグ
	 * @param team チーム
	 * @param connectScore スコア連結
	 * @param tableId テーブルID
	 */
	private AverageStatisticsOutputDTO getTeamStaticsData(String country, String league, String team,
			String connectScore, String tableId) {
		List<String> selDataAllList = UniairColumnMapUtil.getKeyMap(tableId);
		String[] selDataList = new String[selDataAllList.size() - 4];
		selDataList[0] = "id";
		for (int i = 5; i < selDataAllList.size(); i++) {
			selDataList[i - 4] = selDataAllList.get(i);
		}

		String where = "country = '" + country + "' and league = '" + league + "'";
		if (team != null) {
			where += (" and team = '" + team + "'");
		}
		if (connectScore != null) {
			where += (" and score = '" + connectScore + "'");
		}

		List<List<String>> selectResultList = null;
		SqlMainLogic select = new SqlMainLogic();
		try {
			selectResultList = select.executeSelect(null, tableId, selDataList,
					where, null, "1");
		} catch (Exception e) {
			throw new SystemException("", "", "", "err");
		}

		AverageStatisticsOutputDTO averageStatisticsOutputDTO = new AverageStatisticsOutputDTO();
		averageStatisticsOutputDTO.setUpdFlg(false);

		if (!selectResultList.isEmpty()) {
			averageStatisticsOutputDTO.setUpdFlg(true);
			averageStatisticsOutputDTO.setUpdId(selectResultList.get(0).get(0));
			averageStatisticsOutputDTO.setSelectList(selectResultList);
		}
		return averageStatisticsOutputDTO;
	}

	/**
	 * 登録メソッド
	 * @param country 国
	 * @param league リーグ
	 * @param team チーム
	 * @param connectScore スコア連結
	 */
	private void registerCsvTmpData(String country, String league, String team, String connectScore) {
		String[] selDataList = new String[2];
		selDataList[0] = "id";
		selDataList[1] = "game_count";

		String where = "country = '" + country + "' and league = '" + league + "' and "
				+ "team = '" + team + "' and score = '" + connectScore + "'";

		List<List<String>> selectResultList = null;
		SqlMainLogic select = new SqlMainLogic();
		try {
			selectResultList = select.executeSelect(null, UniairConst.BM_M027, selDataList,
					where, null, "1");
		} catch (Exception e) {
			throw new SystemException("", "", "", "err");
		}

		if (!selectResultList.isEmpty()) {
			String id = selectResultList.get(0).get(0);
			String game_count = String.valueOf(Integer.parseInt(selectResultList.get(0).get(1)) + 1);
			StringBuilder sBuilder = new StringBuilder();
			sBuilder.append("game_count = '" + game_count + "'");
			sBuilder.append(", update_time = '" + DateUtil.getSysDate() + "'");
			UpdateWrapper updateWrapper = new UpdateWrapper();

			String whereSub = "id = '" + id + "'";
			updateWrapper.updateExecute(UniairConst.BM_M027, whereSub,
					sBuilder.toString());
		} else {
			List<AverageStatisticsCsvTmpDataEntity> insertEntities = new ArrayList<AverageStatisticsCsvTmpDataEntity>();
			AverageStatisticsCsvTmpDataEntity statSummaries = new AverageStatisticsCsvTmpDataEntity();
			statSummaries.setScore(connectScore);
			statSummaries.setCountry(country);
			statSummaries.setLeague(league);
			statSummaries.setTeam(team);
			statSummaries.setGameCount("1");
			insertEntities.add(statSummaries);

			CsvRegisterImpl csvRegisterImpl = new CsvRegisterImpl();
			try {
				csvRegisterImpl.executeInsert(UniairConst.BM_M027,
						insertEntities, 1, insertEntities.size());
			} catch (Exception e) {
				System.err.println("average_statistics_csv_tmp_data insert err execute: " + e);
			}
		}
	}

	/**
	 * 統計データをリストにして返却する
	 * @param statSummaries
	 * @return
	 */
	private List<String> collectStatSummaryValues(List<StatSummary> statSummaries) {
		// 新しいリストを作成
		List<String> statValues = new ArrayList<>();
		for (StatSummary stat : statSummaries) {
			statValues.add(connectData(stat));
		}
		return statValues;
	}

	/**
	 * 統計データを連結する
	 * @param summary
	 * @return
	 */
	private String connectData(StatSummary summary) {
		return summary.getMin() + "," + summary.getMax() + "," +
				summary.getMean() + "," + summary.getSigma() + "," +
				String.valueOf(summary.getCount()) + "," +
				summary.getFeatureTimeMin() + "," + summary.getFeatureTimeMax() +
				"," + summary.getFeatureTimeMean() + "," + summary.getFeatureTimeSigma() +
				"," + String.valueOf(summary.getFeatureCount());
	}

	/**
	 * 統計データをリストにして返却する
	 * @param statSummaries
	 * @return
	 */
	private List<String> getSplitStringData(List<List<String>> list, int kankaku) {
		List<String> statValues = new ArrayList<>();
		for (List<String> da : list) {
			for (String d : da) {
				if (!d.contains(",")) {
					continue;
				}
				String[] split = d.split(",");
				statValues.add(split[kankaku]);
			}
		}
		return statValues;
	}

	/**
	 * 統計データを変数にして返却する
	 * @param statSummaries
	 * @return
	 */
	private String getSplitFeatureStringData(List<List<String>> list, int kankaku) {
		String statValues = null;
		for (List<String> da : list) {
			for (String d : da) {
				if (!d.contains(",")) {
					continue;
				}
				String[] split = d.split(",");
				statValues = split[kankaku];
				break;
			}
			break;
		}
		return statValues;
	}

	/**
	 * 統計データをリストにして返却する
	 * @param statSummaries
	 * @return
	 */
	private List<Integer> getSplitIntegerData(List<List<String>> list) {
		List<Integer> statValues = new ArrayList<>();
		for (List<String> da : list) {
			for (String d : da) {
				if (!d.contains(",")) {
					continue;
				}
				String[] split = d.split(",");
				statValues.add(Integer.parseInt(split[4]));
			}
		}
		return statValues;
	}

	/**
	 * 統計データを変数にして返却する
	 * @param statSummaries
	 * @return
	 */
	private Integer getSplitFeatureIntegerData(List<List<String>> list) {
		Integer statValues = -1;
		for (List<String> da : list) {
			for (String d : da) {
				if (!d.contains(",")) {
					continue;
				}
				String[] split = d.split(",");
				statValues = Integer.parseInt(split[9]);
				break;
			}
			break;
		}
		return statValues;
	}

	/**
	 * 平均*件数を計算し,リストにして返却する
	 * @param statSummaries
	 * @return
	 */
	private List<String> calcAveSum(List<String> exStrList, List<Integer> exIntList) {
		int ind = 0;
		for (String str : exStrList) {
			String result = "";
			String remarks = "";
			if (str.contains("/")) {
				List<String> splitData = ExecuteMainUtil.splitGroup(str);
				List<String> statValues = new ArrayList<>();
				for (String split : splitData) {
					String subRemarks = "";
					if (split.contains("%")) {
						subRemarks = "%";
						split = split.replace("%", "");
					}
					result = String.valueOf(Double.parseDouble(split) *
							exIntList.get(ind)) + subRemarks;
					statValues.add(result);
				}
				result = String.valueOf(statValues.get(0) + " (" + statValues.get(1) + "/"
						+ statValues.get(2) + ")");
			} else if (str.contains("%")) {
				remarks = "%";
				str = str.replace("%", "");
				result = String.valueOf(Double.parseDouble(str) * exIntList.get(ind)) + remarks;
			} else {
				result = String.valueOf(Double.parseDouble(str) * exIntList.get(ind));
			}
			exStrList.set(ind, result);
			ind++;
		}
		return exStrList;
	}

	/**
	 * 平均*件数を計算し,変数にして返却する
	 * @param statSummaries
	 * @return
	 */
	private String calcFeatureAveSum(String exStr, Integer exInt) {
		String statValues = String.format("%.1f",
				(Double.parseDouble(exStr.replace("'", "")) * exInt)) + "'";
		return statValues;
	}

	/**
	 * スコアが存在するか
	 * @param homeScore
	 * @param awayScore
	 * @param entities
	 * @return
	 */
	private boolean existsScore(int homeScore, int awayScore, List<ThresHoldEntity> entities) {
		for (ThresHoldEntity entity : entities) {
			if ("".equals(entity.getHomeScore()) ||
					"".equals(entity.getAwayScore())) {
				continue;
			}
			if ((Integer.parseInt(entity.getHomeScore()) == homeScore) &&
					(Integer.parseInt(entity.getAwayScore()) == awayScore)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * ハーフタイムの通番を特定するメソッド
	 * @param entityList レコードのリスト
	 * @return ハーフタイムの通番
	 */
	private String findHalfTimeSeq(List<ThresHoldEntity> entityList) {
		// 通番が最も大きいレコードの時がハーフタイムだと仮定
		// もしくは、最初に見つかるハーフタイム通番があればそれを返す
		for (ThresHoldEntity entity : entityList) {
			if (BookMakersCommonConst.FIRST_HALF_TIME.equals(entity.getTimes()) ||
					BookMakersCommonConst.HALF_TIME.equals(entity.getTimes())) {
				return entity.getSeq();
			}
		}
		// もしハーフタイムが見つからなければ、エラーやデフォルト値を返す（ケースに応じて）
		return "-1"; // エラー値（ハーフタイムが見つからない場合）
	}
}
