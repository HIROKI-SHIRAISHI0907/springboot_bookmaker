package dev.application.analyze.bm_m003;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.application.analyze.common.util.BookMakersCommonConst;
import dev.application.analyze.interf.AnalyzeEntityIF;
import dev.application.domain.repository.bm.TeamMonthlyScoreSummaryRepository;
import dev.common.constant.MessageCdConst;
import dev.common.entity.BookDataEntity;
import dev.common.exception.wrap.RootCauseWrapper;
import dev.common.logger.ManageLoggerComponent;
import dev.common.util.ExecuteMainUtil;

/**
 * BM_M003統計分析ロジック
 * @author shiraishitoshio
 *
 */
@Component
public class TeamMonthlyScoreSummaryStat implements AnalyzeEntityIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = TeamMonthlyScoreSummaryStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = TeamMonthlyScoreSummaryStat.class.getSimpleName();

	/** 実行モード */
	private static final String EXEC_MODE = "BM_M003_TEAM_MONTHLY_SCORE";

	/** TeamMonthlyScoreSummaryRepositoryレポジトリクラス */
	@Autowired
	private TeamMonthlyScoreSummaryRepository teamMonthlyScoreSummaryRepository;

	/** ログ管理ラッパー*/
	@Autowired
	private RootCauseWrapper rootCauseWrapper;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void calcStat(Map<String, Map<String, List<BookDataEntity>>> entities) {
		final String METHOD_NAME = "calcStat";
		this.manageLoggerComponent.init(EXEC_MODE, null);
		this.manageLoggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		// スレッドセーフなマップ構造
		Map<String, Map<String, Map<String, Integer>>> goalCountMap = new ConcurrentHashMap<>();
		entities.entrySet().parallelStream().forEach(outerEntry -> {
			String countryLeague = outerEntry.getKey();
			Map<String, List<BookDataEntity>> homeAwayMap = outerEntry.getValue();

			for (Map.Entry<String, List<BookDataEntity>> innerEntry : homeAwayMap.entrySet()) {
				String teamName = innerEntry.getKey();
				String home = teamName.split("-")[0] + "-home";
				String away = teamName.split("-")[1] + "-away";
				List<BookDataEntity> entityList = innerEntry.getValue();

				int prevHomeScore = 0;
				int prevAwayScore = 0;
				for (BookDataEntity entity : entityList) {
					String recordTime = entity.getRecordTime();
					if (recordTime == null || recordTime.length() < 7)
						continue;
					String yearMonth = recordTime.substring(0, 7);

					int currentHomeScore = 0;
					int currentAwayScore = 0;

					// ゴール取り消しはスキップ
					if (BookMakersCommonConst.GOAL_DELETE.equals(entity.getJudge()))
						continue;

					currentHomeScore = parseScore(entity.getHomeScore());
					currentAwayScore = parseScore(entity.getAwayScore());

					// 差分でゴール検出
					int diffHome = currentHomeScore - prevHomeScore;
					int diffAway = currentAwayScore - prevAwayScore;

					if (diffHome > 0) {
						String team = home.replace("-home", "");
						goalCountMap
								.computeIfAbsent(countryLeague, k -> new ConcurrentHashMap<>())
								.computeIfAbsent(team + "-H", k -> new ConcurrentHashMap<>())
								.merge(yearMonth, diffHome, Integer::sum);
					}

					if (diffAway > 0) {
						String team = away.replace("-away", "");
						goalCountMap
								.computeIfAbsent(countryLeague, k -> new ConcurrentHashMap<>())
								.computeIfAbsent(team + "-A", k -> new ConcurrentHashMap<>())
								.merge(yearMonth, diffAway, Integer::sum);
					}

					prevHomeScore = currentHomeScore;
					prevAwayScore = currentAwayScore;
				}
			}
		});

		// 結果の登録/更新処理
		for (Map.Entry<String, Map<String, Map<String, Integer>>> leagueEntry : goalCountMap.entrySet()) {
			String countryLeague = leagueEntry.getKey(); // 例: "Japan-J1"
			String[] split = ExecuteMainUtil.splitLeagueInfo(countryLeague);
			String country = split[0];
			String league = split[1];

			Map<String, Map<String, Integer>> teamMap = leagueEntry.getValue();

			for (Map.Entry<String, Map<String, Integer>> teamEntry : teamMap.entrySet()) {
				String teamWithHA = teamEntry.getKey(); // 例: "Kawasaki-H"
				Map<String, Integer> monthlyGoals = teamEntry.getValue();

				// チーム名とHAを抽出
				String teamName = teamWithHA.substring(0, teamWithHA.length() - 2); // "Kawasaki"
				String ha = teamWithHA.substring(teamWithHA.length() - 1); // "H" or "A"

				for (Map.Entry<String, Integer> monthEntry : monthlyGoals.entrySet()) {
					String yearMonth = monthEntry.getKey(); // 例: "2025-07"
					int goalCount = monthEntry.getValue();

					String year = yearMonth.substring(0, 4); // "2025"
					String month = yearMonth.substring(5, 7); // "07"
					int monthIndex = Integer.parseInt(month) - 1; // 0-based index

					// データ取得（getData）呼び出し

					TeamStaticDataOutputDTO dto = getData(country, league, teamName, ha, year);
					boolean chkFlg = dto.isUpdFlg();
					String[] seasonCountList = dto.getScoreList();
					String seq = dto.getSeq();

					// 加算処理
					int existing = Integer.parseInt(seasonCountList[monthIndex]);
					seasonCountList[monthIndex] = String.valueOf(existing + goalCount);

					// 更新・登録処理
					saveOrUpdate(country, league, teamName, ha, year, seasonCountList, chkFlg, seq);
				}
			}
		}

		this.manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		this.manageLoggerComponent.clear();
	}

	/**
	 * データを取得する
	 * @param country 国
	 * @param league リーグ
	 * @param ha Home,Away
	 * @param team チーム
	 * @param year 年
	 * @param updFlg 更新フラグ
	 */
	private TeamStaticDataOutputDTO getData(String country, String league, String team, String ha,
			String year) {
		TeamStaticDataOutputDTO teamStaticDataOutputDTO = new TeamStaticDataOutputDTO();
		TeamMonthlyScoreSummaryEntity teamMonthlyScoreSummaryEntity = new TeamMonthlyScoreSummaryEntity();
		teamMonthlyScoreSummaryEntity.setCountry(country);
		teamMonthlyScoreSummaryEntity.setLeague(league);
		teamMonthlyScoreSummaryEntity.setTeamName(team);
		teamMonthlyScoreSummaryEntity.setHa(ha);
		teamMonthlyScoreSummaryEntity.setYear(year);
		List<TeamMonthlyScoreSummaryEntity> entities = this.teamMonthlyScoreSummaryRepository
				.findByCount(teamMonthlyScoreSummaryEntity);

		String[] seasonCountList = new String[] {
				"0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0"
		};
		if (entities.isEmpty()) {
			teamStaticDataOutputDTO.setUpdFlg(false);
		} else {
			teamStaticDataOutputDTO.setUpdFlg(true);
			teamStaticDataOutputDTO.setSeq(entities.get(0).getSeq());
			seasonCountList = new String[] {
					entities.get(0).getJanuaryScoreSumCount(),
					entities.get(0).getFebruaryScoreSumCount(),
					entities.get(0).getMarchScoreSumCount(),
					entities.get(0).getAprilScoreSumCount(),
					entities.get(0).getMayScoreSumCount(),
					entities.get(0).getJuneScoreSumCount(),
					entities.get(0).getJulyScoreSumCount(),
					entities.get(0).getAugustScoreSumCount(),
					entities.get(0).getSeptemberScoreSumCount(),
					entities.get(0).getOctoberScoreSumCount(),
					entities.get(0).getNovemberScoreSumCount(),
					entities.get(0).getDecemberScoreSumCount()
			};
		}
		teamStaticDataOutputDTO.setScoreList(seasonCountList);
		return teamStaticDataOutputDTO;
	}

	/**
	 *
	 * @param country 国
	 * @param league リーグ
	 * @param ha Home,Away
	 * @param team チーム
	 * @param year 年
	 * @param seasonCountList 件数リスト
	 * @param seq 連番
	 * @param 更新フラグ
	 */
	private synchronized void saveOrUpdate(String country, String league, String team, String ha,
			String year, String[] seasonCountList, boolean updFlg, String seq) {
		final String METHOD_NAME = "saveOrUpdate";

		TeamMonthlyScoreSummaryEntity teamMonthlyScoreSummaryEntity = new TeamMonthlyScoreSummaryEntity();
		teamMonthlyScoreSummaryEntity.setSeq(seq);
		teamMonthlyScoreSummaryEntity.setCountry(country);
		teamMonthlyScoreSummaryEntity.setLeague(league);
		teamMonthlyScoreSummaryEntity.setTeamName(team);
		teamMonthlyScoreSummaryEntity.setHa(ha);
		teamMonthlyScoreSummaryEntity.setYear(year);
		teamMonthlyScoreSummaryEntity.setJanuaryScoreSumCount(seasonCountList[0]);
		teamMonthlyScoreSummaryEntity.setFebruaryScoreSumCount(seasonCountList[1]);
		teamMonthlyScoreSummaryEntity.setMarchScoreSumCount(seasonCountList[2]);
		teamMonthlyScoreSummaryEntity.setAprilScoreSumCount(seasonCountList[3]);
		teamMonthlyScoreSummaryEntity.setMayScoreSumCount(seasonCountList[4]);
		teamMonthlyScoreSummaryEntity.setJuneScoreSumCount(seasonCountList[5]);
		teamMonthlyScoreSummaryEntity.setJulyScoreSumCount(seasonCountList[6]);
		teamMonthlyScoreSummaryEntity.setAugustScoreSumCount(seasonCountList[7]);
		teamMonthlyScoreSummaryEntity.setSeptemberScoreSumCount(seasonCountList[8]);
		teamMonthlyScoreSummaryEntity.setOctoberScoreSumCount(seasonCountList[9]);
		teamMonthlyScoreSummaryEntity.setNovemberScoreSumCount(seasonCountList[10]);
		teamMonthlyScoreSummaryEntity.setDecemberScoreSumCount(seasonCountList[11]);
		if (updFlg) {
			int result = this.teamMonthlyScoreSummaryRepository.update(teamMonthlyScoreSummaryEntity);
			if (result != 1) {
				String messageCd = MessageCdConst.MCD00008E_UPDATE_FAILED;
				this.rootCauseWrapper.throwUnexpectedRowCount(
				        PROJECT_NAME, CLASS_NAME, METHOD_NAME,
				        messageCd,
				        1, result,
				        String.format("id=%s", seq)
				    );
			}

			String messageCd = MessageCdConst.MCD00006I_UPDATE_SUCCESS;
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, "BM_M003 更新件数: 1件");
		} else {
			int result = this.teamMonthlyScoreSummaryRepository.insertTeamMonthlyScore(teamMonthlyScoreSummaryEntity);
			if (result != 1) {
				String messageCd = MessageCdConst.MCD00007E_INSERT_FAILED;
				this.rootCauseWrapper.throwUnexpectedRowCount(
				        PROJECT_NAME, CLASS_NAME, METHOD_NAME,
				        messageCd,
				        1, result,
				        null
				    );
			}

			String messageCd = MessageCdConst.MCD00005I_INSERT_SUCCESS;
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, "BM_M003 登録件数: 1件");
		}

	}

	/**
	 * 得点があるかをチェックし,int型に変換する
	 * @param scoreStr
	 * @return
	 */
	private int parseScore(String scoreStr) {
		try {
			return (scoreStr == null || scoreStr.isBlank()) ? 0 : Integer.parseInt(scoreStr.trim());
		} catch (NumberFormatException e) {
			return 0;
		}
	}

}
