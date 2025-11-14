package dev.application.analyze.bm_m033;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.application.analyze.interf.AnalyzeEntityIF;
import dev.application.domain.repository.BookDataRepository;
import dev.application.domain.repository.RankHistoryStatRepository;
import dev.common.constant.BookMakersCommonConst;
import dev.common.entity.BookDataEntity;
import dev.common.exception.wrap.RootCauseWrapper;
import dev.common.logger.ManageLoggerComponent;
import dev.common.util.ExecuteMainUtil;

/**
 * BM_M033統計分析ロジック
 * @author shiraishitoshio
 *
 */
@Component
public class RankHistoryStat implements AnalyzeEntityIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = RankHistoryStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = RankHistoryStat.class.getSimpleName();

	/** 実行モード */
	private static final String EXEC_MODE = "BM_M033_RANK_HISTORY";

	/** BookDataRepositoryレポジトリクラス */
	@Autowired
	private BookDataRepository bookDataRepository;

	/** RankHistoryStatRepositoryレポジトリクラス */
	@Autowired
	private RankHistoryStatRepository rankHistoryStatRepository;

	/** ログ管理ラッパー*/
	@Autowired
	private RootCauseWrapper rootCauseWrapper;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * {@inheritDoc}
	 * @throws Exception
	 */
	@Override
	public void calcStat(Map<String, Map<String, List<BookDataEntity>>> entities) {
		final String METHOD_NAME = "calcStat";
		// ログ出力
		this.manageLoggerComponent.init(EXEC_MODE, null);
		this.manageLoggerComponent.debugStartInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		// 同一国,リーグで精査したかつ「終了済」に限定したデータの場合に順位が決定している状態かそうでないかを調査

		// 全リーグ・国を走査
		// その国・リーグのチームマップ
		for (Map.Entry<String, Map<String, List<BookDataEntity>>> entry : entities.entrySet()) {
			Map<String, List<BookDataEntity>> entrySub = entry.getValue();
			for (List<BookDataEntity> entityList : entrySub.values()) {
				// null や空リストはスキップ
				if (entityList == null || entityList.isEmpty())
					continue;
				// 終了済に関するデータのみ
				int match = -1;
				int homeRank = -1;
				int awayRank = -1;
				for (BookDataEntity entity : entityList) {
					if (!entity.getGameTeamCategory().isBlank()) {
						match = ExecuteMainUtil.extractRoundNumbers(entity.getGameTeamCategory());
					}
					if (BookMakersCommonConst.FIN.equals(entity.getTime())) {
						List<String> countryLeague = ExecuteMainUtil
								.getCountryLeagueByRegex(entity.getGameTeamCategory());

						// 基本はアプリ稼働の同タイミングでhome, awayが同じチームがある、
						// home,awayに同じチームが登録される場合でもmatchが異なる
						// 順位が設定済み
						if (!entity.getHomeRank().isBlank()) {
							// 順位が決定されている場合のDTO
							RankHistoryEntity rankHistoryEntity = new RankHistoryEntity();
							homeRank = Integer.parseInt(entity.getHomeRank().replace(".0", ""));
							rankHistoryEntity.setCountry(countryLeague.get(0));
							rankHistoryEntity.setLeague(countryLeague.get(1));
							rankHistoryEntity.setTeam(entity.getHomeTeamName());
							rankHistoryEntity.setMatch(match);
							rankHistoryEntity.setRank(homeRank);
							// matchが異なるので基本的にはここにこない
							if (this.rankHistoryStatRepository.select(rankHistoryEntity) > 0) {
								int result = this.rankHistoryStatRepository.update(rankHistoryEntity);
								if (result != 1) {
									String messageCd = "更新エラー";
									this.rootCauseWrapper.throwUnexpectedRowCount(
											PROJECT_NAME, CLASS_NAME, METHOD_NAME,
											messageCd,
											1, result,
											null);
								}
								String messageCd = "ホーム更新件数";
								this.manageLoggerComponent.debugInfoLog(
										PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null, "BM_M033 更新件数: 1件");
							} else {
								int result = this.rankHistoryStatRepository.insert(rankHistoryEntity);
								if (result != 1) {
									String messageCd = "新規登録エラー";
									this.rootCauseWrapper.throwUnexpectedRowCount(
											PROJECT_NAME, CLASS_NAME, METHOD_NAME,
											messageCd,
											1, result,
											null);
								}
								String messageCd = "ホーム登録件数";
								this.manageLoggerComponent.debugInfoLog(
										PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null, "BM_M033 登録件数: 1件");
							}
						}

						// 基本は同タイミングでhome, awayが同じチームがある、
						// home,awayに同じチームが登録される場合はmatchが異なる
						if (!entity.getAwayRank().isBlank()) {
							// 順位が決定されている場合のDTO
							RankHistoryEntity rankHistoryEntity2 = new RankHistoryEntity();
							awayRank = Integer.parseInt(entity.getAwayRank().replace(".0", ""));
							rankHistoryEntity2.setCountry(countryLeague.get(0));
							rankHistoryEntity2.setLeague(countryLeague.get(1));
							rankHistoryEntity2.setTeam(entity.getAwayTeamName());
							rankHistoryEntity2.setMatch(match);
							rankHistoryEntity2.setRank(awayRank);
							if (this.rankHistoryStatRepository.select(rankHistoryEntity2) > 0) {
								int result = this.rankHistoryStatRepository.update(rankHistoryEntity2);
								if (result != 1) {
									String messageCd = "更新エラー";
									this.rootCauseWrapper.throwUnexpectedRowCount(
											PROJECT_NAME, CLASS_NAME, METHOD_NAME,
											messageCd,
											1, result,
											null);
								}
								String messageCd = "アウェー更新件数";
								this.manageLoggerComponent.debugInfoLog(
										PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null, "BM_M033 更新件数: 1件");
							} else {
								int result = this.rankHistoryStatRepository.insert(rankHistoryEntity2);
								if (result != 1) {
									String messageCd = "新規登録エラー";
									this.rootCauseWrapper.throwUnexpectedRowCount(
											PROJECT_NAME, CLASS_NAME, METHOD_NAME,
											messageCd,
											1, result,
											null);
								}
								String messageCd = "アウェー登録件数";
								this.manageLoggerComponent.debugInfoLog(
										PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null, "BM_M033 登録件数: 1件");
							}
						}

						// 片方だけ
						if (homeRank == -1 || awayRank == -1) {
							try {
								List<TeamPoints> teamPoints = getOriginRank(countryLeague.get(0),
										countryLeague.get(1), String.valueOf(match));
								List<RankedTeamPoints> ranks = rankTeams(teamPoints);
								for (RankedTeamPoints pointDTO : ranks) {
									RankHistoryEntity rankHistoryEntity = new RankHistoryEntity();
									rankHistoryEntity.setCountry(countryLeague.get(0));
									rankHistoryEntity.setLeague(countryLeague.get(1));
									rankHistoryEntity.setTeam(pointDTO.getTeam());
									rankHistoryEntity.setMatch(match);
									rankHistoryEntity.setRank(pointDTO.getRank());
									if (this.rankHistoryStatRepository.select(rankHistoryEntity) > 0) {
										int result = this.rankHistoryStatRepository.update(rankHistoryEntity);
										if (result != 1) {
											String messageCd = "順位後付け更新エラー";
											this.rootCauseWrapper.throwUnexpectedRowCount(
													PROJECT_NAME, CLASS_NAME, METHOD_NAME,
													messageCd,
													1, result,
													null);
										}
										String messageCd = "順位後付け更新件数";
										this.manageLoggerComponent.debugInfoLog(
												PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null,
												"BM_M033 更新件数: 1件");
									} else {
										int result = this.rankHistoryStatRepository.insert(rankHistoryEntity);
										if (result != 1) {
											String messageCd = "順位後付け新規登録エラー";
											this.rootCauseWrapper.throwUnexpectedRowCount(
													PROJECT_NAME, CLASS_NAME, METHOD_NAME,
													messageCd,
													1, result,
													null);
										}
										String messageCd = "順位後付け登録件数";
										this.manageLoggerComponent.debugInfoLog(
												PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null,
												"BM_M033 登録件数: 1件");
									}
								}
							} catch (Exception e) {
								String messageCd = "DBエラー";
								this.rootCauseWrapper.throwUnexpectedRowCount(
										PROJECT_NAME, CLASS_NAME, METHOD_NAME,
										messageCd,
										-99, -99,
										null);
							}
						}
					}
				}
				if (match != -1) {
					break;
				}
			}
		}

		// endLog
		this.manageLoggerComponent.debugEndInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		this.manageLoggerComponent.clear();
	}

	/**
	 * 国,リーグから勝利,敗北,引き分け,得失点差を導出し,<br>
	 * 現在実施済みの試合から最終的な順位を出す
	 * @param country 国
	 * @param league リーグ
	 * @param match 節
	 * @return
	 */
	private List<TeamPoints> getOriginRank(String country, String league, String match) throws Exception {
		return this.bookDataRepository.selectTeamPoints(country, league, match);
	}

	/**
	 * 順位付け
	 * @param teamPoints
	 * @return
	 */
	private List<RankedTeamPoints> rankTeams(List<TeamPoints> teamPoints) {

		// 1. ソート（勝ち点 → 得失点差 → 得点）
		List<TeamPoints> sorted = teamPoints.stream()
				.sorted((a, b) -> {
					int cmpPoints = b.getPoints().compareTo(a.getPoints());
					if (cmpPoints != 0)
						return cmpPoints;

					int goalDiffA = a.getGf() - a.getGa();
					int goalDiffB = b.getGf() - b.getGa();
					int cmpGoalDiff = Integer.compare(goalDiffB, goalDiffA);
					if (cmpGoalDiff != 0)
						return cmpGoalDiff;

					return b.getGf().compareTo(a.getGf());
				})
				.collect(Collectors.toList());

		// 2. 順位付け
		List<RankedTeamPoints> rankedList = new ArrayList<>();

		int rank = 1;
		int index = 0;

		Integer prevPoints = null;
		Integer prevGoalDiff = null;
		Integer prevGf = null;

		for (TeamPoints tp : sorted) {
			int goalDiff = tp.getGf() - tp.getGa();

			// 前のチームと比較して順位決定
			if (prevPoints != null &&
					tp.getPoints().equals(prevPoints) &&
					goalDiff == prevGoalDiff &&
					tp.getGf().equals(prevGf)) {
				// 同順位
			} else {
				// 新しい順位
				rank = index + 1;
			}

			RankedTeamPoints rtp = new RankedTeamPoints();
			rtp.setRank(rank);
			rtp.setTeam(tp.getTeam());
			rtp.setPoints(tp.getPoints());
			rtp.setGf(tp.getGf());
			rtp.setGa(tp.getGa());
			rtp.setPlayed(tp.getPlayed());

			rankedList.add(rtp);

			prevPoints = tp.getPoints();
			prevGoalDiff = goalDiff;
			prevGf = tp.getGf();

			index++;
		}

		return rankedList;
	}

}
