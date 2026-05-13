package dev.application.analyze.bm_m003;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.application.analyze.common.util.BookMakersCommonConst;
import dev.application.analyze.interf.AnalyzeEntityIF;
import dev.common.constant.MessageCdConst;
import dev.common.entity.BookDataEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.util.ExecuteMainUtil;

/**
 * BM_M003統計分析ロジック
 */
@Component
public class TeamMonthlyScoreSummaryStat implements AnalyzeEntityIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = TeamMonthlyScoreSummaryStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = TeamMonthlyScoreSummaryStat.class.getName();

	/** 実行モード */
	private static final String EXEC_MODE = "BM_M003_TEAM_MONTHLY_SCORE";

	@Autowired
	private TeamMonthlyScoreSummaryWriter teamMonthlyScoreSummaryWriter;

	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	@Override
	public void calcStat(Map<String, Map<String, List<BookDataEntity>>> entities) {
		final String METHOD_NAME = "calcStat";

		this.manageLoggerComponent.init(EXEC_MODE, null);
		this.manageLoggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		try {
			Map<String, Map<String, Map<String, Integer>>> goalCountMap = new HashMap<String, Map<String, Map<String, Integer>>>();

			if (entities == null || entities.isEmpty()) {
				return;
			}

			for (Map.Entry<String, Map<String, List<BookDataEntity>>> outerEntry : entities.entrySet()) {
				String countryLeague = outerEntry.getKey();
				Map<String, List<BookDataEntity>> homeAwayMap = outerEntry.getValue();

				if (homeAwayMap == null || homeAwayMap.isEmpty()) {
					continue;
				}

				for (Map.Entry<String, List<BookDataEntity>> innerEntry : homeAwayMap.entrySet()) {
					String key = innerEntry.getKey();
					String[] parts = (key == null) ? new String[0] : key.split("-", -1);

					String fileName = (parts.length >= 1) ? parts[0] : "";
					if (parts.length < 3) {
						String messageCd = MessageCdConst.MCD00099I_LOG;
						this.manageLoggerComponent.debugInfoLog(
								PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
								"skip: invalid key format (need file-home-away). fileName=" + fileName
										+ ", key=" + key
										+ ", partsLen=" + parts.length
										+ ", countryLeague=" + countryLeague);
						continue;
					}

					String homeTeam = parts[1];
					String awayTeam = buildAwayTeam(parts);

					if (isBlank(homeTeam) || isBlank(awayTeam)) {
						String messageCd = MessageCdConst.MCD00099I_LOG;
						this.manageLoggerComponent.debugInfoLog(
								PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
								"skip: blank home/away. fileName=" + fileName
										+ ", key=" + key
										+ ", home=" + homeTeam
										+ ", away=" + awayTeam
										+ ", countryLeague=" + countryLeague);
						continue;
					}

					List<BookDataEntity> entityList = innerEntry.getValue();
					if (entityList == null || entityList.isEmpty()) {
						String messageCd = MessageCdConst.MCD00099I_LOG;
						this.manageLoggerComponent.debugInfoLog(
								PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
								"skip: empty entityList. fileName=" + fileName
										+ ", key=" + key
										+ ", countryLeague=" + countryLeague);
						continue;
					}

					int prevHomeScore = 0;
					int prevAwayScore = 0;

					for (BookDataEntity entity : entityList) {
						if (entity == null) {
							continue;
						}

						String messageCd = MessageCdConst.MCD00099I_LOG;
						this.manageLoggerComponent.debugInfoLog(
								PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
								"fileName=" + fileName + ", filePath=" + entity.getFilePath());

						String recordTime = entity.getRecordTime();
						if (recordTime == null || recordTime.length() < 7) {
							continue;
						}

						String yearMonth = recordTime.substring(0, 7);

						if (BookMakersCommonConst.GOAL_DELETE.equals(entity.getJudge())) {
							continue;
						}

						int currentHomeScore = parseScore(entity.getHomeScore());
						int currentAwayScore = parseScore(entity.getAwayScore());

						int diffHome = currentHomeScore - prevHomeScore;
						int diffAway = currentAwayScore - prevAwayScore;

						if (diffHome > 0) {
							addGoal(goalCountMap, countryLeague, homeTeam, "H", yearMonth, diffHome);
						}

						if (diffAway > 0) {
							addGoal(goalCountMap, countryLeague, awayTeam, "A", yearMonth, diffAway);
						}

						prevHomeScore = currentHomeScore;
						prevAwayScore = currentAwayScore;
					}
				}
			}

			flushGoalCount(goalCountMap);

		} finally {
			this.manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);
			this.manageLoggerComponent.clear();
		}
	}

	private void flushGoalCount(Map<String, Map<String, Map<String, Integer>>> goalCountMap) {
		for (Map.Entry<String, Map<String, Map<String, Integer>>> leagueEntry : goalCountMap.entrySet()) {
			String countryLeague = leagueEntry.getKey();
			String[] split = ExecuteMainUtil.splitLeagueInfo(countryLeague);
			String country = split[0];
			String league = split[1];

			Map<String, Map<String, Integer>> teamMap = leagueEntry.getValue();
			if (teamMap == null) {
				continue;
			}

			for (Map.Entry<String, Map<String, Integer>> teamEntry : teamMap.entrySet()) {
				String teamWithHA = teamEntry.getKey();
				Map<String, Integer> monthlyGoals = teamEntry.getValue();

				if (teamWithHA == null || teamWithHA.length() < 3 || monthlyGoals == null) {
					continue;
				}

				String teamName = teamWithHA.substring(0, teamWithHA.length() - 2);
				String ha = teamWithHA.substring(teamWithHA.length() - 1);

				for (Map.Entry<String, Integer> monthEntry : monthlyGoals.entrySet()) {
					String yearMonth = monthEntry.getKey();
					Integer goalCount = monthEntry.getValue();

					if (yearMonth == null || yearMonth.length() != 7 || goalCount == null || goalCount <= 0) {
						continue;
					}

					String year = yearMonth.substring(0, 4);
					String month = yearMonth.substring(5, 7);
					int monthIndex = Integer.parseInt(month) - 1;

					this.teamMonthlyScoreSummaryWriter.addMonthlyGoal(
							country, league, teamName, ha, year, monthIndex, goalCount.intValue());
				}
			}
		}
	}

	private void addGoal(Map<String, Map<String, Map<String, Integer>>> goalCountMap,
			String countryLeague, String teamName, String ha, String yearMonth, int goalCount) {
		goalCountMap
				.computeIfAbsent(countryLeague, k -> new HashMap<String, Map<String, Integer>>())
				.computeIfAbsent(teamName + "-" + ha, k -> new HashMap<String, Integer>())
				.merge(yearMonth, Integer.valueOf(goalCount), Integer::sum);
	}

	private String buildAwayTeam(String[] parts) {
		if (parts.length == 3) {
			return parts[2];
		}

		StringBuilder sb = new StringBuilder();
		for (int i = 2; i < parts.length; i++) {
			if (i > 2) {
				sb.append("-");
			}
			sb.append(parts[i]);
		}
		return sb.toString();
	}

	private int parseScore(String scoreStr) {
		try {
			return isBlank(scoreStr) ? 0 : Integer.parseInt(scoreStr.trim());
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private boolean isBlank(String value) {
		return value == null || value.trim().isEmpty();
	}
}
