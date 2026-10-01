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
import dev.application.analyze.bm_m004.TeamTimeSegmentStat;
import dev.application.analyze.bm_m005.NoGoalMatchStat;
import dev.application.analyze.bm_m006.CountryLeagueSummaryStat;
import dev.application.analyze.bm_m017.LeagueScoreTimeBandStat;
import dev.application.analyze.bm_m018.MatchClassificationResultStat;
import dev.application.analyze.bm_m021.TeamMatchFinalStat;
import dev.application.analyze.bm_m023.ScoreBasedFeatureStat;
import dev.application.analyze.bm_m024.CalcCorrelationStat;
import dev.application.analyze.bm_m031.SurfaceOverviewStat;
import dev.application.analyze.bm_m034.MatchTeamSnapshotFactStat;
import dev.application.analyze.interf.AnalyzeEntityIF;
import dev.application.analyze.interf.StatIF;
import dev.application.domain.repository.bm.CsvDetailManageRepository;
import dev.application.domain.repository.master.CountryLeagueSeasonMasterRepository;
import dev.common.constant.MessageCdConst;
import dev.common.entity.BookDataEntity;
import dev.common.entity.CsvDetailManageEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.util.ExecuteMainUtil;

/**
 * 統計（BM_M002〜BM_M044）の一括実行。
 *
 * <h2>何をするクラスか</h2>
 * <p>
 * CSV（または手動データ）から読んだ試合データを各統計クラス（Stat）に渡して保存し、
 * 処理した試合を csv_detail_manage に「反映済み」として記録する。
 * </p>
 * <ol>
 *   <li>csv_detail_manage から処理対象の試合を決める（CSV: 未完了 check_fin_flg='0' の試合 / 手動: まだ記録の無い試合）。
 *       対象が無ければ何もしない。</li>
 *   <li>{@link #statSteps()} の順に各 Stat を実行する（手動データでは手動対象の Stat だけ）。</li>
 *   <li>CSV は csv_detail_manage を完了に更新、手動はダミーの CSV ID で完了行を登録する。</li>
 * </ol>
 *
 * <h2>実行する Stat と、Stat を持たない BM</h2>
 * <table border="1">
 *   <caption>BM と実行するもの</caption>
 *   <tr><th>BM</th><th>実行するもの</th><th>手動データ</th></tr>
 *   <tr><td>M002</td><td>ConditionResultDataStat</td><td>×</td></tr>
 *   <tr><td>M003</td><td>TeamMonthlyScoreSummaryStat</td><td>○</td></tr>
 *   <tr><td>M004</td><td>TeamTimeSegmentStat</td><td>×</td></tr>
 *   <tr><td>M005</td><td>NoGoalMatchStat</td><td>○</td></tr>
 *   <tr><td>M006</td><td>CountryLeagueSummaryStat</td><td>×</td></tr>
 *   <tr><td>M017 / M018</td><td>LeagueScoreTimeBandStat（件数はビュー）</td><td>×</td></tr>
 *   <tr><td>M019 / M020</td><td>MatchClassificationResultStat（M020 はビュー classify_result_data_detail）</td><td>×</td></tr>
 *   <tr><td>M021</td><td>TeamMatchFinalStat</td><td>○</td></tr>
 *   <tr><td>M023 / M026</td><td>ScoreBasedFeatureStat（M026 のチーム別は M023 の明細から出すビュー）</td><td>×</td></tr>
 *   <tr><td>M024</td><td>CalcCorrelationStat</td><td>×</td></tr>
 *   <tr><td>M025 / M027</td><td>なし（相関・特徴量のランキングはビュー）</td><td>-</td></tr>
 *   <tr><td>M031 / M032 / M033</td><td>SurfaceOverviewStat（差分 M032・順位 M033 はビュー）</td><td>○</td></tr>
 *   <tr><td>M034</td><td>MatchTeamSnapshotFactStat（試合中の時点ごとの明細）</td><td>×</td></tr>
 *   <tr><td>M035〜M042</td><td>なし（M034・M031 の明細から計算するビュー）</td><td>-</td></tr>
 *   <tr><td>M029</td><td>ここでは実行しない（入力が DataEntity のリアルタイム処理。M034 のビュー match_team_snapshot_latest で置き換え可）</td><td>-</td></tr>
 *   <tr><td>M030 / M043 / M044</td><td>廃止（テーブルも削除）</td><td>-</td></tr>
 * </table>
 *
 * <h2>修正内容</h2>
 * <ul>
 *   <li>Stat の呼び出しを {@link StatStep} の一覧にまとめ、「手動データでも実行するか」を一覧で見えるようにした
 *       （同じ形の if と runStatWithRetry が14個並んでいた）。</li>
 *   <li>無くなったクラスの呼び出しを削除: CalcCorrelationRankingStat（M025 → ビュー）、
 *       EachTeamScoreBasedFeatureStat（M026 → M023 に統合）、RankHistoryStat（M033 → ビュー surface_overview_standing）。</li>
 *   <li>呼ばれていなかった MatchTeamSnapshotFactStat（M034）を追加。M035〜M042 のビューはこの明細から計算するため、
 *       実行しないと M035〜M042 が空になる。</li>
 *   <li>名前の修正: teamTimeSegmentShootingStat → teamTimeSegmentStat、
 *       countryLeagueSeasonMasterBatchRepository → countryLeagueSeasonMasterRepository。</li>
 *   <li>手動データが反映済みのときにスキップされていなかった: selectCsvDetail は反映済みなら空のリストを返すが、
 *       スキップの条件は「空でなく existFlg = true」だったため、空のまま全 Stat が流れていた。対象が空ならスキップするようにした。</li>
 *   <li>シーズン取得で、マスタに無い国・リーグだと findSeasonYear(...).get(0) が IndexOutOfBoundsException になり、
 *       1回分の処理全体が止まっていた。取れなければその試合だけスキップする。</li>
 * </ul>
 *
 * <h2>懸念点・エラーが起こりそうな箇所</h2>
 * <ul>
 *   <li><b>再試行</b>: 接続断などでは Stat を最初からやり直す。各 Stat の保存は試合単位の UPSERT なので、
 *       やり直しても行は増えない（M003 だけは Mapper 未確認。README 参照）。</li>
 *   <li><b>途中で失敗した場合</b>: csv_detail_manage は最後にまとめて更新するため、失敗した回の試合は未完了のまま残り、
 *       次回もう一度処理される（保存は UPSERT なので重複しない）。</li>
 *   <li><b>filePath から補ったカテゴリ（"Japan-J1-ラウンド5" 形式）</b>: csv_detail_manage の記録には使うが、
 *       Stat が見るのは Map の外側のキー（「国: リーグ - ラウンドN」形式でなければ無視）なので、統計には入らない。</li>
 *   <li><b>stat は処理後に clear する</b>（呼び出し元のメモリを空けるため）。呼び出し後に同じ Map を使わないこと。</li>
 *   <li>csv_detail_manage のシーズンはマスタの最新シーズン。各 Writer のシーズン（SeasonResolverIF）と同じ値になる前提。</li>
 * </ul>
 */
@Service
public class CoreStat implements StatIF {

	private static final String PROJECT_NAME = CoreStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	private static final String CLASS_NAME = CoreStat.class.getName();

	/** 手動データの CSV ID（csv_detail_manage に登録するダミー） */
	private static final String CSV_ID_MANUAL = "<UNKNOWN_COUNTRY>-<UNKNOWN_LEAGUE>-<UNKNOWN_ROUND>/-99.csv";

	/** 接続断系の再試行回数 */
	private static final int DB_RETRY_MAX = 3;

	/** 再試行待機(ms) */
	private static final long DB_RETRY_WAIT_MILLIS = 3000L;

	/** ログに出す件数の上限 */
	private static final int MAX_LOG_COUNT = 10;

	@Autowired
	private ConditionResultDataStat conditionResultDataStat;
	@Autowired
	private TeamMonthlyScoreSummaryStat teamMonthlyScoreSummaryStat;
	@Autowired
	private TeamTimeSegmentStat teamTimeSegmentStat;
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
	private SurfaceOverviewStat surfaceOverviewStat;
	@Autowired
	private MatchTeamSnapshotFactStat matchTeamSnapshotFactStat;

	@Autowired
	private CountryLeagueSeasonMasterRepository countryLeagueSeasonMasterRepository;

	@Autowired
	private CsvDetailManageRepository csvDetailManageRepository;

	@Autowired
	private ManageLoggerComponent loggerComponent;

	/**
	 * 実行する Stat の一覧（この順に実行する）。
	 * 手動データ（manualFlg = true）では manualTarget = true のものだけ実行する。
	 */
	private List<StatStep> statSteps() {
		List<StatStep> steps = new ArrayList<>();
		steps.add(new StatStep("BM_M002 conditionResultDataStat", false, this.conditionResultDataStat));
		steps.add(new StatStep("BM_M003 teamMonthlyScoreSummaryStat", true, this.teamMonthlyScoreSummaryStat));
		steps.add(new StatStep("BM_M004 teamTimeSegmentStat", false, this.teamTimeSegmentStat));
		steps.add(new StatStep("BM_M006 countryLeagueSummaryStat", false, this.countryLeagueSummaryStat));
		steps.add(new StatStep("BM_M005 noGoalMatchStat", true, this.noGoalMatchStat));
		steps.add(new StatStep("BM_M017/M018 leagueScoreTimeBandStat", false, this.leagueScoreTimeBandStat));
		steps.add(new StatStep("BM_M019/M020 matchClassificationResultStat", false, this.matchClassificationResultStat));
		steps.add(new StatStep("BM_M021 teamMatchFinalStat", true, this.teamMatchFinalStat));
		steps.add(new StatStep("BM_M023/M026 scoreBasedFeatureStat", false, this.scoreBasedFeatureStat));
		steps.add(new StatStep("BM_M024 calcCorrelationStat", false, this.calcCorrelationStat));
		steps.add(new StatStep("BM_M031/M032/M033 surfaceOverviewStat", true, this.surfaceOverviewStat));
		steps.add(new StatStep("BM_M034 matchTeamSnapshotFactStat", false, this.matchTeamSnapshotFactStat));
		return steps;
	}

	@Override
	public int execute(Map<String, Map<String, List<BookDataEntity>>> stat, boolean manualFlg) throws Exception {
		final String METHOD_NAME = "execute";

		this.loggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		// gameTeamCategory が空文字の行は filePath の親フォルダ名から補完する（csv_detail_manage の記録用）
		fillBlankGameTeamCategoryFromFilePath(stat);

		try {
			// CSV: 未完了の試合だけ / 手動: まだ記録の無い試合だけ（反映済みなら空）
			List<CsvDetailEntityOutputDTO> dtoList = runWithRetry(
					"selectCsvDetail",
					() -> selectCsvDetail(stat, manualFlg));

			if (dtoList == null || dtoList.isEmpty()) {
				this.loggerComponent.debugInfoLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						MessageCdConst.MCD00002I_BATCH_EXECUTION_SKIP,
						"すでに統計反映済み、または登録対象データがありません。 manualFlg=" + manualFlg
								+ ", inputStatSummary=" + buildStatSummaryForLog(stat));
				return 0;
			}

			for (StatStep step : statSteps()) {
				if (manualFlg && !step.manualTarget) {
					continue;
				}
				runStatWithRetry(step.name, () -> step.stat.calcStat(stat));
			}

			// CSV は反映済みに更新、手動はダミーの CSV ID で反映済みを登録
			for (CsvDetailEntityOutputDTO dto : dtoList) {
				String context = (manualFlg ? "insertCsvDetail:" : "updateCsvDetail:") + buildCsvDetailContextCsvId(dto);
				runWithRetry(context, () -> {
					if (manualFlg) {
						insertCsvDetail(dto);
					} else {
						updateCsvDetail(dto);
					}
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
			this.loggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		}
	}

	/**
	 * 処理対象の試合を決める。
	 *
	 * @param stat 入力データ
	 * @param manualFlg true: 手動データ
	 * @return CSV: csv_detail_manage で未完了（check_fin_flg='0'）の試合 /
	 *         手動: 記録がまだ無ければ入力の全試合、1件でもあれば空（反映済み）
	 */
	private List<CsvDetailEntityOutputDTO> selectCsvDetail(
			Map<String, Map<String, List<BookDataEntity>>> stat, boolean manualFlg) {

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

				String season = seasonCache.computeIfAbsent(dataCategory, this::resolveSeasonSafely);
				if (season.isEmpty()) {
					this.loggerComponent.debugWarnLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME,
							MessageCdConst.MCD00099I_LOG,
							"season取得失敗のためスキップ: dataCategory=" + dataCategory);
					continue;
				}

				if (!candidateKeySet.add(String.join("||", dataCategory, season, home, away))) {
					continue;
				}

				String csvId = manualFlg ? CSV_ID_MANUAL : safe(row.getFilePath()).trim();
				if (csvId.isEmpty()) {
					continue;
				}

				candidates.add(newDto(csvId, dataCategory, season, home, away, false));
			}
		}

		if (candidates.isEmpty()) {
			return candidates;
		}

		if (manualFlg) {
			// 手動データ: 既存の記録が1件でもあれば反映済み
			List<CsvDetailManageEntity> existingAnyList = this.csvDetailManageRepository.selectByExactKeys(candidates);
			if (existingAnyList != null && !existingAnyList.isEmpty()) {
				this.loggerComponent.debugInfoLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						MessageCdConst.MCD00099I_LOG,
						"manual data already exists. skip. existing=" + buildCsvDetailManageSummaryForLog(existingAnyList));
				return new ArrayList<>();
			}
			return candidates;
		}

		// CSV データ: 未完了（check_fin_flg='0'）の試合だけ
		List<CsvDetailManageEntity> existingNotFinList = this.csvDetailManageRepository
				.selectCheckedNotFinByExactKeys(candidates);
		if (existingNotFinList == null || existingNotFinList.isEmpty()) {
			this.loggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00099I_LOG,
					"csv data not found as check_fin_flg=0. skip. candidates=" + buildDtoSummaryForLog(candidates));
			return new ArrayList<>();
		}
		return existingNotFinList.stream()
				.map(e -> newDto(e.getCsvId(), e.getDataCategory(), e.getSeason(),
						e.getHomeTeamName(), e.getAwayTeamName(), true))
				.collect(Collectors.toList());
	}

	private static CsvDetailEntityOutputDTO newDto(String csvId, String dataCategory, String season,
			String home, String away, boolean existFlg) {
		CsvDetailEntityOutputDTO dto = new CsvDetailEntityOutputDTO();
		dto.setCsvId(csvId);
		dto.setDataCategory(dataCategory);
		dto.setSeason(season);
		dto.setHomeTeamName(home);
		dto.setAwayTeamName(away);
		dto.setExistFlg(existFlg);
		return dto;
	}

	/**
	 * 手動データの反映済みを登録する（check_fin_flg = '1'）。
	 */
	private void insertCsvDetail(CsvDetailEntityOutputDTO dto) {
		final String METHOD_NAME = "insertCsvDetail";
		CsvDetailManageEntity entity = toEntity(dto);
		entity.setCheckFinFlg("1");
		int result = this.csvDetailManageRepository.insert(entity);
		this.loggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00005I_INSERT_SUCCESS,
				"csv_detail_manage insert件数: " + result + "件 (" + buildCsvDetailContextCsvId(dto) + ")");
	}

	/**
	 * CSV データを反映済みに更新する。
	 */
	private void updateCsvDetail(CsvDetailEntityOutputDTO dto) {
		final String METHOD_NAME = "updateCsvDetail";
		int result = this.csvDetailManageRepository.update(toEntity(dto));
		this.loggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00006I_UPDATE_SUCCESS,
				"csv_detail_manage update件数: " + result + "件 (" + buildCsvDetailContextCsvId(dto) + ")");
	}

	private static CsvDetailManageEntity toEntity(CsvDetailEntityOutputDTO dto) {
		CsvDetailManageEntity entity = new CsvDetailManageEntity();
		entity.setCsvId(dto.getCsvId());
		entity.setDataCategory(dto.getDataCategory());
		entity.setSeason(dto.getSeason());
		entity.setHomeTeamName(dto.getHomeTeamName());
		entity.setAwayTeamName(dto.getAwayTeamName());
		return entity;
	}

	/**
	 * 国・リーグの最新シーズン（マスタに無い・読めなければ空文字）。
	 */
	private String resolveSeasonSafely(String dataCategory) {
		List<String> dataList = ExecuteMainUtil.getCountryLeagueByRegex(dataCategory);
		if (dataList == null || dataList.size() < 2) {
			return "";
		}
		List<String> seasons = this.countryLeagueSeasonMasterRepository.findSeasonYear(dataList.get(0), dataList.get(1));
		if (seasons == null || seasons.isEmpty()) {
			return "";
		}
		return safe(seasons.get(0)).trim();
	}

	private BookDataEntity buildRepresentativeRow(List<BookDataEntity> rows) {
		if (rows == null || rows.isEmpty()) {
			return null;
		}
		BookDataEntity row = new BookDataEntity();
		row.setGameTeamCategory(firstNonBlank(rows, BookDataEntity::getGameTeamCategory));
		row.setHomeTeamName(firstNonBlank(rows, BookDataEntity::getHomeTeamName));
		row.setAwayTeamName(firstNonBlank(rows, BookDataEntity::getAwayTeamName));
		row.setFilePath(firstNonBlank(rows, BookDataEntity::getFilePath));
		return row;
	}

	private static String firstNonBlank(List<BookDataEntity> rows, Function<BookDataEntity, String> getter) {
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

	private static String buildCsvDetailContextCsvId(CsvDetailEntityOutputDTO dto) {
		return String.format("%s:%s(%s): %s vs %s",
				safe(dto.getCsvId()).trim(),
				safe(dto.getDataCategory()).trim(),
				safe(dto.getSeason()).trim(),
				safe(dto.getHomeTeamName()).trim(),
				safe(dto.getAwayTeamName()).trim());
	}

	// ===== 再試行 =====

	private void runStatWithRetry(String statName, CheckedRunnable job) throws Exception {
		final String METHOD_NAME = "runStatWithRetry";
		runWithRetry("stat:" + statName, () -> {
			this.loggerComponent.debugInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00099I_LOG, "stat start: " + statName);
			job.run();
			this.loggerComponent.debugInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00099I_LOG, "stat end: " + statName);
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
							"retry give up. process=" + processName + ", attempt=" + attempt
									+ ", retryable=" + retryable + ", message=" + safe(e.getMessage()));
					throw e;
				}
				this.loggerComponent.debugWarnLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						MessageCdConst.MCD00099I_LOG,
						"retry execute. process=" + processName + ", attempt=" + attempt + "/" + DB_RETRY_MAX
								+ ", waitMillis=" + DB_RETRY_WAIT_MILLIS + ", message=" + safe(e.getMessage()));
				sleepQuietly(DB_RETRY_WAIT_MILLIS);
			}
		}
	}

	/** 接続断・一時的な DB エラーか（原因の連鎖をたどって判定） */
	private static boolean isRetryableDbException(Throwable t) {
		Throwable current = t;
		while (current != null) {
			if (current instanceof CannotGetJdbcConnectionException
					|| current instanceof CannotCreateTransactionException
					|| current instanceof TransientDataAccessException
					|| current instanceof RecoverableDataAccessException) {
				return true;
			}
			if (current instanceof SQLException) {
				String state = ((SQLException) current).getSQLState();
				if (state != null && state.startsWith("08")) {
					return true;
				}
			}
			String className = safe(current.getClass().getName());
			if (className.contains("SQLTransientConnectionException")
					|| className.contains("SQLRecoverableException")) {
				return true;
			}
			String message = safe(current.getMessage()).toLowerCase();
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

	private static void sleepQuietly(long millis) throws InterruptedException {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw e;
		}
	}

	// ===== gameTeamCategory の補完 =====

	private void fillBlankGameTeamCategoryFromFilePath(Map<String, Map<String, List<BookDataEntity>>> stat) {
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
				BookDataEntity sample = null;
				for (BookDataEntity row : rows) {
					if (row == null || !safe(row.getGameTeamCategory()).trim().isEmpty()) {
						continue;
					}
					String fillValue = extractCategoryFromFilePath(row.getFilePath());
					if (fillValue.isEmpty()) {
						continue;
					}
					row.setGameTeamCategory(fillValue);
					fillCount++;
					sample = row;
				}
				if (fillCount > 0) {
					this.loggerComponent.debugInfoLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME,
							MessageCdConst.MCD00099I_LOG,
							"gameTeamCategory を filePath から補完しました: fillCount=" + fillCount
									+ ", filePath=" + safe(sample.getFilePath())
									+ ", category=" + safe(sample.getGameTeamCategory()));
				}
			}
		}
	}

	/**
	 * S3 key からカテゴリ名（親フォルダ名）を取り出す。
	 * <ul>
	 *   <li>Japan-J1-ラウンド5/9.csv → Japan-J1-ラウンド5</li>
	 *   <li>stats/Japan-J1-ラウンド5/9.csv → Japan-J1-ラウンド5</li>
	 *   <li>9.csv → ""</li>
	 * </ul>
	 */
	private static String extractCategoryFromFilePath(String filePath) {
		String path = safe(filePath).trim().replace("\\", "/");
		int lastSlash = path.lastIndexOf('/');
		if (lastSlash <= 0) {
			return "";
		}
		String parentPath = path.substring(0, lastSlash);
		int parentSlash = parentPath.lastIndexOf('/');
		return (parentSlash >= 0 ? parentPath.substring(parentSlash + 1) : parentPath).trim();
	}

	// ===== ログ =====

	private String buildStatSummaryForLog(Map<String, Map<String, List<BookDataEntity>>> stat) {
		if (stat == null || stat.isEmpty()) {
			return "stat is empty";
		}
		List<String> details = new ArrayList<>();
		outer:
		for (Map.Entry<String, Map<String, List<BookDataEntity>>> outer : stat.entrySet()) {
			if (outer.getValue() == null) {
				continue;
			}
			for (List<BookDataEntity> rows : outer.getValue().values()) {
				BookDataEntity row = buildRepresentativeRow(rows);
				if (row == null) {
					continue;
				}
				details.add(String.format(
						"{categoryKey=%s, gameTeamCategory=%s, home=%s, away=%s, filePath=%s}",
						safe(outer.getKey()),
						safe(row.getGameTeamCategory()).trim(),
						safe(row.getHomeTeamName()).trim(),
						safe(row.getAwayTeamName()).trim(),
						safe(row.getFilePath()).trim()));
				if (details.size() >= MAX_LOG_COUNT) {
					break outer;
				}
			}
		}
		return "size=" + details.size() + ", details=" + details;
	}

	private static String buildDtoSummaryForLog(List<CsvDetailEntityOutputDTO> dtoList) {
		return dtoList.stream()
				.limit(MAX_LOG_COUNT)
				.map(dto -> buildCsvDetailContextCsvId(dto) + " existFlg=" + dto.isExistFlg())
				.collect(Collectors.joining(", ", "[", "]"));
	}

	private static String buildCsvDetailManageSummaryForLog(List<CsvDetailManageEntity> entityList) {
		return entityList.stream()
				.limit(MAX_LOG_COUNT)
				.map(e -> String.format("{csvId=%s, dataCategory=%s, season=%s, home=%s, away=%s, checkFinFlg=%s}",
						safe(e.getCsvId()).trim(),
						safe(e.getDataCategory()).trim(),
						safe(e.getSeason()).trim(),
						safe(e.getHomeTeamName()).trim(),
						safe(e.getAwayTeamName()).trim(),
						safe(e.getCheckFinFlg()).trim()))
				.collect(Collectors.joining(", ", "[", "]"));
	}

	private static String safe(String s) {
		return (s == null) ? "" : s;
	}

	// ===== 内部型 =====

	/** 実行する Stat 1件（ログ名・手動データでも実行するか・Stat） */
	private static final class StatStep {
		private final String name;
		private final boolean manualTarget;
		private final AnalyzeEntityIF stat;

		private StatStep(String name, boolean manualTarget, AnalyzeEntityIF stat) {
			this.name = name;
			this.manualTarget = manualTarget;
			this.stat = stat;
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
}