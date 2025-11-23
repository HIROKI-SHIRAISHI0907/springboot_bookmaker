package dev.application.main.service;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.application.analyze.bm_m002.ConditionResultDataStat;
import dev.application.analyze.bm_m003.TeamMonthlyScoreSummaryStat;
import dev.application.analyze.bm_m004.TeamTimeSegmentShootingStat;
import dev.application.analyze.bm_m005.NoGoalMatchStat;
import dev.application.analyze.bm_m006.CountryLeagueSummaryStat;
import dev.application.analyze.bm_m017_bm_m018.LeagueScoreTimeBandStat;
import dev.application.analyze.bm_m019_bm_m020.MatchClassificationResultStat;
import dev.application.analyze.bm_m021.TeamMatchFinalStat;
import dev.application.analyze.bm_m023.ScoreBasedFeatureStat;
import dev.application.analyze.bm_m023C_bm_m026C.MetricDeltaStat;
import dev.application.analyze.bm_m024.CalcCorrelationStat;
import dev.application.analyze.bm_m025.CalcCorrelationRankingStat;
import dev.application.analyze.bm_m026.EachTeamScoreBasedFeatureStat;
import dev.application.analyze.bm_m031.SurfaceOverviewStat;
import dev.application.analyze.bm_m033.RankHistoryStat;
import dev.common.entity.BookDataEntity;
import dev.common.logger.ManageLoggerComponent;


/**
 * 統計分析用サービスクラス
 * @author shiraishitoshio
 *
 */
@Service
@Transactional("bmTxManager")
public class StatService implements StatIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = StatService.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = StatService.class.getSimpleName();

	/**
	 * BM_M023C_BM_M26C統計分析ロジッククラス
	 */
	@Autowired
	private MetricDeltaStat metricDeltaStat;

	/**
	 * BM_M002統計分析ロジッククラス
	 */
	@Autowired
	private ConditionResultDataStat conditionResultDataStat;

	/**
	 * BM_M003統計分析ロジッククラス
	 */
	@Autowired
	private TeamMonthlyScoreSummaryStat teamMonthlyScoreSummaryStat;

	/**
	 * BM_M004統計分析ロジッククラス
	 */
	@Autowired
	private TeamTimeSegmentShootingStat teamTimeSegmentShootingStat;

	/**
	 * BM_M005統計分析ロジッククラス
	 */
	@Autowired
	private NoGoalMatchStat noGoalMatchStat;

	/**
	 * BM_M006統計分析ロジッククラス
	 */
	@Autowired
	private CountryLeagueSummaryStat countryLeagueSummaryStat;

	/**
	 * BM_M007-BM_M016統計分析ロジッククラス
	 */
	//@Autowired
	//private TimeRangeFeatureStat timeRangeFeatureStat;

	/**
	 * BM_M017-BM_M018統計分析ロジッククラス
	 */
	@Autowired
	private LeagueScoreTimeBandStat leagueScoreTimeBandStat;

	/**
	 * BM_M019-BM_M020統計分析ロジッククラス
	 */
	@Autowired
	private MatchClassificationResultStat matchClassificationResultStat;

	/**
	 * BM_M021統計分析ロジッククラス
	 */
	@Autowired
	private TeamMatchFinalStat teamMatchFinalStat;

	/**
	 * BM_M023統計分析ロジッククラス
	 */
	@Autowired
	private ScoreBasedFeatureStat scoreBasedFeatureStat;

	/**
	 * BM_M024統計分析ロジッククラス
	 */
	@Autowired
	private CalcCorrelationStat calcCorrelationStat;

	/**
	 * BM_M025統計分析ロジッククラス
	 */
	@Autowired
	private CalcCorrelationRankingStat calcCorrelationRankingStat;

	/**
	 * BM_M026統計分析ロジッククラス
	 */
	@Autowired
	private EachTeamScoreBasedFeatureStat eachTeamScoreBasedFeatureStat;

	/**
	 * BM_M031統計分析ロジッククラス
	 */
	@Autowired
	private SurfaceOverviewStat surfaceOverviewStat;

	/**
	 * BM_M033統計分析ロジッククラス
	 */
	@Autowired
	private RankHistoryStat rankHistoryStat;

	/**
	 * ログ管理クラス
	 */
	@Autowired
	private ManageLoggerComponent loggerComponent;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int execute(Map<String, Map<String, List<BookDataEntity>>> stat) throws Exception {
		final String METHOD_NAME = "execute";

		// 時間計測開始
		long startTime = System.nanoTime();

		// ログ出力
		this.loggerComponent.debugStartInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		// 履歴ロジック呼び出し
		this.metricDeltaStat.calcStat(stat);

		// 統計ロジック呼び出し(国,リーグ単位で並列)
		this.conditionResultDataStat.calcStat(stat);
		this.teamMonthlyScoreSummaryStat.calcStat(stat);
		this.teamTimeSegmentShootingStat.calcStat(stat);
		this.countryLeagueSummaryStat.calcStat(stat);
		this.noGoalMatchStat.calcStat(stat);
		//this.timeRangeFeatureStat.calcStat(stat);
		this.leagueScoreTimeBandStat.calcStat(stat);
		this.matchClassificationResultStat.calcStat(stat);
		this.teamMatchFinalStat.calcStat(stat);
		this.scoreBasedFeatureStat.calcStat(stat);
		this.calcCorrelationStat.calcStat(stat);
		this.calcCorrelationRankingStat.calcStat(stat);
		this.eachTeamScoreBasedFeatureStat.calcStat(stat);
		this.surfaceOverviewStat.calcStat(stat);
		this.rankHistoryStat.calcStat(stat);

		// endLog
		this.loggerComponent.debugEndInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		// 時間計測終了
		long endTime = System.nanoTime();
		long durationMs = (endTime - startTime) / 1_000_000; // ミリ秒に変換

		System.out.println("時間: " + durationMs);

		return 0;
	}

}
