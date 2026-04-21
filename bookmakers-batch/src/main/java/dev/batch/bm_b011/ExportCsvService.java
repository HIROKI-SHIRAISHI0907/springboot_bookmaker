package dev.batch.bm_b011;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import dev.batch.repository.bm.BookCsvDataRepository;
import dev.batch.repository.bm.BookCsvDetailManageRepository;
import dev.batch.repository.master.CountryLeagueSeasonMasterBatchRepository;
import dev.common.config.PathConfig;
import dev.common.constant.BookMakersCommonConst;
import dev.common.constant.MessageCdConst;
import dev.common.entity.CsvDetailManageEntity;
import dev.common.entity.DataEntity;
import dev.common.exception.wrap.RootCauseWrapper;
import dev.common.filemng.FileMngWrapper;
import dev.common.getinfo.GetStatInfo;
import dev.common.logger.ManageLoggerComponent;
import dev.common.s3.S3Operator;
import dev.common.util.ExecuteMainUtil;

/**
 * StatデータCSV出力ロジック（S3 ラウンド別フォルダアップロード版）
 *
 * 流れ（S3モード）：
 * 1) S3（本番prefix）から seqList.txt / data_team_list.txt をローカルへDL
 * 2) DBからグルーピング作成 → 差分計画（recreate/new）作成
 * 3) 対象データをDBから取得し、フィルタ、補完
 * 4) CSV生成（並列）→ ローカル書き出し → S3へPUT
 * 5) data_team_list.txt 更新 → S3へPUT
 * 6) seqList.txt 更新 → S3へPUT
 *
 * CSV保存先：
 * s3バケット/<国>-<リーグ>-ラウンド<番号>/X.csv
 *
 * 同一フォルダに既存CSVがある場合は、その最大番号+1から採番する。
 *
 * localOnly=true のとき：
 * - S3操作を一切行わず、ローカルのみで完結
 */
@Component
public class ExportCsvService {

	private static final String PROJECT_NAME = ExportCsvService.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	private static final String CLASS_NAME = ExportCsvService.class.getName();

	private static final String CSV_NEW_PREFIX = "mk";

	private static final com.fasterxml.jackson.databind.ObjectMapper SEQ_JSON =
			new com.fasterxml.jackson.databind.ObjectMapper();

	private static final Pattern ROUND_TOKEN = Pattern.compile("ラウンド\\s*[0-9０-９]+");

	private static final Pattern ROUND_NO_PATTERN = Pattern.compile("ラウンド\\s*([0-9０-９]+)");

	// "6089.csv" だけでなく "prefix/6089.csv" も解析できるようにしておく
	private static final Pattern CSV_NO_PATTERN =
			Pattern.compile("(^|.*/)(\\d+)\\.csv$", Pattern.CASE_INSENSITIVE);

	@Value("${exportcsv.local-only:false}")
	private boolean localOnly;

	/**
	 * S3の本番配置プレフィックス（空文字ならバケット直下）
	 * 例: "" / "stats" / "it/20260309-001"
	 */
	@Value("${exportcsv.final-prefix:}")
	private String finalPrefix;

	/**
	 * 「追加が無い回でも管理ファイルをPUTする」(true推奨)
	 */
	@Value("${exportcsv.always-put-manage-files:true}")
	private boolean alwaysPutManageFiles;

	@Autowired
	private S3Operator s3Operator;

	@Autowired
	private GetStatInfo getStatInfo;

	@Autowired
	private PathConfig config;

	@Autowired
	private ReaderCurrentCsvInfoBean bean;

	@Autowired
	private CsvArtifactHelper helper;

	@Autowired
	private BookCsvDataRepository bookCsvDataRepository;

	@Autowired
	private BookCsvDetailManageRepository csvDetailManageRepository;

	@Autowired
	private CountryLeagueSeasonMasterBatchRepository countryLeagueSeasonMasterBatchRepository;

	/** ログ管理ラッパー */
	@Autowired
	private RootCauseWrapper rootCauseWrapper;

	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	public void execute() throws IOException {
		final String METHOD_NAME = "execute";
		this.manageLoggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, "start");

		Path outDir = Paths.get(config.getCsvFolder()).toAbsolutePath().normalize();
		Files.createDirectories(outDir);

		if (localOnly) {
			executeLocalOnly(outDir);
			return;
		}

		final String statsBucket = config.getS3BucketsStats();
		final String prefix = normalizePrefix(finalPrefix);

		final String seqFileName = "seqList.txt";
		final String teamFileName = "data_team_list.txt";

		final String seqKeyFinal = normalizeS3Key(joinS3Key(prefix, seqFileName));
		final String teamKeyFinal = normalizeS3Key(joinS3Key(prefix, teamFileName));

		final Path LOCAL_DIR = outDir;
		ensureDir(LOCAL_DIR);

		final Path localSeqPath = LOCAL_DIR.resolve(seqFileName);
		final Path localTeamPath = LOCAL_DIR.resolve(teamFileName);

		CsvArtifactResource csvArtifactResource;
		try {
			csvArtifactResource = this.helper.getData();
		} catch (Exception e) {
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION, e);
			throw e;
		}

		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG,
				"bucket=" + statsBucket
						+ ", finalPrefix=" + prefix
						+ ", localDir=" + LOCAL_DIR
						+ ", seqKeyFinal=" + seqKeyFinal
						+ ", teamKeyFinal=" + teamKeyFinal);

		boolean seqExists = downloadIfExists(statsBucket, seqKeyFinal, localSeqPath, "seqList.txt download");
		downloadIfExists(statsBucket, teamKeyFinal, localTeamPath, "data_team_list.txt download");

		List<List<Integer>> currentGroups = sortSeqs();
		currentGroups = normalizeGroups(currentGroups);

		boolean firstRun = !seqExists || !Files.exists(localSeqPath);
		List<List<Integer>> textGroups;

		if (firstRun) {
			writeSeqListJson(localSeqPath, currentGroups);
			textGroups = Collections.emptyList();
		} else {
			textGroups = readSeqListJson(localSeqPath);
			textGroups = normalizeGroups(textGroups);
		}

		// ====== 対象グループを事前読込し、対象フォルダだけ既存CSV情報を読む ======
		Map<String, List<DataEntity>> groupResultMap =
				preloadGroupResults(currentGroups, csvArtifactResource, METHOD_NAME);

		Set<String> targetFolders = collectTargetFolders(groupResultMap);

		// S3全件走査ではなく、対象フォルダだけ既存CSV情報を読み込む
		bean.init(targetFolders);

		Map<String, List<Integer>> csvInfoRow = bean.getCsvInfo();
		csvInfoRow = (csvInfoRow != null) ? csvInfoRow : Collections.emptyMap();

		CsvBuildPlan plan;
		if (firstRun) {
			CsvBuildPlan plans = new CsvBuildPlan();
			int i = 0;
			for (List<Integer> curr : currentGroups) {
				plans.newTargets.put(CSV_NEW_PREFIX + "-" + (i++), curr);
			}
			plan = plans;
		} else {
			plan = matchSeqCombPlan(textGroups, currentGroups, csvInfoRow);
		}

		if (plan == null || (plan.recreateByCsvKey.isEmpty() && plan.newTargets.isEmpty())) {

			if (alwaysPutManageFiles) {
				putManageFilesEvenIfNoCsv(statsBucket, prefix, LOCAL_DIR, localSeqPath, localTeamPath, currentGroups);
			}

			String messageCd = MessageCdConst.MCD00014I_NO_MAP_DATA;
			String fillChar = "追加レコードがないため処理終了 (既存CSV数: " + csvInfoRow.size() + "件)";
			endLog(METHOD_NAME, messageCd, fillChar);
			return;
		}

		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG,
				"csvInfoRow.size=" + csvInfoRow.size()
						+ ", plan.recreate=" + plan.recreateByCsvKey.size()
						+ ", plan.newTargets=" + plan.newTargets.size()
						+ ", firstRun=" + firstRun);

		List<SimpleEntry<String, List<DataEntity>>> recreateCandidates = new ArrayList<>();
		List<List<DataEntity>> newCandidates = new ArrayList<>();

		// recreate
		for (Map.Entry<String, List<Integer>> entry : plan.recreateByCsvKey.entrySet()) {
			String relativeKey = entry.getKey();
			if (relativeKey == null || relativeKey.isBlank()) {
				continue;
			}

			List<Integer> ids = normalizeSeqList(entry.getValue());
			if (ids.isEmpty()) {
				continue;
			}

			String gk = groupKey(ids);
			List<DataEntity> result = groupResultMap.get(gk);
			if (result == null || result.isEmpty()) {
				continue;
			}

			if (matchCsvInfo(csvInfoRow, result)) {
				continue;
			}

			String path = LOCAL_DIR.resolve(relativeKey).toString();
			recreateCandidates.add(new SimpleEntry<>(path, result));
		}

		// new
		for (Map.Entry<String, List<Integer>> entry : plan.newTargets.entrySet()) {
			List<Integer> ids = normalizeSeqList(entry.getValue());
			if (ids.isEmpty()) {
				continue;
			}

			String gk = groupKey(ids);
			List<DataEntity> result = groupResultMap.get(gk);
			if (result == null || result.isEmpty()) {
				continue;
			}

			if (matchCsvInfo(csvInfoRow, result)) {
				continue;
			}
			newCandidates.add(result);
		}

		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG,
				"candidates: recreate=" + recreateCandidates.size() + ", new=" + newCandidates.size());

		if (recreateCandidates.isEmpty() && newCandidates.isEmpty()) {

			if (alwaysPutManageFiles) {
				putManageFilesEvenIfNoCsv(statsBucket, prefix, LOCAL_DIR, localSeqPath, localTeamPath, currentGroups);
			}

			String messageCd = MessageCdConst.MCD00014I_NO_MAP_DATA;
			String fillChar = "追加レコードがないため処理終了 (既存CSV数: " + csvInfoRow.size() + "件)";
			endLog(METHOD_NAME, messageCd, fillChar);
			return;
		}

		// new をフォルダ単位に振り分け
		Map<String, List<List<DataEntity>>> newCandidatesByFolder = new LinkedHashMap<>();
		for (List<DataEntity> result : newCandidates) {
			String folderName = resolveRoundFolderName(result);
			newCandidatesByFolder.computeIfAbsent(folderName, k -> new ArrayList<>()).add(result);
		}

		List<SimpleEntry<String, List<DataEntity>>> toCreate = new ArrayList<>();

		recreateCandidates.sort(Comparator.comparing(e -> toRelativeCsvKey(LOCAL_DIR, e.getKey())));
		toCreate.addAll(recreateCandidates);

		Set<String> newTargetFolders = new LinkedHashSet<>(newCandidatesByFolder.keySet());
		Map<String, Integer> s3MaxByFolder = getStatInfo.getMaxCsvNoByFolders(newTargetFolders);
		for (Map.Entry<String, List<List<DataEntity>>> e : newCandidatesByFolder.entrySet()) {
			String folderName = e.getKey();
			List<List<DataEntity>> groups = e.getValue();

			groups.sort(Comparator.comparingInt(ExportCsvService::minSeqOfResult));

			int maxOnS3 = s3MaxByFolder.getOrDefault(folderName, 0);
			int maxLocal = getMaxCsvNoFromLocal(LOCAL_DIR.resolve(folderName));
			int nextNo = Math.max(maxOnS3, maxLocal) + 1;

			for (int i = 0; i < groups.size(); i++) {
				int csvNo = nextNo + i;
				String relativeKey = joinS3Key(folderName, csvNo + BookMakersCommonConst.CSV);
				String path = LOCAL_DIR.resolve(relativeKey).toString();
				toCreate.add(new SimpleEntry<>(path, groups.get(i)));
			}
		}

		int threads = Math.max(2, Runtime.getRuntime().availableProcessors() - 1);
		ExecutorService pool = Executors.newFixedThreadPool(threads);

		List<CompletableFuture<CsvArtifact>> futures = new ArrayList<>(toCreate.size());
		for (SimpleEntry<String, List<DataEntity>> e : toCreate) {
			final String path = e.getKey();
			final List<DataEntity> group = e.getValue();
			futures.add(CompletableFuture.supplyAsync(
					() -> buildCsvArtifact(path, group, csvArtifactResource), pool));
		}

		int success = 0, failed = 0;
		List<SimpleEntry<String, List<DataEntity>>> succeeded = new ArrayList<>();
		List<SimpleEntry<String, List<DataEntity>>> failedEntries = new ArrayList<>();

		for (int i = 0; i < futures.size(); i++) {
			try {
				CsvArtifact art = futures.get(i).join();
				if (art == null || art.getContent() == null || art.getContent().isEmpty()) {
					continue;
				}

				writeLocalCsv(art);
				putLocalFileToFinal(statsBucket, prefix, LOCAL_DIR, Paths.get(art.getFilePath()));

				success++;
				succeeded.add(toCreate.get(i));
			} catch (Exception ex) {
				failed++;
				failedEntries.add(toCreate.get(i));
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION, ex,
						"CSV作成/PUT(final) 失敗");
			}
		}

		pool.shutdown();
		try {
			pool.awaitTermination(1, TimeUnit.MINUTES);
		} catch (InterruptedException ignore) {
			Thread.currentThread().interrupt();
		}

		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG,
				"CSVアップロード結果(final put) (成功: " + success + "件, 失敗: " + failed + "件)");

		registerCsvDetailManage(LOCAL_DIR, succeeded, METHOD_NAME);

		try {
			upsertDataTeamList(LOCAL_DIR, localTeamPath, succeeded, failedEntries);
			putLocalFileToFinal(statsBucket, prefix, LOCAL_DIR, localTeamPath);
		} catch (Exception e) {
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION, e,
					"data_team_list.txt 更新/PUT(final) 失敗");
			throw (e instanceof IOException) ? (IOException) e : new IOException(e);
		}

		try {
			writeSeqListJson(localSeqPath, currentGroups);
			putLocalFileToFinal(statsBucket, prefix, LOCAL_DIR, localSeqPath);
		} catch (Exception e) {
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION, e,
					"seqList.txt 更新/PUT(final) 失敗");
			throw (e instanceof IOException) ? (IOException) e : new IOException(e);
		}

		if (failed > 0) {
			this.manageLoggerComponent.debugWarnLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00099I_LOG,
					"失敗があるため一部のみアップロードされている可能性があります");
		}

		endLog(METHOD_NAME, null, null);
	}

	// =========================================================
	// localOnly
	// =========================================================
	private void executeLocalOnly(Path outDir) throws IOException {
		final String METHOD_NAME = "executeLocalOnly";

		final Path LOCAL_DIR = outDir;
		ensureDir(LOCAL_DIR);

		final Path localSeqPath = LOCAL_DIR.resolve("seqList.txt");
		final Path localTeamPath = LOCAL_DIR.resolve("data_team_list.txt");

		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG,
				"localOnly=true: S3処理を完全にスキップします。localDir=" + LOCAL_DIR);

		Map<String, List<Integer>> csvInfoRow;
		try {
			bean.init();
			csvInfoRow = (bean != null ? bean.getCsvInfo() : null);
		} catch (Exception e) {
			csvInfoRow = Collections.emptyMap();
			this.manageLoggerComponent.debugWarnLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00099I_LOG,
					"localOnly: ReaderCurrentCsvInfoBean.init() をスキップ（外部参照の可能性）");
		}
		csvInfoRow = (csvInfoRow != null) ? csvInfoRow : Collections.emptyMap();

		List<List<Integer>> currentGroups = sortSeqs();
		currentGroups = normalizeGroups(currentGroups);

		boolean firstRun = !Files.exists(localSeqPath);
		List<List<Integer>> textGroups;

		if (firstRun) {
			writeSeqListJson(localSeqPath, currentGroups);
			textGroups = Collections.emptyList();
		} else {
			textGroups = readSeqListJson(localSeqPath);
			textGroups = normalizeGroups(textGroups);
		}

		CsvBuildPlan plan;
		if (firstRun) {
			CsvBuildPlan plans = new CsvBuildPlan();
			int i = 0;
			for (List<Integer> curr : currentGroups) {
				plans.newTargets.put(CSV_NEW_PREFIX + "-" + (i++), curr);
			}
			plan = plans;
		} else {
			plan = matchSeqCombPlan(textGroups, currentGroups, csvInfoRow);
		}

		if (plan == null || (plan.recreateByCsvKey.isEmpty() && plan.newTargets.isEmpty())) {
			upsertDataTeamList(LOCAL_DIR, localTeamPath, Collections.emptyList(), Collections.emptyList());
			writeSeqListJson(localSeqPath, currentGroups);

			String messageCd = MessageCdConst.MCD00014I_NO_MAP_DATA;
			String fillChar = "追加レコードがないため処理終了 (既存CSV数: " + csvInfoRow.size() + "件)";
			endLog(METHOD_NAME, messageCd, fillChar);
			return;
		}

		CsvArtifactResource csvArtifactResource;
		try {
			csvArtifactResource = this.helper.getData();
		} catch (Exception e) {
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION, e);
			throw (e instanceof IOException) ? (IOException) e : new IOException(e);
		}

		Map<String, List<DataEntity>> groupResultMap =
				preloadGroupResults(currentGroups, csvArtifactResource, METHOD_NAME);

		List<SimpleEntry<String, List<DataEntity>>> recreateCandidates = new ArrayList<>();
		List<List<DataEntity>> newCandidates = new ArrayList<>();

		for (Map.Entry<String, List<Integer>> rt : plan.recreateByCsvKey.entrySet()) {
			String relativeKey = rt.getKey();
			if (relativeKey == null || relativeKey.isBlank()) {
				continue;
			}

			List<Integer> ids = normalizeSeqList(rt.getValue());
			String gk = groupKey(ids);

			List<DataEntity> result = groupResultMap.get(gk);
			if (result == null || result.isEmpty()) {
				continue;
			}

			if (matchCsvInfo(csvInfoRow, result)) {
				continue;
			}

			String path = LOCAL_DIR.resolve(relativeKey).toString();
			recreateCandidates.add(new SimpleEntry<>(path, result));
		}

		for (Map.Entry<String, List<Integer>> entry : plan.newTargets.entrySet()) {
			List<Integer> ids = normalizeSeqList(entry.getValue());
			if (ids.isEmpty()) {
				continue;
			}

			String gk = groupKey(ids);
			List<DataEntity> result = groupResultMap.get(gk);
			if (result == null || result.isEmpty()) {
				continue;
			}

			if (matchCsvInfo(csvInfoRow, result)) {
				continue;
			}
			newCandidates.add(result);
		}

		if (recreateCandidates.isEmpty() && newCandidates.isEmpty()) {
			upsertDataTeamList(LOCAL_DIR, localTeamPath, Collections.emptyList(), Collections.emptyList());
			writeSeqListJson(localSeqPath, currentGroups);

			String messageCd = MessageCdConst.MCD00014I_NO_MAP_DATA;
			String fillChar = "追加レコードがないため処理終了 (既存CSV数: " + csvInfoRow.size() + "件)";
			endLog(METHOD_NAME, messageCd, fillChar);
			return;
		}

		Map<String, List<List<DataEntity>>> newCandidatesByFolder = new LinkedHashMap<>();
		for (List<DataEntity> result : newCandidates) {
			String folderName = resolveRoundFolderName(result);
			newCandidatesByFolder.computeIfAbsent(folderName, k -> new ArrayList<>()).add(result);
		}

		List<SimpleEntry<String, List<DataEntity>>> toCreate = new ArrayList<>();

		recreateCandidates.sort(Comparator.comparing(e -> toRelativeCsvKey(LOCAL_DIR, e.getKey())));
		toCreate.addAll(recreateCandidates);

		for (Map.Entry<String, List<List<DataEntity>>> e : newCandidatesByFolder.entrySet()) {
			String folderName = e.getKey();
			List<List<DataEntity>> groups = e.getValue();

			groups.sort(Comparator.comparingInt(ExportCsvService::minSeqOfResult));

			int maxLocal = getMaxCsvNoFromLocal(LOCAL_DIR.resolve(folderName));
			int nextNo = maxLocal + 1;

			for (int i = 0; i < groups.size(); i++) {
				int csvNo = nextNo + i;
				String relativeKey = joinS3Key(folderName, csvNo + BookMakersCommonConst.CSV);
				String path = LOCAL_DIR.resolve(relativeKey).toString();
				toCreate.add(new SimpleEntry<>(path, groups.get(i)));
			}
		}

		int threads = Math.max(2, Runtime.getRuntime().availableProcessors() - 1);
		ExecutorService pool = Executors.newFixedThreadPool(threads);

		List<CompletableFuture<CsvArtifact>> futures = new ArrayList<>(toCreate.size());
		for (SimpleEntry<String, List<DataEntity>> e : toCreate) {
			final String path = e.getKey();
			final List<DataEntity> group = e.getValue();
			futures.add(CompletableFuture.supplyAsync(
					() -> buildCsvArtifact(path, group, csvArtifactResource), pool));
		}

		int success = 0, failed = 0;
		List<SimpleEntry<String, List<DataEntity>>> succeeded = new ArrayList<>();
		List<SimpleEntry<String, List<DataEntity>>> failedEntries = new ArrayList<>();

		for (int i = 0; i < futures.size(); i++) {
			try {
				CsvArtifact art = futures.get(i).join();
				if (art == null || art.getContent() == null || art.getContent().isEmpty()) {
					continue;
				}
				writeLocalCsv(art);

				success++;
				succeeded.add(toCreate.get(i));
			} catch (Exception ex) {
				failed++;
				failedEntries.add(toCreate.get(i));
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION, ex,
						"CSV作成(ローカル) 失敗");
			}
		}

		pool.shutdown();
		try {
			pool.awaitTermination(1, TimeUnit.MINUTES);
		} catch (InterruptedException ignore) {
			Thread.currentThread().interrupt();
		}

		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG,
				"localOnly: CSV生成結果 (成功: " + success + "件, 失敗: " + failed + "件)");

		registerCsvDetailManage(LOCAL_DIR, succeeded, METHOD_NAME);

		upsertDataTeamList(LOCAL_DIR, localTeamPath, succeeded, failedEntries);
		writeSeqListJson(localSeqPath, currentGroups);

		if (failed > 0) {
			this.manageLoggerComponent.debugWarnLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00099I_LOG,
					"localOnly: 失敗があるため一部未生成の可能性があります。localDir=" + LOCAL_DIR);
		}

		endLog(METHOD_NAME, null, null);
	}

	// =========================================================
	// csv_detail_manage 更新
	// =========================================================
	private void registerCsvDetailManage(
			Path baseDir,
			List<SimpleEntry<String, List<DataEntity>>> succeeded,
			String parentMethod) {

		final String METHOD_NAME = "registerCsvDetailManage";

		if (succeeded == null || succeeded.isEmpty()) {
			return;
		}

		for (SimpleEntry<String, List<DataEntity>> e : succeeded) {
			if (e == null) {
				continue;
			}

			String csvId = toRelativeCsvKey(baseDir, e.getKey());
			List<DataEntity> list = e.getValue();

			if (csvId == null || csvId.isBlank() || list == null || list.isEmpty()) {
				continue;
			}

			DataEntity row = findRowWithTeams(list);
			if (row == null) {
				continue;
			}

			String dataCategory = safe(row.getDataCategory()).trim();
			String home = safe(row.getHomeTeamName()).trim();
			String away = safe(row.getAwayTeamName()).trim();

			String season = resolveSeasonSafely(csvId, dataCategory);

			try {
				upsertCsvDetailManage(csvId, dataCategory, season, home, away, parentMethod);
			} catch (Exception ex) {
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION, ex,
						"csv_detail_manage 更新失敗: " + buildCsvDetailContext(dataCategory, season, home, away));

				throw ex;
			}
		}
	}

	private String resolveSeasonSafely(String csvId, String dataCategory) {
		final String METHOD_NAME = "resolveSeasonSafely";

		String country = "";
		String league = "";

		try {
			List<String> dataList = ExecuteMainUtil.getCountryLeagueByRegex(dataCategory);
			if (dataList != null && dataList.size() >= 2) {
				country = safe(dataList.get(0)).trim();
				league = safe(dataList.get(1)).trim();
			}
		} catch (Exception e) {
			this.manageLoggerComponent.debugWarnLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00099I_LOG,
					"dataCategory から country/league 抽出失敗. dataCategory=" + dataCategory + ", csvId=" + csvId);
		}

		// dataCategory から取れなかった場合は csvId から復元
		if (country.isEmpty() || league.isEmpty()) {
			String[] pair = extractCountryLeagueFromCsvId(csvId);
			country = safe(pair[0]).trim();
			league = safe(pair[1]).trim();

			this.manageLoggerComponent.debugWarnLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00099I_LOG,
					"csvId から country/league をフォールバック抽出. country=" + country
							+ ", league=" + league + ", csvId=" + csvId);
		}

		if (country.isEmpty() || league.isEmpty()) {
			this.manageLoggerComponent.debugWarnLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00099I_LOG,
					"season取得スキップ: country/league を特定できません. dataCategory=" + dataCategory + ", csvId=" + csvId);
			return "";
		}

		try {
			String season = countryLeagueSeasonMasterBatchRepository.findSeasonYear(country, league);
			return safe(season).trim();
		} catch (Exception e) {
			this.manageLoggerComponent.debugWarnLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00099I_LOG,
					"season取得失敗. country=" + country + ", league=" + league + ", csvId=" + csvId);
			return "";
		}
	}

	/**
	 * CSV詳細管理
	 */
	private void upsertCsvDetailManage(
			String csvId,
			String dataCategory,
			String season,
			String home,
			String away,
			String parentMethod) {

		final String METHOD_NAME = "upsertCsvDetailManage";

		CsvDetailManageEntity entity = new CsvDetailManageEntity();
		entity.setCsvId(csvId);
		entity.setDataCategory(dataCategory);
		entity.setSeason(season);
		entity.setHomeTeamName(home);
		entity.setAwayTeamName(away);
		entity.setCheckFinFlg("0");

		String context = buildCsvDetailContext(dataCategory, season, home, away);

		CsvDetailManageEntity selectEntity = this.csvDetailManageRepository.select(entity);
		if (selectEntity != null) {
			int result = this.csvDetailManageRepository.update(selectEntity.getCsvId(), "1");
			if (result != 1) {
				String messageCd = MessageCdConst.MCD00008E_UPDATE_FAILED;
				this.rootCauseWrapper.throwUnexpectedRowCount(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						messageCd,
						1, result,
						context);
			}

			String messageCd = MessageCdConst.MCD00006I_UPDATE_SUCCESS;
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
					"csv_detail_manage 更新件数: " + result + "件 (" + context + ", csvId=" + csvId + ")");
		} else {
			int result = this.csvDetailManageRepository.insert(entity);
			if (result != 1) {
				String messageCd = MessageCdConst.MCD00007E_INSERT_FAILED;
				this.rootCauseWrapper.throwUnexpectedRowCount(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						messageCd,
						1, result,
						context);
			}

			String messageCd = MessageCdConst.MCD00005I_INSERT_SUCCESS;
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
					"csv_detail_manage 登録件数: " + result + "件 (" + context + ", csvId=" + csvId + ")");
		}
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

	private String[] extractCountryLeagueFromCsvId(String csvId) {
		if (csvId == null || csvId.isBlank()) {
			return new String[] { "", "" };
		}

		String folder = parentPath(csvId); // 例: ブルガリア-パルヴァ・リーガ-ラウンド27
		if (folder.isBlank()) {
			return new String[] { "", "" };
		}

		int roundIdx = folder.lastIndexOf("-ラウンド");
		String base = (roundIdx >= 0) ? folder.substring(0, roundIdx) : folder;

		int firstHyphen = base.indexOf('-');
		if (firstHyphen < 0) {
			return new String[] { "", "" };
		}

		String country = base.substring(0, firstHyphen).trim();
		String league = base.substring(firstHyphen + 1).trim();

		return new String[] { country, league };
	}

	// =========================================================
	// seqList.txt JSON形式読み書き
	// =========================================================
	private void writeSeqListJson(Path out, List<List<Integer>> groups) throws IOException {
		String json = SEQ_JSON.writeValueAsString(groups);
		Files.writeString(out, json, StandardCharsets.UTF_8,
				StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
	}

	private List<List<Integer>> readSeqListJson(Path path) {
		if (!Files.exists(path)) {
			return Collections.emptyList();
		}
		try {
			String json = Files.readString(path, StandardCharsets.UTF_8).trim();
			if (json.isEmpty()) {
				return Collections.emptyList();
			}

			if (json.startsWith("[")) {
				return SEQ_JSON.readValue(json,
						new com.fasterxml.jackson.core.type.TypeReference<List<List<Integer>>>() {
						});
			}

			List<List<Integer>> result = new ArrayList<>();
			for (String line : json.split("\n")) {
				line = line.trim();
				if (line.isEmpty()) {
					continue;
				}
				List<Integer> group = new ArrayList<>();
				for (String s : line.split(",")) {
					s = s.trim();
					if (!s.isEmpty()) {
						try {
							group.add(Integer.valueOf(s));
						} catch (NumberFormatException ignore) {
						}
					}
				}
				if (!group.isEmpty()) {
					result.add(group);
				}
			}
			return result;

		} catch (Exception e) {
			this.manageLoggerComponent.debugWarnLog(
					PROJECT_NAME, CLASS_NAME, "readSeqListJson",
					MessageCdConst.MCD00099I_LOG,
					"seqList.txt の読み込みに失敗しました: " + path);
			return Collections.emptyList();
		}
	}

	// =========================================================
	// S3 upload
	// =========================================================
	private String putLocalFileToFinal(String bucket, String finalPrefix, Path baseDir, Path localFile) {
		final String METHOD_NAME = "putLocalFileToFinal";

		String relativeKey = baseDir.toAbsolutePath().normalize()
				.relativize(localFile.toAbsolutePath().normalize())
				.toString()
				.replace('\\', '/');

		String finalKey = normalizeS3Key(joinS3Key(finalPrefix, relativeKey));

		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME,
				MessageCdConst.MCD00099I_LOG,
				"UPLOAD(final) bucket=" + bucket + " key=" + finalKey + " localFile=" + localFile);

		try {
			s3Operator.uploadFile(bucket, finalKey, localFile);
			return finalKey;
		} catch (Exception e) {
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00023E_S3_UPLOAD_FAILED, e,
					bucket, finalKey);
			throw e;
		}
	}

	private int getMaxCsvNoFromLocal(Path localDir) {
		final String METHOD_NAME = "getMaxCsvNoFromLocal";
		int max = 0;

		try {
			if (localDir == null || !Files.isDirectory(localDir)) {
				return 0;
			}

			try (var stream = Files.list(localDir)) {
				for (Path p : stream.collect(Collectors.toList())) {
					if (p == null) {
						continue;
					}
					String name = p.getFileName().toString();
					Matcher m = CSV_NO_PATTERN.matcher(name);
					if (!m.find()) {
						continue;
					}

					try {
						int n = Integer.parseInt(m.group(2));
						if (n > max) {
							max = n;
						}
					} catch (NumberFormatException ignore) {
					}
				}
			}
		} catch (Exception e) {
			this.manageLoggerComponent.debugWarnLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00099I_LOG,
					"ローカル最大CSV番号の取得に失敗 localDir=" + localDir);
			return 0;
		}

		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG,
				"ローカル最大CSV番号=" + max + " (dir=" + localDir + ")");
		return max;
	}

	private boolean downloadIfExists(String bucket, String key, Path out, String label) {
		final String METHOD_NAME = "downloadIfExists";
		try {
			s3Operator.downloadToFile(bucket, key, out);
			return true;
		} catch (Exception e) {
			this.manageLoggerComponent.debugWarnLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00099I_LOG,
					label + " failed or not found. bucket=" + bucket + ", key=" + key);
			return false;
		}
	}

	// =========================================================
	// 既存ロジック
	// =========================================================
	private List<List<Integer>> sortSeqs() {
		List<SeqWithKey> rows = this.bookCsvDataRepository.findAllSeqsWithKey();
		List<List<Integer>> result = new ArrayList<>();
		List<Integer> bucket = new ArrayList<>();

		String prevHome = null, prevAway = null;

		for (SeqWithKey r : rows) {
			boolean newGroup = prevHome == null
					|| !Objects.equals(prevHome, r.getHomeTeamName())
					|| !Objects.equals(prevAway, r.getAwayTeamName());

			if (newGroup) {
				if (!bucket.isEmpty()) {
					bucket.sort(Comparator.naturalOrder());
					result.add(bucket);
				}
				bucket = new ArrayList<>();
				prevHome = r.getHomeTeamName();
				prevAway = r.getAwayTeamName();
			}

			if (r.getSeq() != null) {
				bucket.add(r.getSeq());
			}
		}

		if (!bucket.isEmpty()) {
			bucket.sort(Comparator.naturalOrder());
			result.add(bucket);
		}
		return result;
	}

	private static List<List<Integer>> normalizeGroups(List<List<Integer>> groups) {
		if (groups == null) {
			return Collections.emptyList();
		}
		List<List<Integer>> out = new ArrayList<>();
		for (List<Integer> g : groups) {
			List<Integer> ng = normalizeSeqListStatic(g);
			if (!ng.isEmpty()) {
				out.add(ng);
			}
		}
		return out;
	}

	private List<Integer> normalizeSeqList(List<Integer> src) {
		return normalizeSeqListStatic(src);
	}

	private static List<Integer> normalizeSeqListStatic(List<Integer> src) {
		if (src == null || src.isEmpty()) {
			return Collections.emptyList();
		}
		List<Integer> ids = new ArrayList<>(src.size());
		for (Integer n : new TreeSet<>(src)) {
			if (n != null) {
				ids.add(n);
			}
		}
		return ids;
	}

	private List<DataEntity> fetchAndFilter(
			List<Integer> ids,
			CsvArtifactResource csvArtifactResource,
			String parentMethod,
			String label) {

		if (ids == null || ids.isEmpty()) {
			return null;
		}

		List<DataEntity> result;
		try {
			result = this.bookCsvDataRepository.findByData(ids);
		} catch (Exception e) {
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, parentMethod,
					MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION, e,
					label);
			throw e;
		}

		if (!this.helper.csvCondition(result, csvArtifactResource)) {
			return null;
		}

		result = this.helper.abnormalChk(result);
		if (result == null || result.isEmpty()) {
			return null;
		}

		result = new ArrayList<>(result);

		backfillScores(result);
		applyCanonicalCategory(result);

		return result;
	}

	private CsvArtifact buildCsvArtifact(String path, List<DataEntity> result, CsvArtifactResource resource) {
		if (result == null || result.isEmpty()) {
			return null;
		}
		return new CsvArtifact(path, result);
	}

	private void writeLocalCsv(CsvArtifact art) {
		try {
			Path path = Paths.get(art.getFilePath());
			if (path.getParent() != null) {
				Files.createDirectories(path.getParent());
			}
			Files.deleteIfExists(path);
		} catch (IOException e) {
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, "writeLocalCsv",
					MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION, e,
					"削除エラー");
		}
		FileMngWrapper fw = new FileMngWrapper();
		fw.csvWrite(art.getFilePath(), art.getContent());
	}

	private static void ensureDir(Path dir) throws IOException {
		Files.createDirectories(dir);
	}

	private boolean matchCsvInfo(Map<String, List<Integer>> csvInfoRow, List<DataEntity> resource) {
		if (csvInfoRow == null || csvInfoRow.isEmpty() || resource == null || resource.isEmpty()) {
			return false;
		}
		StringBuilder resourceBuilder = new StringBuilder();
		for (DataEntity d : resource) {
			if (resourceBuilder.length() > 0) {
				resourceBuilder.append("-");
			}
			resourceBuilder.append(d.getSeq());
		}
		for (Map.Entry<String, List<Integer>> list : csvInfoRow.entrySet()) {
			List<Integer> vals = list.getValue();
			if (vals == null || vals.isEmpty()) {
				continue;
			}

			StringBuilder sBuilder = new StringBuilder();
			for (Integer d : vals) {
				if (sBuilder.length() > 0) {
					sBuilder.append("-");
				}
				sBuilder.append(d);
			}
			if (resourceBuilder.toString().equals(sBuilder.toString())) {
				return true;
			}
		}
		return false;
	}

	private CsvBuildPlan matchSeqCombPlan(
			List<List<Integer>> textSeqs,
			List<List<Integer>> dbSeqs,
			Map<String, List<Integer>> csvInfoRow) {

		CsvBuildPlan plan = new CsvBuildPlan();

		Map<Integer, String> minSeqToCsvKey = new LinkedHashMap<>();
		Map<String, String> groupKeyToCsvKey = new LinkedHashMap<>();

		for (Map.Entry<String, List<Integer>> e : csvInfoRow.entrySet()) {
			String csvKey = e.getKey();
			List<Integer> ids = normalizeSeqListStatic(e.getValue());
			if (ids.isEmpty()) {
				continue;
			}

			int min = ids.get(0);
			minSeqToCsvKey.put(min, csvKey);
			groupKeyToCsvKey.put(groupKey(ids), csvKey);
		}

		for (List<Integer> dbGroup : dbSeqs) {
			if (dbGroup == null || dbGroup.isEmpty()) {
				continue;
			}

			String gk = groupKey(dbGroup);

			if (groupKeyToCsvKey.containsKey(gk)) {
				continue;
			}

			int min = dbGroup.get(0);
			String csvKey = minSeqToCsvKey.get(min);
			if (csvKey != null) {
				plan.recreateByCsvKey.put(csvKey, dbGroup);
			} else {
				plan.newTargets.put(CSV_NEW_PREFIX + "-" + min, dbGroup);
			}
		}

		return plan;
	}

	private static String groupKey(List<Integer> ids) {
		StringBuilder sb = new StringBuilder();
		for (Integer n : ids) {
			if (n == null) {
				continue;
			}
			if (sb.length() > 0) {
				sb.append('-');
			}
			sb.append(n);
		}
		return sb.toString();
	}

	private static Integer parseCsvNo(String keyOrName) {
		if (keyOrName == null) {
			return null;
		}
		Matcher m = CSV_NO_PATTERN.matcher(keyOrName);
		if (!m.find()) {
			return null;
		}
		try {
			return Integer.valueOf(m.group(2));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	// =========================================================
	// data_team_list.txt 更新
	// =========================================================
	private void upsertDataTeamList(
			Path baseDir,
			Path out,
			List<SimpleEntry<String, List<DataEntity>>> succeeded,
			List<SimpleEntry<String, List<DataEntity>>> failed) throws IOException {

		Map<String, String> csvKeyToLine = new LinkedHashMap<>();

		if (Files.exists(out)) {
			List<String> lines = Files.readAllLines(out, StandardCharsets.UTF_8);
			for (String line : lines) {
				if (line == null) {
					continue;
				}
				String t = line.trim();
				if (t.isEmpty()) {
					continue;
				}

				String[] parts = t.split("\t", 2);
				String csvKey = parts[0].trim();
				if (csvKey.isEmpty()) {
					continue;
				}

				String desc = (parts.length >= 2) ? parts[1] : "";
				csvKeyToLine.put(csvKey, csvKey + "\t" + desc);
			}
		} else {
			Files.createDirectories(out.getParent());
			Files.writeString(out, "", StandardCharsets.UTF_8);
		}

		if (failed != null) {
			for (SimpleEntry<String, List<DataEntity>> e : failed) {
				String csvKey = toRelativeCsvKey(baseDir, e.getKey());
				csvKeyToLine.remove(csvKey);
			}
		}

		if (succeeded != null) {
			for (SimpleEntry<String, List<DataEntity>> e : succeeded) {
				String csvKey = toRelativeCsvKey(baseDir, e.getKey());
				List<DataEntity> list = e.getValue();
				if (list == null || list.isEmpty()) {
					continue;
				}

				DataEntity row = findRowWithTeams(list);

				String dataCategory = safe(row.getDataCategory()).trim();
				String home = safe(row.getHomeTeamName()).trim();
				String away = safe(row.getAwayTeamName()).trim();

				String vsPart;
				if (!home.isEmpty() && !away.isEmpty()) {
					vsPart = home + "vs" + away;
				} else if (!home.isEmpty()) {
					vsPart = home;
				} else if (!away.isEmpty()) {
					vsPart = away;
				} else {
					vsPart = "(team name empty)";
				}

				String desc;
				if (!dataCategory.isEmpty() && !vsPart.isEmpty()) {
					desc = dataCategory + " - " + vsPart;
				} else if (!dataCategory.isEmpty()) {
					desc = dataCategory;
				} else {
					desc = vsPart;
				}

				csvKeyToLine.put(csvKey, csvKey + "\t" + desc);
			}
		}

		List<Map.Entry<String, String>> entries = new ArrayList<>(csvKeyToLine.entrySet());
		entries.sort((a, b) -> compareCsvRelativeKey(a.getKey(), b.getKey()));

		List<String> outLines = new ArrayList<>();
		for (Map.Entry<String, String> en : entries) {
			outLines.add(en.getValue());
		}

		Files.write(out, outLines, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
	}

	private static DataEntity findRowWithTeams(List<DataEntity> list) {
		for (DataEntity d : list) {
			String home = safe(d.getHomeTeamName()).trim();
			String away = safe(d.getAwayTeamName()).trim();
			if (!home.isEmpty() || !away.isEmpty()) {
				return d;
			}
		}
		return list.get(0);
	}

	private static String safe(String s) {
		return (s == null) ? "" : s;
	}

	// =========================================================
	// score / category 補完
	// =========================================================
	private static void backfillScores(List<DataEntity> list) {
		if (list == null || list.isEmpty()) {
			return;
		}

		list.sort(Comparator
				.comparing((DataEntity d) -> {
					String rt = d.getRecordTime();
					return (rt == null) ? "" : rt;
				})
				.thenComparingInt(d -> {
					try {
						return Integer.parseInt(Objects.toString(d.getSeq(), "0"));
					} catch (NumberFormatException e) {
						return Integer.MAX_VALUE;
					}
				}));

		String lastHome = null;
		String lastAway = null;
		for (DataEntity d : list) {
			if (isBlank(d.getHomeScore()) && lastHome != null) {
				d.setHomeScore(lastHome);
			}
			if (isBlank(d.getAwayScore()) && lastAway != null) {
				d.setAwayScore(lastAway);
			}

			if (!isBlank(d.getHomeScore())) {
				lastHome = d.getHomeScore();
			}
			if (!isBlank(d.getAwayScore())) {
				lastAway = d.getAwayScore();
			}
		}
	}

	private static boolean hasRound(String s) {
		return s != null && ROUND_TOKEN.matcher(s).find();
	}

	private static String pickCanonicalCategory(List<DataEntity> group) {
		if (group == null || group.isEmpty()) {
			return "";
		}
		for (DataEntity d : group) {
			String cat = d.getDataCategory();
			if (hasRound(cat)) {
				return cat.trim();
			}
		}
		String first = group.get(0).getDataCategory();
		return first == null ? "" : first.trim();
	}

	private static void applyCanonicalCategory(List<DataEntity> group) {
		String canonical = pickCanonicalCategory(group);
		if (canonical.isBlank()) {
			return;
		}

		for (DataEntity d : group) {
			String cat = d.getDataCategory();
			if (cat == null || cat.trim().isEmpty() || !hasRound(cat)) {
				d.setDataCategory(canonical);
			}
		}
	}

	// =========================================================
	// フォルダ/パス補助
	// =========================================================
	private String resolveRoundFolderName(List<DataEntity> group) {
		DataEntity row = findRowWithTeams(group);
		String dataCategory = safe(row.getDataCategory()).trim();

		List<String> countryLeague = ExecuteMainUtil.getCountryLeagueByRegex(dataCategory);

		String country = (countryLeague != null && countryLeague.size() >= 1)
				? sanitizePathToken(countryLeague.get(0))
				: "unknown";

		String league = (countryLeague != null && countryLeague.size() >= 2)
				? sanitizePathToken(countryLeague.get(1))
				: "unknown";

		Integer roundNo = extractRoundNo(dataCategory);
		String roundPart = (roundNo == null) ? "不明" : String.valueOf(roundNo);

		return country + "-" + league + "-ラウンド" + roundPart;
	}

	private Integer extractRoundNo(String dataCategory) {
		if (dataCategory == null || dataCategory.isBlank()) {
			return null;
		}

		Matcher m = ROUND_NO_PATTERN.matcher(dataCategory);
		if (!m.find()) {
			return null;
		}

		String digits = toHalfWidthDigits(m.group(1));
		try {
			return Integer.parseInt(digits);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private String toRelativeCsvKey(Path baseDir, String filePath) {
		Path base = baseDir.toAbsolutePath().normalize();
		Path file = Paths.get(filePath).toAbsolutePath().normalize();
		return base.relativize(file).toString().replace('\\', '/');
	}

	private static String sanitizePathToken(String value) {
		if (value == null || value.isBlank()) {
			return "unknown";
		}
		return value.trim()
				.replace("/", "_")
				.replace("\\", "_")
				.replace(":", "_")
				.replace("*", "_")
				.replace("?", "_")
				.replace("\"", "_")
				.replace("<", "_")
				.replace(">", "_")
				.replace("|", "_");
	}

	private static String toHalfWidthDigits(String in) {
		StringBuilder sb = new StringBuilder(in.length());
		for (char ch : in.toCharArray()) {
			if (ch >= '０' && ch <= '９') {
				sb.append((char) ('0' + (ch - '０')));
			} else {
				sb.append(ch);
			}
		}
		return sb.toString();
	}

	private static int compareCsvRelativeKey(String a, String b) {
		String fa = parentPath(a);
		String fb = parentPath(b);

		int folderCompare = fa.compareTo(fb);
		if (folderCompare != 0) {
			return folderCompare;
		}

		Integer na = parseCsvNo(a);
		Integer nb = parseCsvNo(b);

		if (na == null && nb == null) {
			return a.compareTo(b);
		}
		if (na == null) {
			return 1;
		}
		if (nb == null) {
			return -1;
		}
		return Integer.compare(na, nb);
	}

	private static String parentPath(String key) {
		if (key == null) {
			return "";
		}
		int idx = key.lastIndexOf('/');
		return (idx >= 0) ? key.substring(0, idx) : "";
	}

	// =========================================================
	// util
	// =========================================================
	private static boolean isBlank(String s) {
		return s == null || s.trim().isEmpty();
	}

	private static String normalizeS3Key(String key) {
		if (key == null) {
			return null;
		}
		String k = key;
		while (k.startsWith("/")) {
			k = k.substring(1);
		}
		return k;
	}

	private static String normalizePrefix(String prefix) {
		if (prefix == null) {
			return "";
		}
		String p = prefix.trim();
		p = p.replaceAll("^/+", "");
		p = p.replaceAll("/+$", "");
		return p;
	}

	private static String joinS3Key(String prefix, String fileName) {
		String p = (prefix == null) ? "" : prefix.trim();
		p = p.replaceAll("^/+", "");
		p = p.replaceAll("/+$", "");

		String f = (fileName == null) ? "" : fileName.trim();
		f = f.replaceAll("^/+", "");

		if (p.isBlank()) {
			return f;
		}
		return p + "/" + f;
	}

	private static int minSeqOfResult(List<DataEntity> list) {
		if (list == null || list.isEmpty()) {
			return Integer.MAX_VALUE;
		}
		int min = Integer.MAX_VALUE;
		for (DataEntity d : list) {
			if (d == null || d.getSeq() == null) {
				continue;
			}
			try {
				int v = Integer.parseInt(String.valueOf(d.getSeq()));
				if (v < min) {
					min = v;
				}
			} catch (NumberFormatException ignore) {
			}
		}
		return min;
	}

	private void putManageFilesEvenIfNoCsv(
			String statsBucket,
			String prefix,
			Path baseDir,
			Path localSeqPath,
			Path localTeamPath,
			List<List<Integer>> currentGroups) throws IOException {

		upsertDataTeamList(baseDir, localTeamPath, Collections.emptyList(), Collections.emptyList());
		putLocalFileToFinal(statsBucket, prefix, baseDir, localTeamPath);

		writeSeqListJson(localSeqPath, currentGroups);
		putLocalFileToFinal(statsBucket, prefix, baseDir, localSeqPath);
	}

	private void endLog(String method, String messageCd, String fillChar) {
		if (messageCd != null && fillChar != null) {
			this.manageLoggerComponent.debugInfoLog(PROJECT_NAME, CLASS_NAME, method, messageCd, fillChar);
		}
		this.manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, method, "end");
	}

	// =========================================================
	// inner classes
	// =========================================================
	private static final class CsvBuildPlan {
		private final Map<String, List<Integer>> recreateByCsvKey = new LinkedHashMap<>();
		private final Map<String, List<Integer>> newTargets = new LinkedHashMap<>();
	}

	private static final class CsvArtifact {
		private final String filePath;
		private final List<DataEntity> content;

		private CsvArtifact(String filePath, List<DataEntity> content) {
			this.filePath = filePath;
			this.content = content;
		}

		public String getFilePath() {
			return filePath;
		}

		public List<DataEntity> getContent() {
			return content;
		}
	}

	/**
	 * 現在のグループ一覧から、CSV生成対象として有効な DataEntity リストを事前読込する。
	 *
	 * @param currentGroups 現在グループ
	 * @param csvArtifactResource 条件リソース
	 * @param methodName 呼び出し元メソッド名
	 * @return key: groupKey, value: DataEntity一覧
	 */
	private Map<String, List<DataEntity>> preloadGroupResults(
			List<List<Integer>> currentGroups,
			CsvArtifactResource csvArtifactResource,
			String methodName) {

		Map<String, List<DataEntity>> groupResultMap = new LinkedHashMap<>();

		if (currentGroups == null || currentGroups.isEmpty()) {
			return groupResultMap;
		}

		for (List<Integer> group : currentGroups) {
			List<Integer> ids = normalizeSeqList(group);
			if (ids.isEmpty()) {
				continue;
			}

			List<DataEntity> result = fetchAndFilter(ids, csvArtifactResource, methodName, "preload findByData");
			if (result == null || result.isEmpty()) {
				continue;
			}

			groupResultMap.put(groupKey(ids), result);
		}

		return groupResultMap;
	}

	/**
	 * 事前読込したグループ結果から対象フォルダ一覧を抽出する。
	 *
	 * @param groupResultMap group結果
	 * @return 対象フォルダ一覧
	 */
	private Set<String> collectTargetFolders(Map<String, List<DataEntity>> groupResultMap) {
		Set<String> targetFolders = new LinkedHashSet<>();

		if (groupResultMap == null || groupResultMap.isEmpty()) {
			return targetFolders;
		}

		for (List<DataEntity> result : groupResultMap.values()) {
			if (result == null || result.isEmpty()) {
				continue;
			}
			targetFolders.add(resolveRoundFolderName(result));
		}

		return targetFolders;
	}

}
