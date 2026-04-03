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
import dev.application.analyze.bm_m024.CalcCorrelationStat;
import dev.application.analyze.bm_m025.CalcCorrelationRankingStat;
import dev.application.analyze.bm_m026.EachTeamScoreBasedFeatureStat;
import dev.application.analyze.bm_m031.SurfaceOverviewStat;
import dev.application.analyze.bm_m033.RankHistoryStat;
import dev.application.analyze.interf.StatIF;
import dev.application.domain.repository.bm.CsvDetailManageRepository;
import dev.application.domain.repository.master.CountryLeagueSeasonMasterRepository;
import dev.common.constant.MessageCdConst;
import dev.common.entity.BookDataEntity;
import dev.common.entity.CsvDetailManageEntity;
import dev.common.exception.wrap.RootCauseWrapper;
import dev.common.logger.ManageLoggerComponent;
import dev.common.util.ExecuteMainUtil;

/**
 * 統計分析用サービスクラス
 * @author shiraishitoshio
 *
 */
@Service
@Transactional
public class CoreStat implements StatIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = CoreStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = CoreStat.class.getName();

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

	@Autowired
	private CountryLeagueSeasonMasterRepository countryLeagueSeasonMasterBatchRepository;

	@Autowired
	private CsvDetailManageRepository csvDetailManageRepository;

	/** ログ管理ラッパー*/
	@Autowired
	private RootCauseWrapper rootCauseWrapper;

	/**
	 * ログ管理クラス
	 */
	@Autowired
	private ManageLoggerComponent loggerComponent;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int execute(Map<String, Map<String, List<BookDataEntity>>> stat, boolean manualFlg) throws Exception {
		final String METHOD_NAME = "execute";

		// 時間計測開始
		long startTime = System.nanoTime();

		// ログ出力
		this.loggerComponent.debugStartInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		CsvDetailEntityOutputDTO existDto = selectCsvDetail(stat);
		if (existDto.isExistFlg()) {
			this.loggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00002I_BATCH_EXECUTION_SKIP,
					"すでに統計反映しているデータのためスキップします。（" + existDto + "）");
			return 0;
		}

		// 統計ロジック呼び出し(国,リーグ単位で並列)
		if (!manualFlg)
			this.conditionResultDataStat.calcStat(stat);
		this.teamMonthlyScoreSummaryStat.calcStat(stat);
		if (!manualFlg)
			this.teamTimeSegmentShootingStat.calcStat(stat);
		if (!manualFlg)
			this.countryLeagueSummaryStat.calcStat(stat);
		this.noGoalMatchStat.calcStat(stat);
		//if (!manualFlg) this.timeRangeFeatureStat.calcStat(stat);
		if (!manualFlg)
			this.leagueScoreTimeBandStat.calcStat(stat);
		if (!manualFlg)
			this.matchClassificationResultStat.calcStat(stat);
		this.teamMatchFinalStat.calcStat(stat);
		if (!manualFlg)
			this.scoreBasedFeatureStat.calcStat(stat);
		if (!manualFlg)
			this.calcCorrelationStat.calcStat(stat);
		if (!manualFlg)
			this.calcCorrelationRankingStat.calcStat(stat);
		if (!manualFlg)
			this.eachTeamScoreBasedFeatureStat.calcStat(stat);
		this.surfaceOverviewStat.calcStat(stat);
		this.rankHistoryStat.calcStat(stat);

		stat.clear();

		// endLog
		this.loggerComponent.debugEndInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		// csv詳細管理登録
		insertCsvDetail(existDto);

		// 時間計測終了
		long endTime = System.nanoTime();
		long durationMs = (endTime - startTime) / 1_000_000; // ミリ秒に変換

		System.out.println("時間: " + durationMs);

		return 0;
	}

	private CsvDetailEntityOutputDTO selectCsvDetail(
			Map<String, Map<String, List<BookDataEntity>>> stat) {
		final String METHOD_NAME = "selectCsvDetail";

		if (stat == null || stat.isEmpty()) {
			return null;
		}

		CsvDetailEntityOutputDTO dto = new CsvDetailEntityOutputDTO();

		for (Map<String, List<BookDataEntity>> innerMap : stat.values()) {
			if (innerMap == null || innerMap.isEmpty()) {
				continue;
			}

			for (List<BookDataEntity> rows : innerMap.values()) {
				if (rows == null || rows.isEmpty()) {
					continue;
				}

				BookDataEntity row = buildRepresentativeRow(rows);
				if (row == null) {
					continue;
				}

				String dataCategory = safe(row.getGameTeamCategory()).trim();
				if (dataCategory.isEmpty()) {
					continue;
				}
				dto.setDataCategory(dataCategory);

				// csv番号
				String file = safe(row.getFilePath()).trim();
				String csvId = "";
				if (!file.isEmpty()) {
					String fileName = java.nio.file.Paths.get(file).getFileName().toString(); // X.csv
					int dot = fileName.lastIndexOf('.');
					csvId = (dot >= 0) ? fileName.substring(0, dot) : fileName; // X
				} else {
					continue;
				}
				dto.setCsvId(csvId);

				List<String> dataList = ExecuteMainUtil.getCountryLeagueByRegex(dataCategory);
				if (dataList == null || dataList.size() < 2) {
					this.loggerComponent.debugWarnLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME,
							MessageCdConst.MCD00099I_LOG,
							"country/league の抽出に失敗: dataCategory=" + dataCategory);
					continue;
				}

				String season = countryLeagueSeasonMasterBatchRepository
						.findSeasonYear(dataList.get(0), dataList.get(1));
				dto.setSeason(season);

				String home = safe(row.getHomeTeamName()).trim();
				dto.setHomeTeamName(home);
				String away = safe(row.getAwayTeamName()).trim();
				dto.setAwayTeamName(away);

				CsvDetailManageEntity entity = new CsvDetailManageEntity();
				entity.setCsvId(csvId);
				entity.setDataCategory(dataCategory);
				entity.setSeason(season);
				entity.setHomeTeamName(home);
				entity.setAwayTeamName(away);

				CsvDetailManageEntity selEntity = this.csvDetailManageRepository
						.select(entity);
				dto.setExistFlg(true);
				if (selEntity == null) {
					dto.setExistFlg(false);
				}
				return dto;
			}
		}
		return dto;
	}

	/**
	 * CSV詳細管理登録
	 * @param stat
	 */
	private void insertCsvDetail(
			CsvDetailEntityOutputDTO existDto) {
		final String METHOD_NAME = "insertCsvDetail";

		if (existDto == null) {
			return;
		}

		CsvDetailManageEntity entity = new CsvDetailManageEntity();
		entity.setCsvId(existDto.getCsvId());
		entity.setDataCategory(existDto.getDataCategory());
		entity.setSeason(existDto.getSeason());
		entity.setHomeTeamName(existDto.getHomeTeamName());
		entity.setAwayTeamName(existDto.getAwayTeamName());
		entity.setCheckFinFlg("1");

		String context = buildCsvDetailContext(
				existDto.getDataCategory(), existDto.getSeason(),
				existDto.getHomeTeamName(), existDto.getAwayTeamName());
		int resultLog = -99;
		int result = this.csvDetailManageRepository.insert(entity);
		resultLog = result;
		if (result != 1) {
			// 基本はここにこない
			entity.setCheckFinFlg("0");
			int result2 = this.csvDetailManageRepository.insert(entity);
			if (result2 != 1) {
				String messageCd = MessageCdConst.MCD00007E_INSERT_FAILED;
				this.rootCauseWrapper.throwUnexpectedRowCount(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						messageCd,
						1, result2,
						context);
			}
			resultLog = result2;
		}

		String messageCd = MessageCdConst.MCD00005I_INSERT_SUCCESS;
		this.loggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
				"csv_detail_manage 登録件数: " + resultLog + "件 "
						+ "(" + context + ", csvId=" + existDto.getCsvId() + ")");
	}

	private BookDataEntity buildRepresentativeRow(List<BookDataEntity> rows) {
		if (rows == null || rows.isEmpty()) {
			return null;
		}

		BookDataEntity row = new BookDataEntity();
		row.setGameTeamCategory(firstNonBlank(rows, BookDataEntity::getGameTeamCategory));
		row.setHomeTeamName(firstNonBlank(rows, BookDataEntity::getHomeTeamName));
		row.setAwayTeamName(firstNonBlank(rows, BookDataEntity::getAwayTeamName));
		row.setRecordTime(firstNonBlank(rows, BookDataEntity::getRecordTime));
		row.setTime(firstNonBlank(rows, BookDataEntity::getTime));
		row.setHomeScore(firstNonBlank(rows, BookDataEntity::getHomeScore));
		row.setAwayScore(firstNonBlank(rows, BookDataEntity::getAwayScore));

		return row;
	}

	private static String safe(String s) {
		return (s == null) ? "" : s;
	}

	private String firstNonBlank(
			List<BookDataEntity> rows,
			java.util.function.Function<BookDataEntity, String> getter) {

		if (rows == null || rows.isEmpty()) {
			return null;
		}

		for (BookDataEntity e : rows) {
			if (e == null) {
				continue;
			}
			String value = getter.apply(e);
			if (value != null && !value.trim().isEmpty()) {
				return value;
			}
		}
		return null;
	}

	private String buildCsvDetailContext(
			String dataCategory,
			String season,
			String home,
			String away) {

		return String.format("%s(%s): %s vs %s",
				safe(dataCategory).trim(),
				safe(season).trim(),
				safe(home).trim(),
				safe(away).trim());
	}

}
