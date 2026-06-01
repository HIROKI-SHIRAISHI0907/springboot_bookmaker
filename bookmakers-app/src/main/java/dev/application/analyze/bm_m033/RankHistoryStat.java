package dev.application.analyze.bm_m033;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.application.domain.repository.bm.BookDataRepository;
import dev.application.domain.repository.master.CountryLeagueSeasonMasterRepository;
import dev.common.constant.BookMakersCommonConst;
import dev.common.constant.MessageCdConst;
import dev.common.entity.BookDataEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.util.ExecuteMainUtil;

/**
 * BM_M033統計分析ロジック
 * 集計・判定のみ担当
 */
@Component
public class RankHistoryStat {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = RankHistoryStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = RankHistoryStat.class.getName();

	/** 実行モード */
	private static final String EXEC_MODE = "BM_M033_RANK_HISTORY";

	/** BM_STAT_NUMBER */
	private static final String BM_NUMBER = "BM_M033";

	/** BookDataRepositoryレポジトリクラス */
	@Autowired
	private BookDataRepository bookDataRepository;

	/** CountryLeagueSeasonMasterRepositoryレポジトリクラス */
	@Autowired
	private CountryLeagueSeasonMasterRepository countryLeagueSeasonMasterRepository;

	/** Writer */
	@Autowired
	private RankHistoryWriter rankHistoryWriter;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * 実行メソッド
	 */
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public void calcStat(Map<String, Map<String, List<BookDataEntity>>> entities, boolean manualFlg) {
		final String METHOD_NAME = "calcStat";

		this.manageLoggerComponent.init(EXEC_MODE, null);
		this.manageLoggerComponent.debugStartInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		try {
			if (entities == null || entities.isEmpty()) {
				this.manageLoggerComponent.debugEndInfoLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME);
				return;
			}

			// 同じ match の後付け順位を何回も実行しないための制御
			Set<String> processedBackfillKeys = new HashSet<>();

			for (Map<String, List<BookDataEntity>> entrySub : entities.values()) {
				if (entrySub == null || entrySub.isEmpty()) {
					continue;
				}

				for (List<BookDataEntity> entityList : entrySub.values()) {
					if (entityList == null || entityList.isEmpty()) {
						continue;
					}

					for (BookDataEntity entity : entityList) {
						if (entity == null) {
							continue;
						}

						try {
							processEntity(entity, processedBackfillKeys, METHOD_NAME, manualFlg);
						} catch (Exception e) {
							String messageCd = MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION;
							this.manageLoggerComponent.debugErrorLog(
									PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e,
									"RankHistory processing failed. category="
											+ safe(entity.getGameTeamCategory())
											+ ", homeTeam=" + safe(entity.getHomeTeamName())
											+ ", awayTeam=" + safe(entity.getAwayTeamName()));
						}
					}
				}
			}

			this.manageLoggerComponent.debugEndInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		} finally {
			this.manageLoggerComponent.clear();
		}
	}

	/**
	 * 1試合ぶん処理
	 */
	private void processEntity(BookDataEntity entity,
			Set<String> processedBackfillKeys,
			String methodName,
			boolean manualFlg) throws Exception {

		// 通常時は終了済み以外をスキップ。手動時は通す。
		if (!manualFlg && !isFinished(entity.getTime())) {
			return;
		}

		String category = trim(entity.getGameTeamCategory());
		if (category.isEmpty()) {
			warn(methodName, "gameTeamCategory is blank.");
			return;
		}

		Integer match = extractMatch(category);
		if (match == null) {
			warn(methodName, "match extract failed. category=" + category);
			return;
		}

		List<String> countryLeague = ExecuteMainUtil.getCountryLeagueByRegex(category);
		if (countryLeague == null || countryLeague.size() < 2) {
			String messageCd = MessageCdConst.MCD00001W_COUNTRY_LEAGUE_SPLIT_FAIL_WARNING;
			this.manageLoggerComponent.debugWarnLog(
					PROJECT_NAME, CLASS_NAME, methodName, messageCd,
					"ExecuteMainUtil.getCountryLeagueByRegex(分割失敗: " + category + ")");
			return;
		}

		String country = trim(countryLeague.get(0));
		String league = trim(countryLeague.get(1));

		if (country.isEmpty() || league.isEmpty()) {
			warn(methodName, "country or league is blank. category=" + category);
			return;
		}

		String seasonYear = resolveSeasonYear(country, league, methodName);
		if (seasonYear.isEmpty()) {
			return;
		}

		boolean homeRankSaved = saveRankIfPresent(
				country, league, seasonYear,
				entity.getHomeTeamName(), entity.getHomeRank(), match,
				methodName, "ホーム");

		boolean awayRankSaved = saveRankIfPresent(
				country, league, seasonYear,
				entity.getAwayTeamName(), entity.getAwayRank(), match,
				methodName, "アウェー");

		// 片方でも順位欠損なら後付け順位計算
		if (!homeRankSaved || !awayRankSaved) {
			String backfillKey = String.join("|",
					country, league, seasonYear, String.valueOf(match));

			if (processedBackfillKeys.add(backfillKey)) {
				backfillRanks(country, league, seasonYear, match, methodName);
			}
		}
	}

	/**
	 * 順位があれば保存
	 */
	private boolean saveRankIfPresent(String country, String league, String seasonYear,
			String teamName, String rankStr, Integer match,
			String methodName, String sideLabel) {

		String team = trim(teamName);
		if (team.isEmpty()) {
			return false;
		}

		Integer rank = parseRank(rankStr);
		if (rank == null) {
			return false;
		}

		RankHistoryEntity rankHistoryEntity = new RankHistoryEntity();
		rankHistoryEntity.setCountry(country);
		rankHistoryEntity.setLeague(league);
		rankHistoryEntity.setSeasonYear(seasonYear);
		rankHistoryEntity.setTeam(team);
		rankHistoryEntity.setMatch(match);
		rankHistoryEntity.setRank(rank);

		this.rankHistoryWriter.write(rankHistoryEntity);

		String messageCd = MessageCdConst.MCD00006I_UPDATE_SUCCESS;
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, methodName, messageCd,
				BM_NUMBER + " " + sideLabel + "順位保存: "
						+ String.join(" / ", country, league, seasonYear,
								String.valueOf(match), team, String.valueOf(rank)));

		return true;
	}

	/**
	 * 順位欠損時の後付け処理
	 */
	private void backfillRanks(String country, String league, String seasonYear,
			Integer match, String methodName) throws Exception {

		List<TeamPoints> teamPoints = getOriginRank(country, league, String.valueOf(match));
		if (teamPoints == null || teamPoints.isEmpty()) {
			String messageCd = MessageCdConst.MCD00001W_COUNTRY_LEAGUE_SPLIT_FAIL_WARNING;
			this.manageLoggerComponent.debugWarnLog(
					PROJECT_NAME, CLASS_NAME, methodName, messageCd,
					"No team points found. " + country + " / " + league + " / " + match);
			return;
		}

		List<RankedTeamPoints> ranks = rankTeams(teamPoints);

		for (RankedTeamPoints pointDTO : ranks) {
			if (pointDTO == null || isBlank(pointDTO.getTeam()) || pointDTO.getRank() == null) {
				continue;
			}

			RankHistoryEntity rankHistoryEntity = new RankHistoryEntity();
			rankHistoryEntity.setCountry(country);
			rankHistoryEntity.setLeague(league);
			rankHistoryEntity.setSeasonYear(seasonYear);
			rankHistoryEntity.setTeam(pointDTO.getTeam());
			rankHistoryEntity.setMatch(match);
			rankHistoryEntity.setRank(pointDTO.getRank());

			this.rankHistoryWriter.write(rankHistoryEntity);
		}

		String messageCd = MessageCdConst.MCD00006I_UPDATE_SUCCESS;
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, methodName, messageCd,
				BM_NUMBER + " 順位後付け完了: "
						+ String.join(" / ", country, league, seasonYear, String.valueOf(match))
						+ " teams=" + ranks.size());
	}

	/**
	 * 国,リーグから勝利,敗北,引き分け,得失点差を導出し、
	 * 現在実施済みの試合から最終的な順位を出す
	 */
	private List<TeamPoints> getOriginRank(String country, String league, String match) throws Exception {
		return this.bookDataRepository.selectTeamPoints(country, league, match);
	}

	/**
	 * 順位付け
	 * 1. 勝ち点 desc
	 * 2. 得失点差 desc
	 * 3. 総得点 desc
	 * 4. チーム名 asc（安定ソート用）
	 */
	private List<RankedTeamPoints> rankTeams(List<TeamPoints> teamPoints) {

		if (teamPoints == null || teamPoints.isEmpty()) {
			return new ArrayList<>();
		}

		List<TeamPoints> sorted = teamPoints.stream()
				.filter(tp -> tp != null && !isBlank(tp.getTeam()))
				.sorted((a, b) -> {
					int pointsA = nvl(a.getPoints());
					int pointsB = nvl(b.getPoints());
					int cmpPoints = Integer.compare(pointsB, pointsA);
					if (cmpPoints != 0) {
						return cmpPoints;
					}

					int gfA = nvl(a.getGf());
					int gaA = nvl(a.getGa());
					int gfB = nvl(b.getGf());
					int gaB = nvl(b.getGa());

					int goalDiffA = gfA - gaA;
					int goalDiffB = gfB - gaB;
					int cmpGoalDiff = Integer.compare(goalDiffB, goalDiffA);
					if (cmpGoalDiff != 0) {
						return cmpGoalDiff;
					}

					int cmpGf = Integer.compare(gfB, gfA);
					if (cmpGf != 0) {
						return cmpGf;
					}

					return safe(a.getTeam()).compareTo(safe(b.getTeam()));
				})
				.collect(Collectors.toList());

		List<RankedTeamPoints> rankedList = new ArrayList<>();

		int rank = 1;
		int index = 0;

		Integer prevPoints = null;
		Integer prevGoalDiff = null;
		Integer prevGf = null;

		for (TeamPoints tp : sorted) {
			int points = nvl(tp.getPoints());
			int gf = nvl(tp.getGf());
			int ga = nvl(tp.getGa());
			int goalDiff = gf - ga;

			if (prevPoints != null
					&& points == prevPoints
					&& goalDiff == prevGoalDiff
					&& gf == prevGf) {
				// 同順位
			} else {
				rank = index + 1;
			}

			RankedTeamPoints rtp = new RankedTeamPoints();
			rtp.setRank(rank);
			rtp.setTeam(tp.getTeam());
			rtp.setPoints(points);
			rtp.setGf(gf);
			rtp.setGa(ga);
			rtp.setPlayed(nvl(tp.getPlayed()));

			rankedList.add(rtp);

			prevPoints = points;
			prevGoalDiff = goalDiff;
			prevGf = gf;
			index++;
		}

		return rankedList;
	}

	/**
	 * 終了済み判定
	 */
	private boolean isFinished(String time) {
		if (time == null || time.isBlank()) {
			return false;
		}
		return BookMakersCommonConst.FIN.equals(time)
				|| time.contains(BookMakersCommonConst.PENALTY);
	}

	/**
	 * 節抽出
	 */
	private Integer extractMatch(String category) {
		if (category == null || category.isBlank()) {
			return null;
		}

		int match = ExecuteMainUtil.extractRoundNumbers(category);
		return match < 0 ? null : match;
	}

	/**
	 * seasonYear 解決
	 */
	private String resolveSeasonYear(String country, String league, String methodName) {
		String seasonYear = this.countryLeagueSeasonMasterRepository
				.findSeasonYear(country, league);

		if (seasonYear == null || seasonYear.isBlank()) {
			this.manageLoggerComponent.debugWarnLog(
					PROJECT_NAME, CLASS_NAME, methodName,
					MessageCdConst.MCD00001W_COUNTRY_LEAGUE_SPLIT_FAIL_WARNING,
					"season_year not found: " + country + " / " + league);
			return "";
		}

		return seasonYear;
	}

	/**
	 * 順位文字列を安全に整数化
	 * 例: "1", "1.0", " 2 "
	 */
	private Integer parseRank(String rankStr) {
		if (rankStr == null || rankStr.isBlank()) {
			return null;
		}

		try {
			String normalized = rankStr.trim().replace(",", "");
			BigDecimal bd = new BigDecimal(normalized).stripTrailingZeros();
			return Integer.valueOf(bd.intValueExact());
		} catch (Exception e) {
			return null;
		}
	}

	private int nvl(Integer value) {
		return value == null ? 0 : value;
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private String trim(String value) {
		return value == null ? "" : value.trim();
	}

	private String safe(String value) {
		return value == null ? "" : value;
	}

	private void warn(String methodName, String msg) {
		this.manageLoggerComponent.debugWarnLog(
				PROJECT_NAME, CLASS_NAME, methodName,
				MessageCdConst.MCD00001W_COUNTRY_LEAGUE_SPLIT_FAIL_WARNING,
				msg);
	}
}
