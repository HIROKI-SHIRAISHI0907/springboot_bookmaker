package dev.application.analyze.bm_m017_bm_m018;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.application.analyze.common.util.BookMakersCommonConst;
import dev.application.analyze.interf.AnalyzeEntityIF;
import dev.application.domain.repository.LeagueScoreTimeBandStatsRepository;
import dev.application.domain.repository.LeagueScoreTimeBandStatsSplitScoreRepository;
import dev.common.entity.BookDataEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.util.ExecuteMainUtil;

/**
 * BM_M017_BM_M018統計分析ロジック
 * @author shiraishitoshio
 *
 */
@Component
@Transactional
public class LeagueScoreTimeBandStat implements AnalyzeEntityIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = LeagueScoreTimeBandStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = LeagueScoreTimeBandStat.class.getSimpleName();

	/** 実行モード */
	private static final String EXEC_MODE = "BM_M017_BM_M018_LEAGUE_SCORE_TIME_BAND";

	/** within_data_scored_counter */
	private static String MAIN = "within_data_scored_counter";

	/** within_data_scored_counter_detail */
	private static String DETAIL = "within_data_scored_counter_detail";

	/** LeagueScoreTimeBandStatsRepositoryレポジトリクラス */
	@Autowired
	private LeagueScoreTimeBandStatsRepository leagueScoreTimeBandStatsRepository;

	/** LeagueScoreTimeBandStatsSplitScoreRepositoryレポジトリクラス */
	@Autowired
	private LeagueScoreTimeBandStatsSplitScoreRepository leagueScoreTimeBandStatsSplitScoreRepository;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void calcStat(Map<String, Map<String, List<BookDataEntity>>> entities) {
		final String METHOD_NAME = "calcStat";
		// ログ出力
		this.manageLoggerComponent.init(EXEC_MODE, null);
		this.manageLoggerComponent.debugStartInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		// スレッドセーフなマップ構造(Map<"leagueKey", Map<"テーブル名", Map<"home_sum_score away_sum_scoreの2段構成, sum_score", Integer>>>)
		Map<String, Map<Integer, LeagueScoreMainData>> mainMap = new ConcurrentHashMap<>();
		entities.entrySet().parallelStream().forEach(entry -> {
			String leagueKey = entry.getKey(); // 例: "Japan-J1"
			Map<String, List<BookDataEntity>> matchMap = entry.getValue();
			for (List<BookDataEntity> dataList : matchMap.values()) {
				int prevSumScore = 0;
				int prevHomeScore = 0;
				int prevAwayScore = 0;
				for (BookDataEntity e : dataList) {
					// ゴール取り消しはスキップ
					if (BookMakersCommonConst.GOAL_DELETE.equals(e.getJudge()))
						continue;
					// その時間の得点を取得し,差分があった場合,現在のスコアをeachMapに保存する
					int currentHomeScore = Integer.parseInt(e.getHomeScore());
					int currentAwayScore = Integer.parseInt(e.getAwayScore());
					int currentScore = currentHomeScore + currentAwayScore;
					int diffHomeScore = currentHomeScore - prevHomeScore;
					int diffAwayScore = currentAwayScore - prevAwayScore;
					int diffScore = currentScore - prevSumScore;
					// ホーム側で差分がある場合
					if (diffHomeScore > 0) {
						String timeRange = ExecuteMainUtil.classifyMatchTime(e.getTime());
						String mainHomeScore = String.valueOf(currentHomeScore);
						String mainAwayScore = String.valueOf(currentAwayScore);
						String target = "1";
						String search = "1";
						// homeのtimeRangeが対象設定値
						setMainMap(mainMap, leagueKey, DETAIL, null,
								null, mainHomeScore, mainAwayScore, timeRange, null, target, search);
					}
					// アウェー側で差分がある場合
					if (diffAwayScore > 0) {
						String timeRange = ExecuteMainUtil.classifyMatchTime(e.getTime());
						String mainHomeScore = String.valueOf(currentHomeScore);
						String mainAwayScore = String.valueOf(currentAwayScore);
						String target = "1";
						String search = "1";
						// homeのtimeRangeが対象設定値
						setMainMap(mainMap, leagueKey, DETAIL, null,
								null, mainHomeScore, mainAwayScore, null, timeRange, target, search);
					}
					// 全体で差分がある場合
					if (diffScore > 0) {
						String timeRange = ExecuteMainUtil.classifyMatchTime(e.getTime());
						String mainSumScore = String.valueOf(currentHomeScore + currentAwayScore);
						String target = "1";
						String search = "1";
						setMainMap(mainMap, leagueKey, MAIN, mainSumScore,
								timeRange, null, null, null, null, target, search);
					}

					prevHomeScore = currentHomeScore;
					prevAwayScore = currentAwayScore;
					prevSumScore = currentScore;
				}

			}
		});

		// 時間範囲、探索件数集計
		Map<String, Map<String, StringBuilder>> timeRangeMap = new HashMap<>();
		Map<String, Map<String, Integer>> countMap = new HashMap<>();
		for (Map.Entry<String, Map<Integer, LeagueScoreMainData>> mapEntry : mainMap.entrySet()) {
			String leagueKey = mapEntry.getKey();
			String[] leagueCountrySp = ExecuteMainUtil.splitLeagueInfo(leagueKey);
			leagueKey = leagueCountrySp[0] + "-" + leagueCountrySp[1];
			Map<Integer, LeagueScoreMainData> matchMap = mapEntry.getValue();

			// MAIN データを集約
			for (LeagueScoreMainData subEntity : matchMap.values()) {
				String table = subEntity.getTable();
				if (MAIN.equals(table)) {
					String sumScore = subEntity.getSumScoreValue();
					String timeRange = subEntity.getTimeRangeArea();
					String mainKey = leagueKey + "-" + MAIN;
					// 時間帯を追記
					StringBuilder sb = timeRangeMap.computeIfAbsent(mainKey, k -> new HashMap<>())
							.computeIfAbsent(sumScore + "-S", k -> new StringBuilder());
					if (sb.length() > 0)
						sb.append(",");
					sb.append(timeRange);

					// 件数を加算
					countMap
							.computeIfAbsent(mainKey, k -> new HashMap<>())
							.merge(sumScore + "-S", 1, Integer::sum);
				}
			}

			// DETAILデータ
			for (LeagueScoreMainData subEntity : matchMap.values()) {
				if (DETAIL.equals(subEntity.getTable())) {
					// スコア取得
					String homeScore = subEntity.getHomeScoreValue();
					String awayScore = subEntity.getAwayScoreValue();
					// 時間範囲取得
					String timeHomeRange = subEntity.getHomeTimeRangeArea();
					String timeAwayRange = subEntity.getAwayTimeRangeArea();

					// キーに leagueKey を含めてユニークにする
					String mainKey = leagueKey + "-" + DETAIL;

					// 時間帯を追記（ホーム）
					if (timeHomeRange != null) {
						StringBuilder sbHome = timeRangeMap.computeIfAbsent(mainKey, k -> new HashMap<>())
								.computeIfAbsent(homeScore + "-H", k -> new StringBuilder());
						if (sbHome.length() > 0)
							sbHome.append(",");
						sbHome.append(timeHomeRange);
					}

					// 時間帯を追記（アウェー）
					if (timeAwayRange != null) {
						StringBuilder sbAway = timeRangeMap.computeIfAbsent(mainKey, k -> new HashMap<>())
								.computeIfAbsent(awayScore + "-A", k -> new StringBuilder());
						if (sbAway.length() > 0)
							sbAway.append(",");
						sbAway.append(timeAwayRange);
					}

					// 件数カウント（1エントリに対して1カウントでOK）
					countMap
							.computeIfAbsent(mainKey, k -> new HashMap<>())
							.merge(homeScore + "-H", 1, Integer::sum);
					countMap
							.computeIfAbsent(mainKey, k -> new HashMap<>())
							.merge(awayScore + "-A", 1, Integer::sum);
				}
			}
		}

		// 保持マップに格納したデータから分類する
		for (Entry<String, Map<String, StringBuilder>> map : timeRangeMap.entrySet()) {
			// String: leagueKey + "-" + MAIN, leagueKey + "-" + DETAIL
			// String: sumScore + "-S", homeScore + "-H", awayScore + "-A"
			// StringBuilder: timeRange, timeHomeRange, timeAwayRange
			String mainKey = map.getKey();
			String[] split = mainKey.split("-");
			String country = split[0];
			String league = split[1];
			String table = split[2];
			Map<String, StringBuilder> builder = map.getValue();
			String mainScore = "";
			String mainHomeScore = "";
			String mainAwayScore = "";
			String sortTimeRange = "";
			String sortTimeHomeRange = "";
			String sortTimeAwayRange = "";
			for (Entry<String, StringBuilder> subMap : builder.entrySet()) {
				String scoreKey = subMap.getKey();
				String score = scoreKey.split("-")[0];
				String ha = scoreKey.split("-")[1];

				// target,searchは同一
				int target = countMap.get(mainKey).get(scoreKey);
				int search = target;
				// データ取得
				if (MAIN.equals(table) && "S".equals(ha)) {
					mainScore = score;
					String timeRange = subMap.getValue().toString();
					// timeRangeをソート
					sortTimeRange = sortTimeRanges(timeRange);
				} else {
					// ホームスコア,アウェースコア振り分け
					if (DETAIL.equals(table) && "H".equals(ha)) {
						mainHomeScore = score;
						String timeHomeRange = subMap.getValue().toString();
						// timeRangeをソート
						sortTimeHomeRange = sortTimeRanges(timeHomeRange);
					} else if (DETAIL.equals(table) && "A".equals(ha)) {
						mainAwayScore = score;
						String timeAwayRange = subMap.getValue().toString();
						// timeRangeをソート
						sortTimeAwayRange = sortTimeRanges(timeAwayRange);
					}
				}

				if (!mainScore.isBlank()) {
					LeagueScoreTimeBandOutputDTO dto = getMainData(
							country, league, mainScore, sortTimeRange);
					if (dto.isUpdFlg()) {
						String id = dto.getId();
						String targetUpd = String.valueOf(Integer.parseInt(dto.getTarget()) + 1);
						String searchUpd = String.valueOf(Integer.parseInt(dto.getSearch()) + 1);
						setUpdData(MAIN, id, String.valueOf(targetUpd), String.valueOf(searchUpd),
								country, league);
					} else {
						// homeのtimeRangeが対象設定値
						String targetReg = "1";
						String searchReg = "1";
						setRegData(MAIN, mainScore, sortTimeRange,
								null, null, null, null,
								targetReg, searchReg, country, league);
					}
					mainScore = "";
					sortTimeRange = "";
				}

				if (!mainHomeScore.isBlank() || !mainAwayScore.isBlank()) {
					LeagueScoreTimeBandOutputDTO dto = getDetailData(
							country, league, mainHomeScore, mainAwayScore, sortTimeHomeRange, sortTimeAwayRange);
					if (dto.isUpdFlg()) {
						String id = dto.getId();
						String targetUpd = String.valueOf(Integer.parseInt(dto.getTarget()) + 1);
						String searchUpd = String.valueOf(Integer.parseInt(dto.getSearch()) + 1);
						setUpdData(DETAIL, id, String.valueOf(targetUpd), String.valueOf(searchUpd),
								country, league);
					} else {
						// homeのtimeRangeが対象設定値
						String targetReg = "1";
						String searchReg = "1";
						setRegData(DETAIL, null, null,
								mainHomeScore, mainAwayScore, sortTimeHomeRange, sortTimeAwayRange,
								targetReg, searchReg, country, league);
					}
					mainHomeScore = "";
					mainAwayScore = "";
					sortTimeHomeRange = "";
					sortTimeAwayRange = "";
				}
			}

		}

		// endLog
		this.manageLoggerComponent.debugEndInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		this.manageLoggerComponent.clear();
	}

	/**
	 * 取得データ
	 * @param country 国
	 * @param league リーグ
	 * @param sumScore 合計スコア
	 * @param timeRange 時間範囲
	 * @return CountryLeagueSummaryOutputDTO
	 */
	private LeagueScoreTimeBandOutputDTO getMainData(String country, String league,
			String sumScore, String timeRange) {
		LeagueScoreTimeBandOutputDTO leagueScoreTimeBandOutputDTO = new LeagueScoreTimeBandOutputDTO();
		List<LeagueScoreTimeBandStatsEntity> datas = this.leagueScoreTimeBandStatsRepository.findData(country,
				league, sumScore, timeRange);
		if (!datas.isEmpty()) {
			leagueScoreTimeBandOutputDTO.setUpdFlg(true);
			leagueScoreTimeBandOutputDTO.setId(datas.get(0).getId());
			leagueScoreTimeBandOutputDTO.setTarget(datas.get(0).getTarget());
			leagueScoreTimeBandOutputDTO.setSearch(datas.get(0).getSearch());
		} else {
			leagueScoreTimeBandOutputDTO.setUpdFlg(false);
		}
		return leagueScoreTimeBandOutputDTO;
	}

	/**
	 * 取得データ
	 * @param country 国
	 * @param league リーグ
	 * @param homeSumScore 合計スコア
	 * @param awaySumScore 合計スコア
	 * @param homeTimeRange ホーム時間範囲
	 * @param awayTimeRange アウェー時間範囲
	 * @return CountryLeagueSummaryOutputDTO
	 */
	private LeagueScoreTimeBandOutputDTO getDetailData(String country, String league,
			String homeSumScore, String awaySumScore, String homeTimeRange, String awayTimeRange) {
		LeagueScoreTimeBandOutputDTO leagueScoreTimeBandOutputDTO = new LeagueScoreTimeBandOutputDTO();
		List<LeagueScoreTimeBandStatsSplitScoreEntity> datas = this.leagueScoreTimeBandStatsSplitScoreRepository
				.findData(country,
						league, homeSumScore, awaySumScore, homeTimeRange, awayTimeRange);
		if (!datas.isEmpty()) {
			leagueScoreTimeBandOutputDTO.setUpdFlg(true);
			leagueScoreTimeBandOutputDTO.setId(datas.get(0).getId());
			leagueScoreTimeBandOutputDTO.setTarget(datas.get(0).getTarget());
			leagueScoreTimeBandOutputDTO.setSearch(datas.get(0).getSearch());
		} else {
			leagueScoreTimeBandOutputDTO.setUpdFlg(false);
		}
		return leagueScoreTimeBandOutputDTO;
	}

	/**
	 * 全体保持マップ
	 * @param resultMap マップ
	 * @param leagueKey リーグキー
	 * @param table テーブル
	 * @param sumScore 合計スコア
	 * @param timeRange 時間範囲
	 * @param homeSumScore ホーム合計スコア
	 * @param awaySumScore アウェー合計スコア
	 * @param homeTimeRange ホーム時間範囲
	 * @param awayTimeRange アウェー時間範囲
	 * @param target 対象数
	 * @param search 探索数
	 */
	private synchronized void setMainMap(Map<String, Map<Integer, LeagueScoreMainData>> resultMap,
			String leagueKey, String table, String sumScore, String timeRange,
			String homeSumScore, String awaySumScore, String homeTimeRange, String awayTimeRange,
			String target, String search) {
		// leagueKey に対応する内側の Map を取得または作成
		Map<Integer, LeagueScoreMainData> innerMap = resultMap.computeIfAbsent(leagueKey, k -> new HashMap<>());
		// 通番は 1 から順番に（既存サイズ + 1）
		int nextSeqNo = innerMap.size() + 1;
		// Map に登録
		innerMap.put(nextSeqNo,
				new LeagueScoreMainData(sumScore, timeRange, homeSumScore, awaySumScore,
						homeTimeRange, awayTimeRange, target, search, table));
	}

	/**
	 * 登録マップ
	 * @param table テーブル
	 * @param sumScore 合計スコア
	 * @param timeRange 時間範囲
	 * @param homeSumScore ホーム合計スコア
	 * @param awaySumScore アウェー合計スコア
	 * @param homeTimeRange ホーム時間範囲
	 * @param awayTimeRange アウェー時間範囲
	 * @param target 対象数
	 * @param search 探索数
	 * @param country 国
	 * @param league リーグ
	 */
	private synchronized void setRegData(String table, String sumScore, String timeRange,
			String homeSumScore, String awaySumScore, String homeTimeRange, String awayTimeRange,
			String target, String search, String country, String league) {
		final String METHOD_NAME = "setRegData";
		//		// leagueKey に対応する内側の Map を取得または作成
		//		Map<Integer, LeagueScoreRegisterData> innerMap = resultMap.computeIfAbsent(leagueKey, k -> new HashMap<>());
		//		// 通番は 1 から順番に（既存サイズ + 1）
		//		int nextSeqNo = innerMap.size() + 1;
		//		// Map に登録
		//		innerMap.put(nextSeqNo,
		//				new LeagueScoreRegisterData(sumScore, timeRange, homeSumScore, awaySumScore,
		//						homeTimeRange, awayTimeRange, target, search, table));
		String fillChar = setLoggerFillChar(country, league);
		if (MAIN.equals(table)) {
			// 登録
			LeagueScoreTimeBandStatsEntity leagueScoreTimeBandStatsEntity = new LeagueScoreTimeBandStatsEntity();
			leagueScoreTimeBandStatsEntity.setCountry(country);
			leagueScoreTimeBandStatsEntity.setLeague(league);
			leagueScoreTimeBandStatsEntity.setSumScoreValue(sumScore);
			leagueScoreTimeBandStatsEntity.setTimeRangeArea(timeRange);
			leagueScoreTimeBandStatsEntity.setTarget(target);
			leagueScoreTimeBandStatsEntity.setSearch(search);
			int result = this.leagueScoreTimeBandStatsRepository.insert(leagueScoreTimeBandStatsEntity);
			if (result != 1) {
				String messageCd = "新規登録エラー";
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null);
				this.manageLoggerComponent.createSystemException(
						PROJECT_NAME,
						CLASS_NAME,
						METHOD_NAME,
						messageCd,
						null);

			}
			String messageCd = "登録件数";
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, fillChar, "BM_M017 登録件数: 1件");
		} else if (DETAIL.equals(table)) {
			// 登録
			LeagueScoreTimeBandStatsSplitScoreEntity leagueScoreTimeBandStatsSplitScoreEntity = new LeagueScoreTimeBandStatsSplitScoreEntity();
			leagueScoreTimeBandStatsSplitScoreEntity.setCountry(country);
			leagueScoreTimeBandStatsSplitScoreEntity.setLeague(league);
			leagueScoreTimeBandStatsSplitScoreEntity.setHomeScoreValue(homeSumScore);
			leagueScoreTimeBandStatsSplitScoreEntity.setAwayScoreValue(awaySumScore);
			leagueScoreTimeBandStatsSplitScoreEntity.setHomeTimeRangeArea(homeTimeRange);
			leagueScoreTimeBandStatsSplitScoreEntity.setAwayTimeRangeArea(awayTimeRange);
			leagueScoreTimeBandStatsSplitScoreEntity.setTarget(target);
			leagueScoreTimeBandStatsSplitScoreEntity.setSearch(search);
			int result = this.leagueScoreTimeBandStatsSplitScoreRepository
					.insert(leagueScoreTimeBandStatsSplitScoreEntity);
			if (result != 1) {
				String messageCd = "新規登録エラー";
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null);
				this.manageLoggerComponent.createSystemException(
						PROJECT_NAME,
						CLASS_NAME,
						METHOD_NAME,
						messageCd,
						null);

			}
			String messageCd = "登録件数";
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, fillChar, "BM_M018 登録件数: 1件");
		}

	}

	/**
	 * 更新マップ
	 * @param table テーブル
	 * @param id ID
	 * @param target 対象数
	 * @param search 探索数
	 * @param country 国
	 * @param league リーグ
	 */
	private synchronized void setUpdData(String table, String id, String target,
			String search, String country, String league) {
		final String METHOD_NAME = "setUpdData";

		//		// leagueKey に対応する内側の Map を取得または作成
		//		Map<Integer, LeagueScoreUpdateData> innerMap = resultMap.computeIfAbsent(leagueKey, k -> new HashMap<>());
		//		// 通番は 1 から順番に（既存サイズ + 1）
		//		int nextSeqNo = innerMap.size() + 1;
		//		// Map に登録
		//		innerMap.put(nextSeqNo,
		//				new LeagueScoreUpdateData(id, target, search, table, timeRange));
		String fillChar = setLoggerFillChar(country, league);
		if (MAIN.equals(table)) {
			int result = this.leagueScoreTimeBandStatsRepository.update(id, target, search);
			if (result != 1) {
				String messageCd = "更新エラー";
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null);
				this.manageLoggerComponent.createSystemException(
						PROJECT_NAME,
						CLASS_NAME,
						METHOD_NAME,
						messageCd,
						null);
			}
			String messageCd = "更新件数";
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, fillChar, "BM_M017 更新件数: 1件");
		} else if (DETAIL.equals(table)) {
			int result = this.leagueScoreTimeBandStatsSplitScoreRepository
					.update(id, target, search);
			if (result != 1) {
				String messageCd = "更新エラー";
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null);
				this.manageLoggerComponent.createSystemException(
						PROJECT_NAME,
						CLASS_NAME,
						METHOD_NAME,
						messageCd,
						null);
			}
			String messageCd = "更新件数";
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, fillChar, "BM_M018 更新件数: 1件");
		}
	}

	/**
	 * 時間範囲のソート
	 * @param input 時間範囲
	 * @return
	 */
	private synchronized String sortTimeRanges(String input) {
		if (input == null || input.isEmpty())
			return input;

		return Arrays.stream(input.split(","))
				.sorted(Comparator.comparingInt(timeRange -> {
					String[] parts = timeRange.split("〜");
					return Integer.parseInt(parts[0].trim());
				}))
				.collect(Collectors.joining(","));
	}

	/**
	 * 埋め字設定
	 * @param country
	 * @param league
	 * @return
	 */
	private String setLoggerFillChar(String country, String league) {
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append("国: " + country + ", ");
		stringBuilder.append("リーグ: " + league);
		return stringBuilder.toString();
	}

}
