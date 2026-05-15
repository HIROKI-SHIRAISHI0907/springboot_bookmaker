package dev.batch.bm_b011;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
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
import org.springframework.transaction.annotation.Transactional;

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
 * StatデータCSV出力ロジック（チャンク処理版）
 */
@Component
@Transactional
public class ExportCsvService {

	private static final String PROJECT_NAME = ExportCsvService.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	private static final String CLASS_NAME = ExportCsvService.class.getName();

	private static final String CSV_NEW_PREFIX = "mk";

	private static final com.fasterxml.jackson.databind.ObjectMapper SEQ_JSON = new com.fasterxml.jackson.databind.ObjectMapper();

	private static final Pattern ROUND_TOKEN = Pattern.compile("ラウンド\\s*[0-9０-９]+");

	private static final Pattern ROUND_NO_PATTERN = Pattern.compile("ラウンド\\s*([0-9０-９]+)");

	private static final Pattern CSV_NO_PATTERN = Pattern.compile("(^|.*/)(\\d+)\\.csv$", Pattern.CASE_INSENSITIVE);

	@Value("${exportcsv.local-only:false}")
	private boolean localOnly;

	@Value("${exportcsv.final-prefix:}")
	private String finalPrefix;

	@Value("${exportcsv.always-put-manage-files:true}")
	private boolean alwaysPutManageFiles;

	@Value("${exportcsv.work-chunk-size:20}")
	private int workChunkSize;

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
						+ ", teamKeyFinal=" + teamKeyFinal
						+ ", workChunkSize=" + workChunkSize);

		boolean seqExists = downloadIfExists(statsBucket, seqKeyFinal, localSeqPath, "seqList.txt download");
		downloadIfExists(statsBucket, teamKeyFinal, localTeamPath, "data_team_list.txt download");

		List<List<Integer>> currentGroups = normalizeGroups(sortSeqs());

		boolean firstRun = !seqExists || !Files.exists(localSeqPath);
		List<List<Integer>> textGroups;

		if (firstRun) {
			writeSeqListJson(localSeqPath, currentGroups);
			textGroups = Collections.emptyList();
		} else {
			textGroups = normalizeGroups(readSeqListJson(localSeqPath));
		}

		Map<String, List<Integer>> csvInfoRow;
		try {
			bean.init();
			csvInfoRow = bean.getCsvInfo();
		} catch (Exception e) {
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION, e,
					"ReaderCurrentCsvInfoBean.init() 失敗");
			throw e;
		}
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

		List<CsvWorkItem> workItems = buildWorkItems(
				LOCAL_DIR,
				plan,
				csvArtifactResource,
				METHOD_NAME,
				true);

		if (workItems.isEmpty()) {
			if (alwaysPutManageFiles) {
				putManageFilesEvenIfNoCsv(statsBucket, prefix, LOCAL_DIR, localSeqPath, localTeamPath, currentGroups);
			}

			String messageCd = MessageCdConst.MCD00014I_NO_MAP_DATA;
			String fillChar = "追加レコードがないため処理終了 (workItems=0)";
			endLog(METHOD_NAME, messageCd, fillChar);
			return;
		}

		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG,
				"workItems.size=" + workItems.size()
						+ ", recreate=" + plan.recreateByCsvKey.size()
						+ ", newTargets=" + plan.newTargets.size()
						+ ", firstRun=" + firstRun);

		int threads = Math.max(2, Runtime.getRuntime().availableProcessors() - 1);
		ExecutorService pool = Executors.newFixedThreadPool(threads);

		ProcessResult processResult;
		try {
			processResult = processWorkItemsInChunks(
					workItems,
					csvArtifactResource,
					pool,
					LOCAL_DIR,
					false,
					statsBucket,
					prefix,
					METHOD_NAME);
		} finally {
			pool.shutdown();
			try {
				pool.awaitTermination(1, TimeUnit.MINUTES);
			} catch (InterruptedException ignore) {
				Thread.currentThread().interrupt();
			}
		}

		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG,
				"CSVアップロード結果(final put) (成功: " + processResult.successCount
						+ "件, 失敗: " + processResult.failedCount
						+ "件, スキップ: " + processResult.skippedCount + "件)");

		registerCsvDetailManage(processResult.succeeded, METHOD_NAME);

		try {
			upsertDataTeamList(localTeamPath, processResult.succeeded, processResult.failedRelativeKeys);
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

		if (processResult.failedCount > 0) {
			this.manageLoggerComponent.debugWarnLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00099I_LOG,
					"失敗があるため一部のみアップロードされている可能性があります");
		}

		endLog(METHOD_NAME, null, null);
	}

	private void executeLocalOnly(Path outDir) throws IOException {
		final String METHOD_NAME = "executeLocalOnly";

		final Path LOCAL_DIR = outDir;
		ensureDir(LOCAL_DIR);

		final Path localSeqPath = LOCAL_DIR.resolve("seqList.txt");
		final Path localTeamPath = LOCAL_DIR.resolve("data_team_list.txt");

		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG,
				"localOnly=true: S3処理を完全にスキップします。localDir=" + LOCAL_DIR
						+ ", workChunkSize=" + workChunkSize);

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

		List<List<Integer>> currentGroups = normalizeGroups(sortSeqs());

		boolean firstRun = !Files.exists(localSeqPath);
		List<List<Integer>> textGroups;

		if (firstRun) {
			writeSeqListJson(localSeqPath, currentGroups);
			textGroups = Collections.emptyList();
		} else {
			textGroups = normalizeGroups(readSeqListJson(localSeqPath));
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
			upsertDataTeamList(localTeamPath, Collections.emptyList(), Collections.emptySet());
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

		List<CsvWorkItem> workItems = buildWorkItems(
				LOCAL_DIR,
				plan,
				csvArtifactResource,
				METHOD_NAME,
				false);

		if (workItems.isEmpty()) {
			upsertDataTeamList(localTeamPath, Collections.emptyList(), Collections.emptySet());
			writeSeqListJson(localSeqPath, currentGroups);

			String messageCd = MessageCdConst.MCD00014I_NO_MAP_DATA;
			String fillChar = "追加レコードがないため処理終了 (workItems=0)";
			endLog(METHOD_NAME, messageCd, fillChar);
			return;
		}

		int threads = Math.max(2, Runtime.getRuntime().availableProcessors() - 1);
		ExecutorService pool = Executors.newFixedThreadPool(threads);

		ProcessResult processResult;
		try {
			processResult = processWorkItemsInChunks(
					workItems,
					csvArtifactResource,
					pool,
					LOCAL_DIR,
					true,
					null,
					null,
					METHOD_NAME);
		} finally {
			pool.shutdown();
			try {
				pool.awaitTermination(1, TimeUnit.MINUTES);
			} catch (InterruptedException ignore) {
				Thread.currentThread().interrupt();
			}
		}

		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG,
				"localOnly: CSV生成結果 (成功: " + processResult.successCount
						+ "件, 失敗: " + processResult.failedCount
						+ "件, スキップ: " + processResult.skippedCount + "件)");

		registerCsvDetailManage(processResult.succeeded, METHOD_NAME);

		upsertDataTeamList(localTeamPath, processResult.succeeded, processResult.failedRelativeKeys);
		writeSeqListJson(localSeqPath, currentGroups);

		if (processResult.failedCount > 0) {
			this.manageLoggerComponent.debugWarnLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00099I_LOG,
					"localOnly: 失敗があるため一部未生成の可能性があります。localDir=" + LOCAL_DIR);
		}

		endLog(METHOD_NAME, null, null);
	}

	private List<CsvWorkItem> buildWorkItems(
			Path localDir,
			CsvBuildPlan plan,
			CsvArtifactResource csvArtifactResource,
			String parentMethod,
			boolean s3Mode) {

		List<CsvWorkItem> workItems = new ArrayList<>();

		for (Map.Entry<String, List<Integer>> entry : plan.recreateByCsvKey.entrySet()) {
			String relativeKey = entry.getKey();
			List<Integer> ids = normalizeSeqList(entry.getValue());

			if (relativeKey == null || relativeKey.isBlank() || ids.isEmpty()) {
				continue;
			}

			workItems.add(new CsvWorkItem(relativeKey, ids));
		}

		Map<String, List<List<Integer>>> newTargetsByFolder = resolveNewTargetsByFolder(
				plan.newTargets,
				csvArtifactResource,
				parentMethod);

		if (!newTargetsByFolder.isEmpty()) {
			if (s3Mode) {
				Map<String, Integer> s3MaxByFolder = getStatInfo.getMaxCsvNoByFolders(newTargetsByFolder.keySet());

				for (Map.Entry<String, List<List<Integer>>> e : newTargetsByFolder.entrySet()) {
					String folderName = e.getKey();
					List<List<Integer>> groups = e.getValue();

					groups.sort(Comparator.comparingInt(ExportCsvService::minSeqOfIds));

					int maxOnS3 = s3MaxByFolder.getOrDefault(folderName, 0);
					int nextNo = maxOnS3 + 1;

					for (int i = 0; i < groups.size(); i++) {
						int csvNo = nextNo + i;
						String relativeKey = joinS3Key(folderName, csvNo + BookMakersCommonConst.CSV);
						workItems.add(new CsvWorkItem(relativeKey, groups.get(i)));
					}

					this.manageLoggerComponent.debugInfoLog(
							PROJECT_NAME, CLASS_NAME, parentMethod, MessageCdConst.MCD00099I_LOG,
							"採番(S3): folder=" + folderName
									+ ", maxOnS3=" + maxOnS3
									+ ", nextNo=" + nextNo
									+ ", groupCount=" + groups.size());
				}
			} else {
				for (Map.Entry<String, List<List<Integer>>> e : newTargetsByFolder.entrySet()) {
					String folderName = e.getKey();
					List<List<Integer>> groups = e.getValue();

					groups.sort(Comparator.comparingInt(ExportCsvService::minSeqOfIds));

					int maxLocal = getMaxCsvNoFromLocal(localDir.resolve(folderName));
					int nextNo = maxLocal + 1;

					for (int i = 0; i < groups.size(); i++) {
						int csvNo = nextNo + i;
						String relativeKey = joinS3Key(folderName, csvNo + BookMakersCommonConst.CSV);
						workItems.add(new CsvWorkItem(relativeKey, groups.get(i)));
					}

					this.manageLoggerComponent.debugInfoLog(
							PROJECT_NAME, CLASS_NAME, parentMethod, MessageCdConst.MCD00099I_LOG,
							"採番(Local): folder=" + folderName
									+ ", maxLocal=" + maxLocal
									+ ", nextNo=" + nextNo
									+ ", groupCount=" + groups.size());
				}
			}
		}

		workItems.sort((a, b) -> compareCsvRelativeKey(a.getRelativeKey(), b.getRelativeKey()));
		return workItems;
	}

	private Map<String, List<List<Integer>>> resolveNewTargetsByFolder(
			Map<String, List<Integer>> newTargets,
			CsvArtifactResource csvArtifactResource,
			String parentMethod) {

		Map<String, List<List<Integer>>> newTargetsByFolder = new LinkedHashMap<>();
		if (newTargets == null || newTargets.isEmpty()) {
			return newTargetsByFolder;
		}

		for (Map.Entry<String, List<Integer>> entry : newTargets.entrySet()) {
			List<Integer> ids = normalizeSeqList(entry.getValue());
			if (ids.isEmpty()) {
				continue;
			}

			List<DataEntity> preview = fetchAndFilter(ids, csvArtifactResource, parentMethod, "resolveNewTargetsByFolder");
			if (preview == null || preview.isEmpty()) {
				continue;
			}

			String folderName = resolveRoundFolderName(preview);
			newTargetsByFolder.computeIfAbsent(folderName, k -> new ArrayList<>()).add(ids);
		}

		return newTargetsByFolder;
	}

	private ProcessResult processWorkItemsInChunks(
			List<CsvWorkItem> workItems,
			CsvArtifactResource csvArtifactResource,
			ExecutorService pool,
			Path baseDir,
			boolean localMode,
			String bucket,
			String prefix,
			String parentMethod) {

		ProcessResult processResult = new ProcessResult();
		if (workItems == null || workItems.isEmpty()) {
			return processResult;
		}

		int chunkSize = Math.max(1, workChunkSize);

		for (int from = 0; from < workItems.size(); from += chunkSize) {
			int to = Math.min(from + chunkSize, workItems.size());
			List<CsvWorkItem> chunk = workItems.subList(from, to);

			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, parentMethod, MessageCdConst.MCD00099I_LOG,
					"chunk start: from=" + (from + 1) + ", to=" + to + ", total=" + workItems.size());

			List<CompletableFuture<CsvTaskResult>> futures = new ArrayList<>(chunk.size());

			for (CsvWorkItem item : chunk) {
				futures.add(CompletableFuture.supplyAsync(
						() -> processSingleWorkItem(
								item,
								csvArtifactResource,
								baseDir,
								localMode,
								bucket,
								prefix,
								parentMethod),
						pool));
			}

			for (CompletableFuture<CsvTaskResult> future : futures) {
				CsvTaskResult taskResult = future.join();
				if (taskResult == null) {
					continue;
				}

				switch (taskResult.getStatus()) {
				case SUCCESS:
					processResult.successCount++;
					if (taskResult.getMeta() != null) {
						processResult.succeeded.add(taskResult.getMeta());
					}
					break;
				case FAILED:
					processResult.failedCount++;
					if (taskResult.getRelativeKey() != null && !taskResult.getRelativeKey().isBlank()) {
						processResult.failedRelativeKeys.add(taskResult.getRelativeKey());
					}
					break;
				case SKIPPED:
				default:
					processResult.skippedCount++;
					break;
				}
			}

			futures.clear();

			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, parentMethod, MessageCdConst.MCD00099I_LOG,
					"chunk end: from=" + (from + 1)
							+ ", to=" + to
							+ ", success=" + processResult.successCount
							+ ", failed=" + processResult.failedCount
							+ ", skipped=" + processResult.skippedCount);
		}

		return processResult;
	}

	private CsvTaskResult processSingleWorkItem(
			CsvWorkItem item,
			CsvArtifactResource csvArtifactResource,
			Path baseDir,
			boolean localMode,
			String bucket,
			String prefix,
			String parentMethod) {

		final String METHOD_NAME = "processSingleWorkItem";

		try {
			List<DataEntity> result = fetchAndFilter(
					item.getSeqIds(),
					csvArtifactResource,
					parentMethod,
					"processSingleWorkItem: " + item.getRelativeKey());

			if (result == null || result.isEmpty()) {
				return CsvTaskResult.skipped(item.getRelativeKey());
			}

			String filePath = baseDir.resolve(item.getRelativeKey()).toString();
			CsvArtifact art = buildCsvArtifact(filePath, result, csvArtifactResource);

			if (art == null || art.getContent() == null || art.getContent().isEmpty()) {
				return CsvTaskResult.skipped(item.getRelativeKey());
			}

			DataEntity row = findRowWithTeams(result);
			CsvOutputMeta meta = new CsvOutputMeta(
					item.getRelativeKey(),
					safe(row.getDataCategory()).trim(),
					safe(row.getHomeTeamName()).trim(),
					safe(row.getAwayTeamName()).trim());

			writeLocalCsv(art);

			if (!localMode) {
				putLocalFileToFinal(bucket, prefix, baseDir, Paths.get(art.getFilePath()));
			}

			return CsvTaskResult.success(item.getRelativeKey(), meta);

		} catch (Exception ex) {
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION, ex,
					"CSV作成処理失敗 relativeKey=" + item.getRelativeKey());

			return CsvTaskResult.failed(item.getRelativeKey());
		}
	}

	private void registerCsvDetailManage(
			List<CsvOutputMeta> succeeded,
			String parentMethod) {

		final String METHOD_NAME = "registerCsvDetailManage";

		if (succeeded == null || succeeded.isEmpty()) {
			return;
		}

		for (CsvOutputMeta meta : succeeded) {
			if (meta == null) {
				continue;
			}

			String csvId = meta.getRelativeCsvKey();
			String dataCategory = safe(meta.getDataCategory()).trim();
			String home = safe(meta.getHomeTeamName()).trim();
			String away = safe(meta.getAwayTeamName()).trim();

			if (csvId == null || csvId.isBlank()) {
				continue;
			}

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
			int result = this.csvDetailManageRepository.update(entity);
			if (result != 1) {
				String messageCd = MessageCdConst.MCD00008E_UPDATE_FAILED;
				this.rootCauseWrapper.throwUnexpectedRowCount(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						messageCd,
						1, result,
						context + ", csvId=" + csvId);
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
						context + ", csvId=" + csvId);
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

		String folder = parentPath(csvId);
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

	private void upsertDataTeamList(
			Path out,
			List<CsvOutputMeta> succeeded,
			Set<String> failedRelativeKeys) throws IOException {

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
			if (out.getParent() != null) {
				Files.createDirectories(out.getParent());
			}
			Files.writeString(out, "", StandardCharsets.UTF_8,
					StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
		}

		if (failedRelativeKeys != null) {
			for (String csvKey : failedRelativeKeys) {
				if (csvKey == null || csvKey.isBlank()) {
					continue;
				}
				csvKeyToLine.remove(csvKey);
			}
		}

		if (succeeded != null) {
			for (CsvOutputMeta meta : succeeded) {
				if (meta == null) {
					continue;
				}

				String csvKey = meta.getRelativeCsvKey();
				if (csvKey == null || csvKey.isBlank()) {
					continue;
				}

				String dataCategory = safe(meta.getDataCategory()).trim();
				String home = safe(meta.getHomeTeamName()).trim();
				String away = safe(meta.getAwayTeamName()).trim();

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

		Files.write(out, outLines, StandardCharsets.UTF_8,
				StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
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

	private static int minSeqOfIds(List<Integer> ids) {
		if (ids == null || ids.isEmpty()) {
			return Integer.MAX_VALUE;
		}
		int min = Integer.MAX_VALUE;
		for (Integer id : ids) {
			if (id == null) {
				continue;
			}
			if (id < min) {
				min = id;
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

		upsertDataTeamList(localTeamPath, Collections.emptyList(), Collections.emptySet());
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

	private static final class CsvWorkItem {
		private final String relativeKey;
		private final List<Integer> seqIds;

		private CsvWorkItem(String relativeKey, List<Integer> seqIds) {
			this.relativeKey = relativeKey;
			this.seqIds = seqIds;
		}

		public String getRelativeKey() {
			return relativeKey;
		}

		public List<Integer> getSeqIds() {
			return seqIds;
		}
	}

	private static final class CsvOutputMeta {
		private final String relativeCsvKey;
		private final String dataCategory;
		private final String homeTeamName;
		private final String awayTeamName;

		private CsvOutputMeta(
				String relativeCsvKey,
				String dataCategory,
				String homeTeamName,
				String awayTeamName) {
			this.relativeCsvKey = relativeCsvKey;
			this.dataCategory = dataCategory;
			this.homeTeamName = homeTeamName;
			this.awayTeamName = awayTeamName;
		}

		public String getRelativeCsvKey() {
			return relativeCsvKey;
		}

		public String getDataCategory() {
			return dataCategory;
		}

		public String getHomeTeamName() {
			return homeTeamName;
		}

		public String getAwayTeamName() {
			return awayTeamName;
		}
	}

	private enum CsvTaskStatus {
		SUCCESS,
		FAILED,
		SKIPPED
	}

	private static final class CsvTaskResult {
		private final CsvTaskStatus status;
		private final String relativeKey;
		private final CsvOutputMeta meta;

		private CsvTaskResult(CsvTaskStatus status, String relativeKey, CsvOutputMeta meta) {
			this.status = status;
			this.relativeKey = relativeKey;
			this.meta = meta;
		}

		public static CsvTaskResult success(String relativeKey, CsvOutputMeta meta) {
			return new CsvTaskResult(CsvTaskStatus.SUCCESS, relativeKey, meta);
		}

		public static CsvTaskResult failed(String relativeKey) {
			return new CsvTaskResult(CsvTaskStatus.FAILED, relativeKey, null);
		}

		public static CsvTaskResult skipped(String relativeKey) {
			return new CsvTaskResult(CsvTaskStatus.SKIPPED, relativeKey, null);
		}

		public CsvTaskStatus getStatus() {
			return status;
		}

		public String getRelativeKey() {
			return relativeKey;
		}

		public CsvOutputMeta getMeta() {
			return meta;
		}
	}

	private static final class ProcessResult {
		private int successCount;
		private int failedCount;
		private int skippedCount;
		private final List<CsvOutputMeta> succeeded = new ArrayList<>();
		private final Set<String> failedRelativeKeys = new LinkedHashSet<>();
	}
}
