package dev.application.main.service;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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
import dev.common.logger.ManageLoggerComponent;
import dev.common.util.ExecuteMainUtil;

@Service
@Transactional
public class CoreStat implements StatIF {

	private static final String PROJECT_NAME = CoreStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	private static final String CLASS_NAME = CoreStat.class.getName();

	private static final String CSV_ID_MANUAL = "<UNKNOWN_COUNTRY>-<UNKNOWN_LEAGUE>-<UNKNOWN_ROUND>/-99.csv";

	@Autowired
	private ConditionResultDataStat conditionResultDataStat;
	@Autowired
	private TeamMonthlyScoreSummaryStat teamMonthlyScoreSummaryStat;
	@Autowired
	private TeamTimeSegmentShootingStat teamTimeSegmentShootingStat;
	@Autowired
	private NoGoalMatchStat noGoalMatchStat;
	@Autowired
	private CountryLeagueSummaryStat countryLeagueSummaryStat;
	@Autowired
	private LeagueScoreTimeBandStat leagueScoreTimeBandStat;
	@Autowired
	private MatchClassificationResultStat matchClassificationResultStat;
	@Autowired
	private TeamMatchFinalStat teamMatchFinalStat;
	@Autowired
	private ScoreBasedFeatureStat scoreBasedFeatureStat;
	@Autowired
	private CalcCorrelationStat calcCorrelationStat;
	@Autowired
	private CalcCorrelationRankingStat calcCorrelationRankingStat;
	@Autowired
	private EachTeamScoreBasedFeatureStat eachTeamScoreBasedFeatureStat;
	@Autowired
	private SurfaceOverviewStat surfaceOverviewStat;
	@Autowired
	private RankHistoryStat rankHistoryStat;

	@Autowired
	private CountryLeagueSeasonMasterRepository countryLeagueSeasonMasterBatchRepository;

	@Autowired
	private CsvDetailManageRepository csvDetailManageRepository;

	@Autowired
	private ManageLoggerComponent loggerComponent;

	@Override
	public int execute(Map<String, Map<String, List<BookDataEntity>>> stat, boolean manualFlg) throws Exception {
		final String METHOD_NAME = "execute";

		this.loggerComponent.debugStartInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		List<CsvDetailEntityOutputDTO> dtoList = selectCsvDetail(stat, manualFlg);
		if (dtoList == null || dtoList.isEmpty()) {
			this.loggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00002I_BATCH_EXECUTION_SKIP,
					"すでに統計反映済み、または登録対象データがありません。");
			return 0;
		}

		if (!manualFlg) {
			this.conditionResultDataStat.calcStat(stat);
		}
		this.teamMonthlyScoreSummaryStat.calcStat(stat);
		if (!manualFlg) {
			this.teamTimeSegmentShootingStat.calcStat(stat);
		}
		if (!manualFlg) {
			this.countryLeagueSummaryStat.calcStat(stat);
		}
		this.noGoalMatchStat.calcStat(stat);
		if (!manualFlg) {
			this.leagueScoreTimeBandStat.calcStat(stat);
		}
		if (!manualFlg) {
			this.matchClassificationResultStat.calcStat(stat);
		}
		this.teamMatchFinalStat.calcStat(stat);
		if (!manualFlg) {
			this.scoreBasedFeatureStat.calcStat(stat);
		}
		if (!manualFlg) {
			this.calcCorrelationStat.calcStat(stat);
		}
		if (!manualFlg) {
			this.calcCorrelationRankingStat.calcStat(stat);
		}
		if (!manualFlg) {
			this.eachTeamScoreBasedFeatureStat.calcStat(stat);
		}
		this.surfaceOverviewStat.calcStat(stat);
		this.rankHistoryStat.calcStat(stat);

		if (manualFlg) {
			for (CsvDetailEntityOutputDTO dto : dtoList) {
				upsertCsvDetail(dto);
			}
		}

		stat.clear();

		this.loggerComponent.debugEndInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		return dtoList.size();
	}

	private List<CsvDetailEntityOutputDTO> selectCsvDetail(
			Map<String, Map<String, List<BookDataEntity>>> stat,
			boolean manualFlg) {

		final String METHOD_NAME = "selectCsvDetail";
		List<CsvDetailEntityOutputDTO> candidates = new ArrayList<>();

		if (stat == null || stat.isEmpty()) {
			return candidates;
		}

		Map<String, String> seasonCache = new LinkedHashMap<>();
		Set<String> candidateKeySet = new HashSet<>();

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
				String home = safe(row.getHomeTeamName()).trim();
				String away = safe(row.getAwayTeamName()).trim();

				if (dataCategory.isEmpty() || home.isEmpty() || away.isEmpty()) {
					continue;
				}

				String season = seasonCache.computeIfAbsent(
						dataCategory,
						this::resolveSeasonSafely);

				if (season.isEmpty()) {
					this.loggerComponent.debugWarnLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME,
							MessageCdConst.MCD00099I_LOG,
							"season取得失敗のためスキップ: dataCategory=" + dataCategory);
					continue;
				}

				String businessKey = buildCsvDetailKey(dataCategory, season, home, away);
				if (!candidateKeySet.add(businessKey)) {
					continue;
				}

				String csvId = manualFlg
						? CSV_ID_MANUAL
						: resolveCsvId(row.getFilePath());

				if (csvId.isEmpty()) {
					continue;
				}

				CsvDetailEntityOutputDTO dto = new CsvDetailEntityOutputDTO();
				dto.setCsvId(csvId);
				dto.setDataCategory(dataCategory);
				dto.setSeason(season);
				dto.setHomeTeamName(home);
				dto.setAwayTeamName(away);
				dto.setExistFlg(false);

				candidates.add(dto);
			}
		}

		if (candidates.isEmpty()) {
			return candidates;
		}

		List<String> dataCategories = candidates.stream()
				.map(CsvDetailEntityOutputDTO::getDataCategory)
				.filter(s -> !safe(s).trim().isEmpty())
				.distinct()
				.collect(Collectors.toList());

		if (dataCategories.isEmpty()) {
			return candidates;
		}

		Set<String> existingKeySet = this.csvDetailManageRepository
				.selectCheckedFinByDataCategories(dataCategories)
				.stream()
				.map(e -> buildCsvDetailKey(
						e.getDataCategory(),
						e.getSeason(),
						e.getHomeTeamName(),
						e.getAwayTeamName()))
				.collect(Collectors.toSet());

		return candidates.stream()
				.filter(dto -> !existingKeySet.contains(buildCsvDetailKey(
						dto.getDataCategory(),
						dto.getSeason(),
						dto.getHomeTeamName(),
						dto.getAwayTeamName())))
				.collect(Collectors.toList());
	}

	private void upsertCsvDetail(CsvDetailEntityOutputDTO dto) {
		final String METHOD_NAME = "upsertCsvDetail";

		if (dto == null) {
			return;
		}

		CsvDetailManageEntity entity = new CsvDetailManageEntity();
		entity.setCsvId(dto.getCsvId());
		entity.setDataCategory(dto.getDataCategory());
		entity.setSeason(dto.getSeason());
		entity.setHomeTeamName(dto.getHomeTeamName());
		entity.setAwayTeamName(dto.getAwayTeamName());
		entity.setCheckFinFlg("1");

		int result = this.csvDetailManageRepository.upsert(entity);

		String messageCd = MessageCdConst.MCD00005I_INSERT_SUCCESS;
		this.loggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
				"csv_detail_manage upsert件数: " + result + "件 ("
						+ buildCsvDetailContext(
								dto.getDataCategory(),
								dto.getSeason(),
								dto.getHomeTeamName(),
								dto.getAwayTeamName())
						+ ", csvId=" + dto.getCsvId() + ")");
	}

	private String resolveSeasonSafely(String dataCategory) {
		List<String> dataList = ExecuteMainUtil.getCountryLeagueByRegex(dataCategory);
		if (dataList == null || dataList.size() < 2) {
			return "";
		}
		return safe(countryLeagueSeasonMasterBatchRepository
				.findCurrentSeasonYear(dataList.get(0), dataList.get(1))).trim();
	}

	private String resolveCsvId(String filePath) {
		String file = safe(filePath).trim();
		if (file.isEmpty()) {
			return "";
		}
		String fileName = Paths.get(file).getFileName().toString();
		int dot = fileName.lastIndexOf('.');
		return (dot >= 0) ? fileName.substring(0, dot) : fileName;
	}

	private String buildCsvDetailKey(
			String dataCategory,
			String season,
			String home,
			String away) {

		return String.join("||",
				safe(dataCategory).trim(),
				safe(season).trim(),
				safe(home).trim(),
				safe(away).trim());
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
		row.setFilePath(firstNonBlank(rows, BookDataEntity::getFilePath));
		row.setSeq(firstNonBlank(rows, BookDataEntity::getSeq));
		return row;
	}

	private String firstNonBlank(
			List<BookDataEntity> rows,
			Function<BookDataEntity, String> getter) {

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

	private static String safe(String s) {
		return (s == null) ? "" : s;
	}
}
