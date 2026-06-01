package dev.application.main.service;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.CannotCreateTransactionException;

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
public class CoreStat implements StatIF {

	private static final String PROJECT_NAME = CoreStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	private static final String CLASS_NAME = CoreStat.class.getName();

	private static final String CSV_ID_MANUAL = "<UNKNOWN_COUNTRY>-<UNKNOWN_LEAGUE>-<UNKNOWN_ROUND>/-99.csv";

	/** 接続断系の再試行回数 */
	private static final int DB_RETRY_MAX = 3;

	/** 再試行待機(ms) */
	private static final long DB_RETRY_WAIT_MILLIS = 3000L;

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

		// gameTeamCategory が空文字の行は filePath の親フォルダ名から補完する
		fillBlankGameTeamCategoryFromFilePath(stat);

		try {
			List<CsvDetailEntityOutputDTO> dtoList = runWithRetry(
					"selectCsvDetail",
					() -> selectCsvDetail(stat, manualFlg));

			if (dtoList == null || dtoList.isEmpty()) {
				this.loggerComponent.debugInfoLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						MessageCdConst.MCD00002I_BATCH_EXECUTION_SKIP,
						"すでに統計反映済み、または登録対象データがありません。 inputStatSummary="
								+ buildStatSummaryForLog(stat));
				return 0;
			}

			if (!manualFlg) {
				runStatWithRetry("conditionResultDataStat",
						() -> this.conditionResultDataStat.calcStat(stat));
			}

			runStatWithRetry("teamMonthlyScoreSummaryStat",
					() -> this.teamMonthlyScoreSummaryStat.calcStat(stat));

			if (!manualFlg) {
				runStatWithRetry("teamTimeSegmentShootingStat",
						() -> this.teamTimeSegmentShootingStat.calcStat(stat));
			}

			if (!manualFlg) {
				runStatWithRetry("countryLeagueSummaryStat",
						() -> this.countryLeagueSummaryStat.calcStat(stat));
			}

			runStatWithRetry("noGoalMatchStat",
					() -> this.noGoalMatchStat.calcStat(stat));

			if (!manualFlg) {
				runStatWithRetry("leagueScoreTimeBandStat",
						() -> this.leagueScoreTimeBandStat.calcStat(stat));
			}

			if (!manualFlg) {
				runStatWithRetry("matchClassificationResultStat",
						() -> this.matchClassificationResultStat.calcStat(stat));
			}

			runStatWithRetry("teamMatchFinalStat",
					() -> this.teamMatchFinalStat.calcStat(stat));

			if (!manualFlg) {
				runStatWithRetry("scoreBasedFeatureStat",
						() -> this.scoreBasedFeatureStat.calcStat(stat));
			}

			if (!manualFlg) {
				runStatWithRetry("calcCorrelationStat",
						() -> this.calcCorrelationStat.calcStat(stat));
			}

			if (!manualFlg) {
				runStatWithRetry("calcCorrelationRankingStat",
						() -> this.calcCorrelationRankingStat.calcStat(stat));
			}

			if (!manualFlg) {
				runStatWithRetry("eachTeamScoreBasedFeatureStat",
						() -> this.eachTeamScoreBasedFeatureStat.calcStat(stat));
			}

			runStatWithRetry("surfaceOverviewStat",
					() -> this.surfaceOverviewStat.calcStat(stat));

			runStatWithRetry("rankHistoryStat",
					() -> this.rankHistoryStat.calcStat(stat, manualFlg));

			for (CsvDetailEntityOutputDTO dto : dtoList) {
				runWithRetry(
						"upsertCsvDetail:" + buildCsvDetailContext(
								dto.getDataCategory(),
								dto.getSeason(),
								dto.getHomeTeamName(),
								dto.getAwayTeamName()),
						() -> {
							upsertCsvDetail(dto);
							return null;
						});
			}

			return dtoList.size();

		} catch (Exception e) {
			this.loggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00099I_LOG, null,
					"CoreStat execute failed. message=" + safe(e.getMessage()));
			throw e;

		} finally {
			if (stat != null) {
				stat.clear();
			}

			this.loggerComponent.debugEndInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		}
	}

	/**
	 * データ取得およびcsv_detail_manageで完了フラグ更新用の保持設定メソッド
	 * @param stat
	 * @param manualFlg
	 * @return
	 */
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
						: row.getFilePath();

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

		// 完了フラグが0のデータ（すでに統計データ反映済み）を取得
		List<CsvDetailManageEntity> existingList = this.csvDetailManageRepository
				.selectCheckedNotFinByExactKeys(candidates);

		if (existingList == null || existingList.isEmpty()) {
			return new ArrayList<>();
		}

		return existingList.stream()
				.map(e -> {
					CsvDetailEntityOutputDTO out = new CsvDetailEntityOutputDTO();
					out.setCsvId(e.getCsvId());
					out.setDataCategory(e.getDataCategory());
					out.setSeason(e.getSeason());
					out.setHomeTeamName(e.getHomeTeamName());
					out.setAwayTeamName(e.getAwayTeamName());
					out.setExistFlg(true);
					return out;
				})
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
				.findSeasonYear(dataList.get(0), dataList.get(1))).trim();
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

	private void runStatWithRetry(String statName, CheckedRunnable job) throws Exception {
		final String METHOD_NAME = "runStatWithRetry";

		runWithRetry("stat:" + statName, () -> {
			this.loggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00099I_LOG,
					"stat start: " + statName);
			job.run();
			this.loggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00099I_LOG,
					"stat end: " + statName);
			return null;
		});
	}

	private <T> T runWithRetry(String processName, CheckedSupplier<T> supplier) throws Exception {
		final String METHOD_NAME = "runWithRetry";

		int attempt = 0;
		while (true) {
			attempt++;
			try {
				return supplier.get();
			} catch (Exception e) {
				boolean retryable = isRetryableDbException(e);

				if (!retryable || attempt >= DB_RETRY_MAX) {
					this.loggerComponent.debugErrorLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME,
							MessageCdConst.MCD00099I_LOG, null,
							"retry give up. process=" + processName
									+ ", attempt=" + attempt
									+ ", retryable=" + retryable
									+ ", message=" + safe(e.getMessage()));
					throw e;
				}

				this.loggerComponent.debugWarnLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						MessageCdConst.MCD00099I_LOG,
						"retry execute. process=" + processName
								+ ", attempt=" + attempt
								+ "/" + DB_RETRY_MAX
								+ ", waitMillis=" + DB_RETRY_WAIT_MILLIS
								+ ", message=" + safe(e.getMessage()));

				sleepQuietly(DB_RETRY_WAIT_MILLIS);
			}
		}
	}

	private boolean isRetryableDbException(Throwable t) {
		Throwable current = t;

		while (current != null) {
			if (current instanceof CannotGetJdbcConnectionException) {
				return true;
			}
			if (current instanceof CannotCreateTransactionException) {
				return true;
			}
			if (current instanceof TransientDataAccessException) {
				return true;
			}
			if (current instanceof RecoverableDataAccessException) {
				return true;
			}
			if (current instanceof SQLException) {
				String state = ((SQLException) current).getSQLState();
				if (state != null && state.startsWith("08")) {
					return true;
				}
			}

			String className = safe(current.getClass().getName());
			String message = safe(current.getMessage()).toLowerCase();

			if (className.contains("SQLTransientConnectionException")
					|| className.contains("SQLRecoverableException")) {
				return true;
			}

			if (message.contains("connection is closed")
					|| message.contains("connection has been closed")
					|| message.contains("broken pipe")
					|| message.contains("connection reset")
					|| message.contains("communications link failure")
					|| message.contains("could not open jdbc connection")
					|| message.contains("failed to obtain jdbc connection")
					|| message.contains("the connection attempt failed")
					|| message.contains("socket closed")
					|| message.contains("connection refused")
					|| message.contains("i/o error occurred while sending to the backend")) {
				return true;
			}

			current = current.getCause();
		}

		return false;
	}

	private void sleepQuietly(long millis) throws InterruptedException {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw e;
		}
	}

	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws Exception;
	}

	@FunctionalInterface
	private interface CheckedSupplier<T> {
		T get() throws Exception;
	}

	private void fillBlankGameTeamCategoryFromFilePath(
			Map<String, Map<String, List<BookDataEntity>>> stat) {

		final String METHOD_NAME = "fillBlankGameTeamCategoryFromFilePath";

		if (stat == null || stat.isEmpty()) {
			return;
		}

		for (Map<String, List<BookDataEntity>> innerMap : stat.values()) {
			if (innerMap == null || innerMap.isEmpty()) {
				continue;
			}

			for (List<BookDataEntity> rows : innerMap.values()) {
				if (rows == null || rows.isEmpty()) {
					continue;
				}

				int fillCount = 0;

				for (BookDataEntity row : rows) {
					if (row == null) {
						continue;
					}

					// 既に入っているものは触らない
					if (!safe(row.getGameTeamCategory()).trim().isEmpty()) {
						continue;
					}

					String fillValue = extractCategoryFromFilePath(row.getFilePath());
					if (fillValue.isEmpty()) {
						continue;
					}

					row.setGameTeamCategory(fillValue);
					fillCount++;
				}

				if (fillCount > 0) {
					BookDataEntity sample = rows.stream()
							.filter(e -> e != null)
							.findFirst()
							.orElse(null);

					this.loggerComponent.debugInfoLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME,
							MessageCdConst.MCD00099I_LOG,
							"gameTeamCategory を filePath から補完しました: fillCount=" + fillCount
									+ ", filePath=" + safe(sample == null ? null : sample.getFilePath())
									+ ", category=" + safe(sample == null ? null : sample.getGameTeamCategory()));
				}
			}
		}
	}

	/**
	 * S3 key からカテゴリ名を抽出する。
	 *
	 * 例:
	 * - Japan-J1-ラウンド5/9.csv                -> Japan-J1-ラウンド5
	 * - stats/Japan-J1-ラウンド5/9.csv          -> Japan-J1-ラウンド5
	 * - 9.csv                                  -> ""
	 */
	private String extractCategoryFromFilePath(String filePath) {
		String path = safe(filePath).trim();
		if (path.isEmpty()) {
			return "";
		}

		path = path.replace("\\", "/");

		int lastSlash = path.lastIndexOf('/');
		if (lastSlash <= 0) {
			return "";
		}

		String parentPath = path.substring(0, lastSlash);
		int parentSlash = parentPath.lastIndexOf('/');

		String folderName = (parentSlash >= 0)
				? parentPath.substring(parentSlash + 1)
				: parentPath;

		return safe(folderName).trim();
	}

	/**
	 * ログ詳細用ビルダー
	 * @param stat
	 * @return
	 */
	private String buildStatSummaryForLog(
			Map<String, Map<String, List<BookDataEntity>>> stat) {

		if (stat == null || stat.isEmpty()) {
			return "stat is empty";
		}

		List<String> details = new ArrayList<>();
		int maxLogCount = 10;
		int count = 0;

		for (Map.Entry<String, Map<String, List<BookDataEntity>>> outer : stat.entrySet()) {
			String categoryKey = safe(outer.getKey());

			Map<String, List<BookDataEntity>> innerMap = outer.getValue();
			if (innerMap == null || innerMap.isEmpty()) {
				continue;
			}

			for (Map.Entry<String, List<BookDataEntity>> inner : innerMap.entrySet()) {
				List<BookDataEntity> rows = inner.getValue();
				if (rows == null || rows.isEmpty()) {
					continue;
				}

				BookDataEntity row = buildRepresentativeRow(rows);
				if (row == null) {
					continue;
				}

				details.add(String.format(
						"{categoryKey=%s, gameTeamCategory=%s, home=%s, away=%s, filePath=%s}",
						categoryKey,
						safe(row.getGameTeamCategory()).trim(),
						safe(row.getHomeTeamName()).trim(),
						safe(row.getAwayTeamName()).trim(),
						safe(row.getFilePath()).trim()));

				count++;
				if (count >= maxLogCount) {
					break;
				}
			}

			if (count >= maxLogCount) {
				break;
			}
		}

		return "size=" + details.size() + ", details=" + details;
	}

	private static String safe(String s) {
		return (s == null) ? "" : s;
	}
}
