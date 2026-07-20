package dev.batch.bm_b011;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.Normalizer;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.batch.repository.bm.BookCsvDataRepository;
import dev.batch.repository.bm.BookCsvDetailManageRepository;
import dev.batch.repository.master.CountryLeagueSeasonMasterBatchRepository;
import dev.batch.repository.master.FutureMasterRepository;
import dev.batch.service.CsvFileNameService;
import dev.batch.service.FileExistsService;
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
public class ExportCsvService {

	private static final String PROJECT_NAME = ExportCsvService.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	private static final String CLASS_NAME = ExportCsvService.class.getName();

	private static final String CSV_NEW_PREFIX = "mk";

	private static final com.fasterxml.jackson.databind.ObjectMapper SEQ_JSON = new com.fasterxml.jackson.databind.ObjectMapper();

	private static final Pattern ROUND_TOKEN = Pattern.compile("ラウンド\\s*[0-9０-９]+");

	private static final Pattern CSV_NO_PATTERN = Pattern.compile("(^|.*/)(\\d+)\\.csv$", Pattern.CASE_INSENSITIVE);

	@Value("${exportcsv.local-only:false}")
	private boolean localOnly;

	@Value("${exportcsv.final-prefix:}")
	private String finalPrefix;

	@Value("${exportcsv.always-put-manage-files:true}")
	private boolean alwaysPutManageFiles;

	@Value("${exportcsv.work-chunk-size:20}")
	private int workChunkSize;

	/**
	 * グルーピングサイズ
	 */
	private static final int GROUP_PAGE_SIZE = 300;

	@Autowired
	private FileExistsService fileExistsService;

	@Autowired
	private CsvFileNameService csvFileNameService;

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
	private FutureMasterRepository futureMasterRepository;

	@Autowired
	private BookCsvDetailManageRepository csvDetailManageRepository;

	@Autowired
	private CountryLeagueSeasonMasterBatchRepository countryLeagueSeasonMasterBatchRepository;

	@Autowired
	private RootCauseWrapper rootCauseWrapper;

	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * 実行メソッド
	 * @throws IOException
	 */
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public void execute() throws IOException {
		final String METHOD_NAME = "execute";
		this.manageLoggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, "start");

		logInfo(METHOD_NAME, "execute 開始 localOnly=" + localOnly
				+ ", finalPrefix=" + finalPrefix
				+ ", workChunkSize=" + workChunkSize
				+ ", groupPageSize=" + GROUP_PAGE_SIZE);

		Path outDir = Paths.get(config.getCsvFolder()).toAbsolutePath().normalize();
		Files.createDirectories(outDir);
		logInfo(METHOD_NAME, "出力ディレクトリ準備完了 outDir=" + outDir);

		if (localOnly) {
			logInfo(METHOD_NAME, "localOnly=true のため executeLocalOnly へ移行");
			executeLocalOnly(outDir);
			return;
		}

		final String statsBucket = config.getS3BucketsStats();
		final String prefix = normalizePrefix(finalPrefix);

		final String seqFileName = "seqList.txt";
		final String teamFileName = "data_team_list.txt";

		final Path LOCAL_DIR = outDir;
		ensureDir(LOCAL_DIR);

		final Path localSeqPath = LOCAL_DIR.resolve(seqFileName);
		final Path localTeamPath = LOCAL_DIR.resolve(teamFileName);

		logInfo(METHOD_NAME,
				"実行パラメータ bucket=" + statsBucket
						+ ", prefix=" + prefix
						+ ", localSeqPath=" + localSeqPath
						+ ", localTeamPath=" + localTeamPath);

		CsvArtifactResource csvArtifactResource;
		try {
			logInfo(METHOD_NAME, "helper.getData() 開始");
			csvArtifactResource = this.helper.getData();
			logInfo(METHOD_NAME, "helper.getData() 終了 resource取得成功");
		} catch (Exception e) {
			logError(METHOD_NAME, "helper.getData() 失敗", e);
			throw e;
		}

		int totalGroupCount = 0;
		try {
			logInfo(METHOD_NAME, "countGroupTargets() 開始");
			totalGroupCount = this.bookCsvDataRepository.countGroupTargets();
			logInfo(METHOD_NAME, "countGroupTargets() 終了 totalGroupCount=" + totalGroupCount);
		} catch (Exception e) {
			logWarn(METHOD_NAME, "countGroupTargets() 失敗。処理継続");
		}

		boolean seqExists = fileExistsService.downloadIfExists(
		        statsBucket,
		        prefix,
		        seqFileName,
		        localSeqPath,
		        "seqList.txt download");

		boolean teamExists = fileExistsService.downloadIfExists(
		        statsBucket,
		        prefix,
		        teamFileName,
		        localTeamPath,
		        "data_team_list.txt download");

		logInfo(METHOD_NAME, "管理ファイル取得結果 seqExists=" + seqExists + ", teamExists=" + teamExists);

		logInfo(METHOD_NAME, "sortSeqs() 開始");
		List<List<Integer>> currentGroups = normalizeGroups(sortSeqs());
		logInfo(METHOD_NAME, "sortSeqs() 終了 currentGroups.size=" + currentGroups.size());

		boolean firstRun = !seqExists || !Files.exists(localSeqPath);
		List<List<Integer>> textGroups;

		logInfo(METHOD_NAME, "firstRun判定 result=" + firstRun + ", localSeqPathExists=" + Files.exists(localSeqPath));

		if (firstRun) {
			logInfo(METHOD_NAME, "初回実行のため seqListJson を新規出力");
			writeSeqListJson(localSeqPath, currentGroups);
			textGroups = Collections.emptyList();
		} else {
			logInfo(METHOD_NAME, "既存 seqListJson 読み込み開始");
			textGroups = normalizeGroups(readSeqListJson(localSeqPath));
			logInfo(METHOD_NAME, "既存 seqListJson 読み込み終了 textGroups.size=" + textGroups.size());
		}

		Map<String, List<Integer>> csvInfoRow;
		try {
			logInfo(METHOD_NAME, "ReaderCurrentCsvInfoBean.init() 開始");
			bean.init();
			csvInfoRow = bean.getCsvInfo();
			logInfo(METHOD_NAME, "ReaderCurrentCsvInfoBean.init() 終了 csvInfoRow.size="
					+ (csvInfoRow == null ? 0 : csvInfoRow.size()));
		} catch (Exception e) {
			logError(METHOD_NAME, "ReaderCurrentCsvInfoBean.init() 失敗", e);
			throw e;
		}
		csvInfoRow = canonicalizeCsvInfoMap(
				(csvInfoRow != null) ? csvInfoRow : Collections.emptyMap());

		CsvBuildPlan plan;
		if (firstRun) {
			logInfo(METHOD_NAME, "初回実行向け CsvBuildPlan 構築開始");
			CsvBuildPlan plans = new CsvBuildPlan();
			int i = 0;
			for (List<Integer> curr : currentGroups) {
				plans.newTargets.put(CSV_NEW_PREFIX + "-" + (i++), curr);
			}
			plan = plans;
			logInfo(METHOD_NAME, "初回実行向け CsvBuildPlan 構築終了 newTargets.size=" + plan.newTargets.size());
		} else {
			logInfo(METHOD_NAME, "matchSeqCombPlan() 開始 textGroups.size=" + textGroups.size()
					+ ", currentGroups.size=" + currentGroups.size()
					+ ", csvInfoRow.size=" + csvInfoRow.size());
			plan = matchSeqCombPlan(textGroups, currentGroups, csvInfoRow);
			logInfo(METHOD_NAME, "matchSeqCombPlan() 終了 recreateByCsvKey.size="
					+ (plan == null ? 0 : plan.recreateByCsvKey.size())
					+ ", newTargets.size=" + (plan == null ? 0 : plan.newTargets.size()));
		}

		if (plan == null || (plan.recreateByCsvKey.isEmpty() && plan.newTargets.isEmpty())) {

			logInfo(METHOD_NAME, "処理対象なし plan空");

			if (alwaysPutManageFiles) {
				logInfo(METHOD_NAME, "alwaysPutManageFiles=true のため管理ファイルPUT開始");
				putManageFilesEvenIfNoCsv(statsBucket, prefix, LOCAL_DIR, localSeqPath, localTeamPath, currentGroups);
				logInfo(METHOD_NAME, "管理ファイルPUT終了");
			}

			String messageCd = MessageCdConst.MCD00014I_NO_MAP_DATA;
			String fillChar = "追加レコードがないため処理終了 (既存CSV数: " + csvInfoRow.size() + "件)";
			endLog(METHOD_NAME, messageCd, fillChar);
			return;
		}

		logInfo(METHOD_NAME, "buildWorkItems() 開始");
		List<CsvWorkItem> workItems = buildWorkItems(
				LOCAL_DIR,
				plan,
				csvArtifactResource,
				METHOD_NAME,
				true);
		logInfo(METHOD_NAME, "buildWorkItems() 終了 workItems.size=" + workItems.size());

		if (workItems.isEmpty()) {
			logInfo(METHOD_NAME, "workItems=0 のためCSV生成なし");

			if (alwaysPutManageFiles) {
				logInfo(METHOD_NAME, "alwaysPutManageFiles=true のため管理ファイルPUT開始");
				putManageFilesEvenIfNoCsv(statsBucket, prefix, LOCAL_DIR, localSeqPath, localTeamPath, currentGroups);
				logInfo(METHOD_NAME, "管理ファイルPUT終了");
			}

			String messageCd = MessageCdConst.MCD00014I_NO_MAP_DATA;
			String fillChar = "追加レコードがないため処理終了 (workItems=0)";
			endLog(METHOD_NAME, messageCd, fillChar);
			return;
		}

		logInfo(METHOD_NAME,
				"処理対象 summary workItems.size=" + workItems.size()
						+ ", recreate=" + plan.recreateByCsvKey.size()
						+ ", newTargets=" + plan.newTargets.size()
						+ ", firstRun=" + firstRun);

		int threads = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 2));
		logInfo(METHOD_NAME, "ExecutorService 作成 threads=" + threads);
		ExecutorService pool = Executors.newFixedThreadPool(threads);

		ProcessResult processResult;
		try {
			logInfo(METHOD_NAME, "processWorkItemsInChunks() 開始");
			processResult = processWorkItemsInChunks(
					workItems,
					csvArtifactResource,
					pool,
					LOCAL_DIR,
					false,
					statsBucket,
					prefix,
					METHOD_NAME);
			logInfo(METHOD_NAME, "processWorkItemsInChunks() 終了 success=" + processResult.successCount
					+ ", failed=" + processResult.failedCount
					+ ", skipped=" + processResult.skippedCount);
		} finally {
			logInfo(METHOD_NAME, "ExecutorService shutdown 開始");
			pool.shutdown();
			try {
				pool.awaitTermination(1, TimeUnit.MINUTES);
				logInfo(METHOD_NAME, "ExecutorService shutdown 完了");
			} catch (InterruptedException ignore) {
				Thread.currentThread().interrupt();
				logWarn(METHOD_NAME, "ExecutorService shutdown 中に interrupt");
			}
		}

		logInfo(METHOD_NAME, "registerCsvDetailManage() 開始 succeeded.size=" + processResult.succeeded.size());
		registerCsvDetailManage(processResult.succeeded, METHOD_NAME);
		logInfo(METHOD_NAME, "registerCsvDetailManage() 終了");

		try {
			logInfo(METHOD_NAME, "data_team_list 更新開始");
			upsertDataTeamList(localTeamPath, processResult.succeeded, processResult.failedRelativeKeys);
			logInfo(METHOD_NAME, "data_team_list 更新終了");

			logInfo(METHOD_NAME, "data_team_list S3 PUT 開始");
			fileExistsService.uploadDataTeamListIfExists(statsBucket, prefix);
			logInfo(METHOD_NAME, "data_team_list S3 PUT 終了");
		} catch (Exception e) {
			logError(METHOD_NAME, "data_team_list.txt 更新/PUT(final) 失敗", e);
			throw (e instanceof IOException) ? (IOException) e : new IOException(e);
		}

		try {
			logInfo(METHOD_NAME, "seqListJson 更新開始 currentGroups.size=" + currentGroups.size());
			writeSeqListJson(localSeqPath, currentGroups);
			logInfo(METHOD_NAME, "seqListJson 更新終了");

			logInfo(METHOD_NAME, "seqListJson S3 PUT 開始");
			fileExistsService.uploadSeqListIfExists(statsBucket, prefix);
			logInfo(METHOD_NAME, "seqListJson S3 PUT 終了");
		} catch (Exception e) {
			logError(METHOD_NAME, "seqList.txt 更新/PUT(final) 失敗", e);
			throw (e instanceof IOException) ? (IOException) e : new IOException(e);
		}

		if (processResult.failedCount > 0) {
			logWarn(METHOD_NAME, "失敗あり failedCount=" + processResult.failedCount
					+ ", failedRelativeKeys.size=" + processResult.failedRelativeKeys.size());
		}

		logInfo(METHOD_NAME, "execute 正常終了");
		endLog(METHOD_NAME, null, null);
	}

	/**
	 * ローカル
	 * @param outDir
	 * @throws IOException
	 */
	private void executeLocalOnly(Path outDir) throws IOException {
		final String METHOD_NAME = "executeLocalOnly";
		this.manageLoggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, "start");

		final Path LOCAL_DIR = outDir;
		ensureDir(LOCAL_DIR);

		final Path localSeqPath = LOCAL_DIR.resolve("seqList.txt");
		final Path localTeamPath = LOCAL_DIR.resolve("data_team_list.txt");

		logInfo(METHOD_NAME, "localOnly 実行開始 localDir=" + LOCAL_DIR
				+ ", workChunkSize=" + workChunkSize
				+ ", groupPageSize=" + GROUP_PAGE_SIZE);

		try {
			int totalGroupCount = 0;
			try {
				logInfo(METHOD_NAME, "countGroupTargets() 開始");
				totalGroupCount = this.bookCsvDataRepository.countGroupTargets();
				logInfo(METHOD_NAME, "countGroupTargets() 終了 totalGroupCount=" + totalGroupCount);
			} catch (Exception e) {
				logWarn(METHOD_NAME, "countGroupTargets() 失敗。処理継続");
			}

			Map<String, List<Integer>> csvInfoRow;
			try {
				logInfo(METHOD_NAME, "ReaderCurrentCsvInfoBean.init() 開始");
				bean.init();
				csvInfoRow = (bean != null ? bean.getCsvInfo() : null);
				logInfo(METHOD_NAME, "ReaderCurrentCsvInfoBean.init() 終了 csvInfoRow.size="
						+ (csvInfoRow == null ? 0 : csvInfoRow.size()));
			} catch (Exception e) {
				csvInfoRow = Collections.emptyMap();
				logWarn(METHOD_NAME, "ReaderCurrentCsvInfoBean.init() スキップ扱い");
			}
			csvInfoRow = canonicalizeCsvInfoMap(
					(csvInfoRow != null) ? csvInfoRow : Collections.emptyMap());

			logInfo(METHOD_NAME, "sortSeqs() 開始");
			List<List<Integer>> currentGroups = normalizeGroups(sortSeqs());
			logInfo(METHOD_NAME, "sortSeqs() 終了 currentGroups.size=" + currentGroups.size());

			boolean firstRun = !Files.exists(localSeqPath);
			List<List<Integer>> textGroups;

			logInfo(METHOD_NAME, "firstRun=" + firstRun + ", localSeqPathExists=" + Files.exists(localSeqPath));

			if (firstRun) {
				logInfo(METHOD_NAME, "初回実行のため seqListJson 作成");
				writeSeqListJson(localSeqPath, currentGroups);
				textGroups = Collections.emptyList();
			} else {
				logInfo(METHOD_NAME, "既存 seqListJson 読み込み開始");
				textGroups = normalizeGroups(readSeqListJson(localSeqPath));
				logInfo(METHOD_NAME, "既存 seqListJson 読み込み終了 textGroups.size=" + textGroups.size());
			}

			CsvBuildPlan plan;
			if (firstRun) {
				logInfo(METHOD_NAME, "初回実行向け plan 構築開始");
				CsvBuildPlan plans = new CsvBuildPlan();
				int i = 0;
				for (List<Integer> curr : currentGroups) {
					plans.newTargets.put(CSV_NEW_PREFIX + "-" + (i++), curr);
				}
				plan = plans;
				logInfo(METHOD_NAME, "初回実行向け plan 構築終了 newTargets.size=" + plan.newTargets.size());
			} else {
				logInfo(METHOD_NAME, "matchSeqCombPlan() 開始");
				plan = matchSeqCombPlan(textGroups, currentGroups, csvInfoRow);
				logInfo(METHOD_NAME, "matchSeqCombPlan() 終了 recreateByCsvKey.size="
						+ (plan == null ? 0 : plan.recreateByCsvKey.size())
						+ ", newTargets.size=" + (plan == null ? 0 : plan.newTargets.size()));
			}

			if (plan == null || (plan.recreateByCsvKey.isEmpty() && plan.newTargets.isEmpty())) {
				logInfo(METHOD_NAME, "処理対象なしのため管理ファイルのみ更新");
				upsertDataTeamList(localTeamPath, Collections.emptyList(), Collections.emptySet());
				writeSeqListJson(localSeqPath, currentGroups);

				String messageCd = MessageCdConst.MCD00014I_NO_MAP_DATA;
				String fillChar = "追加レコードがないため処理終了 (既存CSV数: " + csvInfoRow.size() + "件)";
				endLog(METHOD_NAME, messageCd, fillChar);
				return;
			}

			CsvArtifactResource csvArtifactResource;
			try {
				logInfo(METHOD_NAME, "helper.getData() 開始");
				csvArtifactResource = this.helper.getData();
				logInfo(METHOD_NAME, "helper.getData() 終了");
			} catch (Exception e) {
				logError(METHOD_NAME, "helper.getData() 失敗", e);
				throw (e instanceof IOException) ? (IOException) e : new IOException(e);
			}

			logInfo(METHOD_NAME, "buildWorkItems() 開始");
			List<CsvWorkItem> workItems = buildWorkItems(
					LOCAL_DIR,
					plan,
					csvArtifactResource,
					METHOD_NAME,
					false);
			logInfo(METHOD_NAME, "buildWorkItems() 終了 workItems.size=" + workItems.size());

			if (workItems.isEmpty()) {
				logInfo(METHOD_NAME, "workItems=0 のため管理ファイルのみ更新");
				upsertDataTeamList(localTeamPath, Collections.emptyList(), Collections.emptySet());
				writeSeqListJson(localSeqPath, currentGroups);

				String messageCd = MessageCdConst.MCD00014I_NO_MAP_DATA;
				String fillChar = "追加レコードがないため処理終了 (workItems=0)";
				endLog(METHOD_NAME, messageCd, fillChar);
				return;
			}

			int threads = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 2));
			logInfo(METHOD_NAME, "ExecutorService 作成 threads=" + threads);
			ExecutorService pool = Executors.newFixedThreadPool(threads);

			ProcessResult processResult;
			try {
				logInfo(METHOD_NAME, "processWorkItemsInChunks() 開始");
				processResult = processWorkItemsInChunks(
						workItems,
						csvArtifactResource,
						pool,
						LOCAL_DIR,
						true,
						null,
						null,
						METHOD_NAME);
				logInfo(METHOD_NAME, "processWorkItemsInChunks() 終了 success=" + processResult.successCount
						+ ", failed=" + processResult.failedCount
						+ ", skipped=" + processResult.skippedCount);
			} finally {
				logInfo(METHOD_NAME, "ExecutorService shutdown 開始");
				pool.shutdown();
				try {
					pool.awaitTermination(1, TimeUnit.MINUTES);
					logInfo(METHOD_NAME, "ExecutorService shutdown 完了");
				} catch (InterruptedException ignore) {
					Thread.currentThread().interrupt();
					logWarn(METHOD_NAME, "ExecutorService shutdown 中に interrupt");
				}
			}

			logInfo(METHOD_NAME, "registerCsvDetailManage() 開始 succeeded.size=" + processResult.succeeded.size());
			registerCsvDetailManage(processResult.succeeded, METHOD_NAME);
			logInfo(METHOD_NAME, "registerCsvDetailManage() 終了");

			logInfo(METHOD_NAME, "data_team_list 更新開始");
			upsertDataTeamList(localTeamPath, processResult.succeeded, processResult.failedRelativeKeys);
			logInfo(METHOD_NAME, "data_team_list 更新終了");

			logInfo(METHOD_NAME, "seqListJson 更新開始");
			writeSeqListJson(localSeqPath, currentGroups);
			logInfo(METHOD_NAME, "seqListJson 更新終了");

			if (processResult.failedCount > 0) {
				logWarn(METHOD_NAME, "失敗あり failedCount=" + processResult.failedCount);
			}

			logInfo(METHOD_NAME, "executeLocalOnly 正常終了");
			endLog(METHOD_NAME, null, null);

		} catch (IOException e) {
			logError(METHOD_NAME, "executeLocalOnly IOException", e);
			throw e;
		} catch (Exception e) {
			logError(METHOD_NAME, "executeLocalOnly 予期せぬ例外", e);
			throw (e instanceof IOException) ? (IOException) e : new IOException(e);
		}
	}

	private List<CsvWorkItem> buildWorkItems(
			Path localDir,
			CsvBuildPlan plan,
			CsvArtifactResource csvArtifactResource,
			String parentMethod,
			boolean s3Mode) {

		final String METHOD_NAME = "buildWorkItems";
		logInfo(METHOD_NAME, "開始 recreateByCsvKey.size="
				+ (plan == null ? 0 : plan.recreateByCsvKey.size())
				+ ", newTargets.size=" + (plan == null ? 0 : plan.newTargets.size())
				+ ", s3Mode=" + s3Mode
				+ ", localDir=" + localDir);

		List<CsvWorkItem> workItems = new ArrayList<>();

		for (Map.Entry<String, List<Integer>> entry : plan.recreateByCsvKey.entrySet()) {
			String relativeKey = canonicalizeCsvId(entry.getKey());
			List<Integer> ids = normalizeSeqList(entry.getValue());

			if (relativeKey.isBlank() || ids.isEmpty()) {
				logWarn(METHOD_NAME, "recreate skip relativeKey=" + shortKey(relativeKey)
						+ ", ids=" + summarizeIds(ids));
				continue;
			}

			workItems.add(new CsvWorkItem(relativeKey, ids));
			logInfo(METHOD_NAME, "recreate add relativeKey=" + shortKey(relativeKey)
					+ ", ids=" + summarizeIds(ids));
		}

		logInfo(METHOD_NAME, "resolveNewTargetsByFolder() 開始");
		Map<String, List<List<Integer>>> newTargetsByFolder = resolveNewTargetsByFolder(
				plan.newTargets,
				csvArtifactResource,
				parentMethod);
		logInfo(METHOD_NAME, "resolveNewTargetsByFolder() 終了 folderCount=" + newTargetsByFolder.size());

		if (!newTargetsByFolder.isEmpty()) {
			if (s3Mode) {
				logInfo(METHOD_NAME, "S3採番用 maxCsvNo 取得開始 folderCount=" + newTargetsByFolder.keySet().size());
				Map<String, Integer> s3MaxByFolder = getStatInfo.getMaxCsvNoByFolders(newTargetsByFolder.keySet());
				logInfo(METHOD_NAME, "S3採番用 maxCsvNo 取得終了 result.size=" + s3MaxByFolder.size());

				for (Map.Entry<String, List<List<Integer>>> e : newTargetsByFolder.entrySet()) {
					String folderName = canonicalizeFolderSegment(e.getKey());
					List<List<Integer>> groups = e.getValue();

					groups.sort(Comparator.comparingInt(ExportCsvService::minSeqOfIds));

					int maxOnS3 = s3MaxByFolder.getOrDefault(folderName, 0);
					int nextNo = maxOnS3 + 1;

					for (int i = 0; i < groups.size(); i++) {
						int csvNo = nextNo + i;
						String relativeKey = canonicalizeCsvId(
								joinS3Key(folderName, csvNo + BookMakersCommonConst.CSV));
						workItems.add(new CsvWorkItem(relativeKey, groups.get(i)));

						logInfo(METHOD_NAME, "new add(S3) relativeKey=" + shortKey(relativeKey)
								+ ", ids=" + summarizeIds(groups.get(i)));
					}
				}
			} else {
				for (Map.Entry<String, List<List<Integer>>> e : newTargetsByFolder.entrySet()) {
					String folderName = canonicalizeFolderSegment(e.getKey());
					List<List<Integer>> groups = e.getValue();

					groups.sort(Comparator.comparingInt(ExportCsvService::minSeqOfIds));

					int maxLocal = getMaxCsvNoFromLocal(localDir.resolve(folderName));
					int nextNo = maxLocal + 1;

					for (int i = 0; i < groups.size(); i++) {
						int csvNo = nextNo + i;
						String relativeKey = canonicalizeCsvId(
								joinS3Key(folderName, csvNo + BookMakersCommonConst.CSV));
						workItems.add(new CsvWorkItem(relativeKey, groups.get(i)));

						logInfo(METHOD_NAME, "new add(Local) relativeKey=" + shortKey(relativeKey)
								+ ", ids=" + summarizeIds(groups.get(i)));
					}
				}
			}
		}

		workItems.sort((a, b) -> compareCsvRelativeKey(a.getRelativeKey(), b.getRelativeKey()));
		logInfo(METHOD_NAME, "終了 workItems.size=" + workItems.size());
		return workItems;
	}

	/**
	 * 解決
	 * @param newTargets
	 * @param csvArtifactResource
	 * @param parentMethod
	 * @return
	 */
	private Map<String, List<List<Integer>>> resolveNewTargetsByFolder(
			Map<String, List<Integer>> newTargets,
			CsvArtifactResource csvArtifactResource,
			String parentMethod) {

		final String METHOD_NAME = "resolveNewTargetsByFolder";
		logInfo(METHOD_NAME, "開始 newTargets.size=" + (newTargets == null ? 0 : newTargets.size()));

		Map<String, List<List<Integer>>> newTargetsByFolder = new LinkedHashMap<>();
		if (newTargets == null || newTargets.isEmpty()) {
			logInfo(METHOD_NAME, "newTargets 空のため終了");
			return newTargetsByFolder;
		}

		for (Map.Entry<String, List<Integer>> entry : newTargets.entrySet()) {
			String tempKey = entry.getKey();
			List<Integer> ids = normalizeSeqList(entry.getValue());

			if (ids.isEmpty()) {
				logWarn(METHOD_NAME, "skip ids empty tempKey=" + tempKey);
				continue;
			}

			logInfo(METHOD_NAME, "preview fetch 開始 tempKey=" + tempKey + ", ids=" + summarizeIds(ids));
			List<CsvPreviewRow> preview = fetchPreview(ids, "resolveNewTargetsByFolder");
			logInfo(METHOD_NAME, "preview fetch 終了 tempKey=" + tempKey
					+ ", preview.size=" + (preview == null ? 0 : preview.size()));

			if (preview == null || preview.isEmpty()) {
				logWarn(METHOD_NAME, "skip preview empty tempKey=" + tempKey);
				continue;
			}

			String folderName = canonicalizeFolderSegment(resolveRoundFolderNamePreview(preview));
			logInfo(METHOD_NAME, "folder resolve tempKey=" + tempKey + ", folderName=" + folderName);

			newTargetsByFolder.computeIfAbsent(folderName, k -> new ArrayList<>()).add(ids);
		}

		logInfo(METHOD_NAME, "終了 folderCount=" + newTargetsByFolder.size());
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

		final String METHOD_NAME = "processWorkItemsInChunks";
		logInfo(METHOD_NAME, "開始 workItems.size=" + (workItems == null ? 0 : workItems.size())
				+ ", localMode=" + localMode
				+ ", chunkSize=" + Math.max(1, workChunkSize));

		ProcessResult processResult = new ProcessResult();
		if (workItems == null || workItems.isEmpty()) {
			logInfo(METHOD_NAME, "workItems 空のため終了");
			return processResult;
		}

		int chunkSize = Math.max(1, workChunkSize);

		for (int from = 0; from < workItems.size(); from += chunkSize) {
			int to = Math.min(from + chunkSize, workItems.size());
			List<CsvWorkItem> chunk = workItems.subList(from, to);

			logInfo(METHOD_NAME, "chunk start from=" + (from + 1)
					+ ", to=" + to
					+ ", total=" + workItems.size());

			List<CompletableFuture<CsvTaskResult>> futures = new ArrayList<>(chunk.size());

			for (CsvWorkItem item : chunk) {
				logInfo(METHOD_NAME, "future submit relativeKey=" + shortKey(item.getRelativeKey())
						+ ", ids=" + summarizeIds(item.getSeqIds()));

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
					logWarn(METHOD_NAME, "future result null");
					continue;
				}

				switch (taskResult.getStatus()) {
				case SUCCESS:
					processResult.successCount++;
					if (taskResult.getMeta() != null) {
						processResult.succeeded.add(taskResult.getMeta());
					}
					logInfo(METHOD_NAME, "chunk item success relativeKey=" + shortKey(taskResult.getRelativeKey()));
					break;
				case FAILED:
					processResult.failedCount++;
					if (taskResult.getRelativeKey() != null && !taskResult.getRelativeKey().isBlank()) {
						processResult.failedRelativeKeys.add(taskResult.getRelativeKey());
					}
					logWarn(METHOD_NAME, "chunk item failed relativeKey=" + shortKey(taskResult.getRelativeKey()));
					break;
				case SKIPPED:
				default:
					processResult.skippedCount++;
					logInfo(METHOD_NAME, "chunk item skipped relativeKey=" + shortKey(taskResult.getRelativeKey()));
					break;
				}
			}

			futures.clear();

			logInfo(METHOD_NAME, "chunk end from=" + (from + 1)
					+ ", to=" + to
					+ ", success=" + processResult.successCount
					+ ", failed=" + processResult.failedCount
					+ ", skipped=" + processResult.skippedCount);
		}

		logInfo(METHOD_NAME, "終了 success=" + processResult.successCount
				+ ", failed=" + processResult.failedCount
				+ ", skipped=" + processResult.skippedCount);

		return processResult;
	}

	/**
	 * workItemシングルプロセス
	 * @param item
	 * @param csvArtifactResource
	 * @param baseDir
	 * @param localMode
	 * @param bucket
	 * @param prefix
	 * @param parentMethod
	 * @return
	 */
	private CsvTaskResult processSingleWorkItem(
	        CsvWorkItem item,
	        CsvArtifactResource csvArtifactResource,
	        Path baseDir,
	        boolean localMode,
	        String bucket,
	        String prefix,
	        String parentMethod) {

	    final String METHOD_NAME = "processSingleWorkItem";
	    String step = "start";

	    logInfo(METHOD_NAME, "開始 relativeKey=" + shortKey(item.getRelativeKey())
	            + ", ids=" + summarizeIds(item.getSeqIds())
	            + ", localMode=" + localMode);

	    try {
	        step = "fetchAndFilter";
	        logInfo(METHOD_NAME, "fetchAndFilter() 開始 relativeKey=" + shortKey(item.getRelativeKey()));
	        List<DataEntity> result = fetchAndFilter(
	                item.getSeqIds(),
	                csvArtifactResource,
	                parentMethod,
	                "processSingleWorkItem: " + item.getRelativeKey());
	        logInfo(METHOD_NAME, "fetchAndFilter() 終了 relativeKey=" + shortKey(item.getRelativeKey())
	                + ", result.size=" + (result == null ? 0 : result.size()));

	        if (result == null || result.isEmpty()) {
	            logInfo(METHOD_NAME, "skip result empty relativeKey=" + shortKey(item.getRelativeKey()));
	            return CsvTaskResult.skipped(item.getRelativeKey());
	        }

	        step = "applyFutureFallbackToCsvRows";
	        String resolvedCategory = applyFutureFallbackToCsvRows(result, METHOD_NAME);

	        DataEntity row = findRowWithTeams(result);
	        String homeTeamName = safe(row.getHomeTeamName()).trim();
	        String awayTeamName = safe(row.getAwayTeamName()).trim();

	        if (homeTeamName.isEmpty()) {
	            homeTeamName = safe(firstDataValue(result, DataEntity::getHomeTeamName, false)).trim();
	        }
	        if (awayTeamName.isEmpty()) {
	            awayTeamName = safe(firstDataValue(result, DataEntity::getAwayTeamName, false)).trim();
	        }
	        if (resolvedCategory.isEmpty()) {
	            resolvedCategory = safe(firstDataValue(result, DataEntity::getDataCategory, true)).trim();
	        }

	        step = "buildCsvArtifact";
	        String filePath = baseDir.resolve(item.getRelativeKey()).toString();
	        logInfo(METHOD_NAME, "buildCsvArtifact() 開始 filePath=" + filePath);

	        CsvArtifact art = buildCsvArtifact(filePath, result, csvArtifactResource);

	        logInfo(METHOD_NAME, "buildCsvArtifact() 終了 hasArtifact=" + (art != null)
	                + ", contentSize=" + ((art == null || art.getContent() == null) ? 0 : art.getContent().size()));

	        if (art == null || art.getContent() == null || art.getContent().isEmpty()) {
	            logInfo(METHOD_NAME, "skip artifact empty relativeKey=" + shortKey(item.getRelativeKey()));
	            return CsvTaskResult.skipped(item.getRelativeKey());
	        }

	        CsvOutputMeta meta = new CsvOutputMeta(
	                item.getRelativeKey(),
	                resolvedCategory,
	                homeTeamName,
	                awayTeamName);

	        step = "writeLocalCsv";
	        logInfo(METHOD_NAME, "writeLocalCsv() 開始 filePath=" + art.getFilePath());
	        writeLocalCsv(art);
	        logInfo(METHOD_NAME, "writeLocalCsv() 終了 filePath=" + art.getFilePath());

	        step = "verifyLocalCsvExists";
	        Path written = Paths.get(art.getFilePath());
	        if (!Files.exists(written)) {
	            throw new IllegalStateException("CSV file was not created: " + written);
	        }

	        if (!localMode) {
	            step = "putLocalFileToFinal";
	            logInfo(METHOD_NAME, "putLocalFileToFinal() 開始 relativeKey=" + shortKey(item.getRelativeKey()));
	            putLocalFileToFinal(bucket, prefix, baseDir, Paths.get(art.getFilePath()));
	            logInfo(METHOD_NAME, "putLocalFileToFinal() 終了 relativeKey=" + shortKey(item.getRelativeKey()));
	        }

	        logInfo(METHOD_NAME, "成功 relativeKey=" + shortKey(item.getRelativeKey())
	                + ", dataCategory=" + meta.getDataCategory()
	                + ", home=" + meta.getHomeTeamName()
	                + ", away=" + meta.getAwayTeamName());

	        return CsvTaskResult.success(item.getRelativeKey(), meta);

	    } catch (Exception ex) {
	        logError(METHOD_NAME,
	                "CSV作成処理失敗 step=" + step
	                + ", relativeKey=" + shortKey(item.getRelativeKey())
	                + ", fullPath=" + baseDir.resolve(item.getRelativeKey()),
	                ex);
	        ex.printStackTrace();
	        return CsvTaskResult.failed(item.getRelativeKey());
	    }
	}

	private String applyFutureFallbackToCsvRows(List<DataEntity> result, String parentMethod) {
	    final String METHOD_NAME = "applyFutureFallbackToCsvRows";

	    if (result == null || result.isEmpty()) {
	        logInfo(METHOD_NAME, "result empty");
	        return "";
	    }

	    String currentCategory = safe(firstDataValue(result, DataEntity::getDataCategory, true)).trim();
	    String canonicalHome = safe(firstDataValue(result, DataEntity::getHomeTeamName, false)).trim();
	    String canonicalAway = safe(firstDataValue(result, DataEntity::getAwayTeamName, false)).trim();

	    String resolvedCategory = resolveCategoryWithFutureFallback(
	            currentCategory,
	            canonicalHome,
	            canonicalAway,
	            parentMethod);

	    int categoryFilled = 0;
	    int homeFilled = 0;
	    int awayFilled = 0;

	    for (DataEntity d : result) {
	        if (d == null) {
	            continue;
	        }

	        String beforeCategory = safe(d.getDataCategory()).trim();
	        if (!resolvedCategory.isEmpty() && needsCategoryBackfill(beforeCategory)) {
	            d.setDataCategory(resolvedCategory);
	            categoryFilled++;
	        }

	        if (isBlank(d.getHomeTeamName()) && !canonicalHome.isEmpty()) {
	            d.setHomeTeamName(canonicalHome);
	            homeFilled++;
	        }

	        if (isBlank(d.getAwayTeamName()) && !canonicalAway.isEmpty()) {
	            d.setAwayTeamName(canonicalAway);
	            awayFilled++;
	        }
	    }

	    logInfo(METHOD_NAME,
	            "補完完了 categoryFilled=" + categoryFilled
	            + ", homeFilled=" + homeFilled
	            + ", awayFilled=" + awayFilled
	            + ", resolvedCategory=" + resolvedCategory
	            + ", homeTeamName=" + canonicalHome
	            + ", awayTeamName=" + canonicalAway);

	    return resolvedCategory;
	}

	private void registerCsvDetailManage(
			List<CsvOutputMeta> succeeded,
			String parentMethod) {

		final String METHOD_NAME = "registerCsvDetailManage";
		logInfo(METHOD_NAME, "開始 succeeded.size=" + (succeeded == null ? 0 : succeeded.size()));

		if (succeeded == null || succeeded.isEmpty()) {
			logInfo(METHOD_NAME, "対象なしのため終了");
			return;
		}

		int index = 0;
		for (CsvOutputMeta meta : succeeded) {
			index++;

			if (meta == null) {
				logWarn(METHOD_NAME, "meta null をスキップ index=" + index);
				continue;
			}

			String csvId = canonicalizeCsvId(meta.getRelativeCsvKey());
			String dataCategory = safe(meta.getDataCategory()).trim();
			String home = safe(meta.getHomeTeamName()).trim();
			String away = safe(meta.getAwayTeamName()).trim();

			if (csvId.isBlank()) {
				logWarn(METHOD_NAME, "csvId blank をスキップ index=" + index);
				continue;
			}

			String season = resolveSeasonSafely(csvId, dataCategory);

			try {
				upsertCsvDetailManage(csvId, dataCategory, season, home, away, parentMethod);
			} catch (Exception ex) {
				logError(METHOD_NAME, "csv_detail_manage 更新失敗 index=" + index
						+ ", csvId=" + shortKey(csvId), ex);
				throw ex;
			}
		}

		logInfo(METHOD_NAME, "終了");
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
		String normalizedCsvId = canonicalizeCsvId(csvId);
		if (normalizedCsvId.isBlank()) {
			return new String[] { "", "" };
		}

		String folder = parentPath(normalizedCsvId);
		if (folder.isBlank()) {
			return new String[] { "", "" };
		}

		String lastFolder = folder;
		int slash = lastFolder.lastIndexOf('/');
		if (slash >= 0) {
			lastFolder = lastFolder.substring(slash + 1);
		}

		lastFolder = lastFolder.trim();
		int roundIdx = lastFolder.lastIndexOf("-ラウンド");
		String base = (roundIdx >= 0) ? lastFolder.substring(0, roundIdx) : lastFolder;

		int firstHyphen = base.indexOf('-');
		if (firstHyphen < 0) {
			return new String[] { "", "" };
		}

		String country = base.substring(0, firstHyphen).trim();
		String league = base.substring(firstHyphen + 1).trim();

		return new String[] { country, league };
	}

	/**
	 * JSON作成
	 * @param out
	 * @param groups
	 * @throws IOException
	 */
	private void writeSeqListJson(Path out, List<List<Integer>> groups) throws IOException {
		final String METHOD_NAME = "writeSeqListJson";
		logInfo(METHOD_NAME, "開始 path=" + out + ", groups.size=" + (groups == null ? 0 : groups.size()));

		String json = SEQ_JSON.writeValueAsString(groups);
		Files.writeString(out, json, StandardCharsets.UTF_8,
				StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

		logInfo(METHOD_NAME, "終了 path=" + out + ", json.length=" + json.length());
	}

	/**
	 * readJson
	 * @param path
	 * @return
	 */
	private List<List<Integer>> readSeqListJson(Path path) {
		final String METHOD_NAME = "readSeqListJson";
		logInfo(METHOD_NAME, "開始 path=" + path);

		if (!Files.exists(path)) {
			logInfo(METHOD_NAME, "ファイル不存在 path=" + path);
			return Collections.emptyList();
		}
		try {
			String json = Files.readString(path, StandardCharsets.UTF_8).trim();
			logInfo(METHOD_NAME, "read 完了 path=" + path + ", length=" + json.length());

			if (json.isEmpty()) {
				logInfo(METHOD_NAME, "空ファイル path=" + path);
				return Collections.emptyList();
			}

			if (json.startsWith("[")) {
				List<List<Integer>> result = SEQ_JSON.readValue(json,
						new com.fasterxml.jackson.core.type.TypeReference<List<List<Integer>>>() {
						});
				logInfo(METHOD_NAME, "JSON形式読込完了 groups.size=" + result.size());
				return result;
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

			logInfo(METHOD_NAME, "旧形式読込完了 groups.size=" + result.size());
			return result;

		} catch (Exception e) {
			logWarn(METHOD_NAME, "seqList.txt の読み込みに失敗しました path=" + path);
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

	/**
	 * 連番をソートする
	 * @return
	 */
	/**
	 * 連番をソートする
	 * 改善版:
	 * - findGroupTargetsPage() は home/away 単位
	 * - 同一グループに対する findSeqListByGroup() の重複実行を防止
	 */
	private List<List<Integer>> sortSeqs() {
		final String METHOD_NAME = "sortSeqs";
		logInfo(METHOD_NAME, "開始 GROUP_PAGE_SIZE=" + GROUP_PAGE_SIZE);

		List<List<Integer>> result = new ArrayList<>();

		int offset = 0;
		int pageNo = 1;

		// 同一グループの重複SQL実行防止
		Map<String, List<Integer>> seqCache = new LinkedHashMap<>();
		Set<String> processedGroupKeys = new LinkedHashSet<>();

		while (true) {
			logInfo(METHOD_NAME, "findGroupTargetsPage() 開始 offset=" + offset + ", pageNo=" + pageNo);

			List<SeqWithKey> page = this.bookCsvDataRepository.findGroupTargetsPage(GROUP_PAGE_SIZE, offset);

			if (page == null || page.isEmpty()) {
				logInfo(METHOD_NAME, "page empty のため終了 offset=" + offset + ", pageNo=" + pageNo);
				break;
			}

			logInfo(METHOD_NAME, "page fetch 完了 offset=" + offset + ", pageNo=" + pageNo + ", size=" + page.size());

			for (SeqWithKey r : page) {
				if (r == null) {
					logWarn(METHOD_NAME, "SeqWithKey null をスキップ");
					continue;
				}

				String home = safe(r.getHomeTeamName()).trim();
				String away = safe(r.getAwayTeamName()).trim();
				String matchId = safe(r.getMatchId()).trim();
				String dataCategory = safe(r.getDataCategory()).trim();

				if (home.isEmpty() && away.isEmpty()) {
					logWarn(METHOD_NAME, "home/away empty をスキップ");
					continue;
				}

				String groupKey = String.join("\u0001",
						home,
						away,
						matchId,
						dataCategory);

				// 念のため重複防止
				if (!processedGroupKeys.add(groupKey)) {
					logInfo(METHOD_NAME, "重複グループをスキップ home=" + home
							+ ", away=" + away
							+ ", matchId=" + matchId);
					continue;
				}

				List<Integer> seqs = seqCache.get(groupKey);
				if (seqs == null) {
					logInfo(METHOD_NAME, "findSeqListByGroup() 開始 home=" + home
							+ ", away=" + away
							+ ", matchId=" + matchId
							+ ", dataCategory=" + dataCategory);

					seqs = normalizeSeqList(
							this.bookCsvDataRepository.findSeqListByGroup(
									home,
									away,
									matchId,
									dataCategory));

					logInfo(METHOD_NAME, "findSeqListByGroup() 終了 home=" + home
							+ ", away=" + away
							+ ", matchId=" + matchId
							+ ", " + summarizeIds(seqs));

					seqCache.put(groupKey, seqs);
				}

				if (seqs.isEmpty()) {
					logWarn(METHOD_NAME, "seq empty のためスキップ home=" + home
							+ ", away=" + away
							+ ", matchId=" + matchId);
					continue;
				}

				result.add(seqs);

				if (result.size() % 100 == 0) {
				    logInfo(METHOD_NAME, "進捗 groupCount=" + result.size()
				            + ", offset=" + offset
				            + ", pageNo=" + pageNo);
				}
			}

			offset += page.size();
			pageNo++;
		}

		logInfo(METHOD_NAME, "終了 result.groupCount=" + result.size());
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

		final String METHOD_NAME = "fetchAndFilter";
		logInfo(METHOD_NAME, "開始 label=" + label + ", ids=" + summarizeIds(ids));

		if (ids == null || ids.isEmpty()) {
			logInfo(METHOD_NAME, "ids empty のため null返却 label=" + label);
			return null;
		}

		List<DataEntity> result;
		try {
			logInfo(METHOD_NAME, "findByData() 開始 label=" + label + ", ids=" + summarizeIds(ids));
			result = this.bookCsvDataRepository.findByData(ids);
			logInfo(METHOD_NAME, "findByData() 終了 label=" + label
					+ ", result.size=" + (result == null ? 0 : result.size()));
		} catch (Exception e) {
			logError(METHOD_NAME, "findByData() 失敗 label=" + label + ", ids=" + summarizeIds(ids), e);
			throw e;
		}

		if (result == null || result.isEmpty()) {
		    logInfo(METHOD_NAME, "findByData() 結果なしのため null返却 label=" + label);
		    return null;
		}

		boolean condition = this.helper.csvCondition(result, csvArtifactResource);
		logInfo(METHOD_NAME, "csvCondition 判定 label=" + label + ", result=" + condition);

		if (!condition) {
			logInfo(METHOD_NAME, "csvCondition=false のため null返却 label=" + label);
			return null;
		}

		result = this.helper.abnormalChk(result);
		logInfo(METHOD_NAME, "abnormalChk() 後 label=" + label
				+ ", result.size=" + (result == null ? 0 : result.size()));

		if (result == null || result.isEmpty()) {
			logInfo(METHOD_NAME, "abnormalChk() 後 empty のため null返却 label=" + label);
			return null;
		}

		result = new ArrayList<>(result);
		logInfo(METHOD_NAME, "ArrayList copy 完了 label=" + label + ", result.size=" + result.size());

		backfillScores(result);
		logInfo(METHOD_NAME, "backfillScores() 完了 label=" + label);

		applyCanonicalMatchKeys(result);
		logInfo(METHOD_NAME, "applyCanonicalMatchKeys() 完了 label=" + label);

		logInfo(METHOD_NAME, "終了 label=" + label + ", final.size=" + result.size());
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

		Map<String, List<Integer>> normalizedCsvInfo = canonicalizeCsvInfoMap(csvInfoRow);

		for (Map.Entry<String, List<Integer>> e : normalizedCsvInfo.entrySet()) {
			String csvKey = canonicalizeCsvId(e.getKey());
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
				plan.recreateByCsvKey.put(canonicalizeCsvId(csvKey), dbGroup);
			} else {
				plan.newTargets.put(CSV_NEW_PREFIX + "-" + min, dbGroup);
			}
		}

		return plan;
	}

	/**
	 * 軽量版
	 * @param ids
	 * @param label
	 * @return
	 */
	private List<CsvPreviewRow> fetchPreview(List<Integer> ids, String label) {
		final String METHOD_NAME = "fetchPreview";
		logInfo(METHOD_NAME, "開始 label=" + label + ", ids=" + summarizeIds(ids));

		if (ids == null || ids.isEmpty()) {
			return Collections.emptyList();
		}

		List<CsvPreviewRow> result = this.bookCsvDataRepository.findPreviewByData(ids);
		logInfo(METHOD_NAME, "findPreviewByData() 終了 label=" + label
				+ ", result.size=" + (result == null ? 0 : result.size()));

		if (result == null || result.isEmpty()) {
			return Collections.emptyList();
		}

		result = new ArrayList<>(result);
		backfillPreviewScores(result);
		applyCanonicalPreviewMatchKeys(result);

		logInfo(METHOD_NAME, "終了 label=" + label + ", final.size=" + result.size());
		return result;
	}

	/**
	 * folder 判定用オーバーロード
	 * @param group
	 * @return
	 */
	private String resolveRoundFolderNamePreview(List<CsvPreviewRow> group) {
		final String METHOD_NAME = "resolveRoundFolderNamePreview";

		CsvPreviewRow row = findPreviewRowWithTeams(group);

		String homeTeamName = safe(row.getHomeTeamName()).trim();
		String awayTeamName = safe(row.getAwayTeamName()).trim();

		String resolvedCategory = resolveCategoryWithFutureFallback(
				safe(row.getDataCategory()).trim(),
				homeTeamName,
				awayTeamName,
				METHOD_NAME);

		String folderBase = csvFileNameService.makeFolderNameFromTeams(
				homeTeamName,
				awayTeamName);

		String roundName = safe(csvFileNameService.extractRoundName(resolvedCategory)).trim();
		if (roundName.isEmpty()) {
			roundName = "unknown";
		}

		return folderBase + "-" + roundName;
	}

	private String resolveCategoryWithFutureFallback(
			String currentCategory,
			String homeTeamName,
			String awayTeamName,
			String parentMethod) {

		final String METHOD_NAME = "resolveCategoryWithFutureFallback";

		String normalizedCategory = safe(currentCategory).trim();
		if (isUsableCategory(normalizedCategory)) {
			return normalizedCategory;
		}

		if (isBlank(homeTeamName) || isBlank(awayTeamName)) {
			logWarn(METHOD_NAME,
					"future_master fallback skip: team name empty"
					+ ", homeTeamName=" + homeTeamName
					+ ", awayTeamName=" + awayTeamName
					+ ", currentCategory=" + normalizedCategory);
			return normalizedCategory;
		}

		try {
			String futureCategory = safe(
					this.futureMasterRepository.findLatestGameTeamCategoryByTeams(
							homeTeamName,
							awayTeamName))
					.trim();

			if (!isUsableCategory(futureCategory)) {
				// 念のためホーム/アウェイ逆順でも探す
				futureCategory = safe(
						this.futureMasterRepository.findLatestGameTeamCategoryByTeams(
								awayTeamName,
								homeTeamName))
						.trim();
			}

			if (isUsableCategory(futureCategory)) {
				logInfo(METHOD_NAME,
						"future_master fallback success"
						+ ", homeTeamName=" + homeTeamName
						+ ", awayTeamName=" + awayTeamName
						+ ", category=" + futureCategory);
				return futureCategory;
			}

			logWarn(METHOD_NAME,
					"future_master fallback not found"
					+ ", homeTeamName=" + homeTeamName
					+ ", awayTeamName=" + awayTeamName
					+ ", currentCategory=" + normalizedCategory);

			return normalizedCategory;

		} catch (Exception e) {
			logError(METHOD_NAME,
					"future_master fallback failed"
					+ ", homeTeamName=" + homeTeamName
					+ ", awayTeamName=" + awayTeamName
					+ ", currentCategory=" + normalizedCategory,
					e);
			return normalizedCategory;
		}
	}

	private boolean isUsableCategory(String category) {
		String normalized = safe(category).trim();
		if (normalized.isEmpty()) {
			return false;
		}
		if ("unknown".equalsIgnoreCase(normalized)) {
			return false;
		}

		String roundName = safe(this.csvFileNameService.extractRoundName(normalized)).trim();
		if (roundName.isEmpty()) {
			return false;
		}
		if ("unknown".equalsIgnoreCase(roundName)) {
			return false;
		}

		return true;
	}

	private static CsvPreviewRow findPreviewRowWithTeams(List<CsvPreviewRow> list) {
		for (CsvPreviewRow d : list) {
			String home = safe(d.getHomeTeamName()).trim();
			String away = safe(d.getAwayTeamName()).trim();
			if (!home.isEmpty() || !away.isEmpty()) {
				return d;
			}
		}
		return list.get(0);
	}

	private static void backfillPreviewScores(List<CsvPreviewRow> list) {
		if (list == null || list.isEmpty()) {
			return;
		}

		list.sort(Comparator
				.comparing((CsvPreviewRow d) -> d.getRecordTime() == null ? "" : d.getRecordTime())
				.thenComparingInt(d -> d.getSeq() == null ? Integer.MAX_VALUE : d.getSeq()));

		String lastHome = null;
		String lastAway = null;
		for (CsvPreviewRow d : list) {
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

	private static void applyCanonicalPreviewMatchKeys(List<CsvPreviewRow> group) {
		if (group == null || group.isEmpty()) {
			return;
		}

		String canonicalCategory = firstPreviewValue(group, CsvPreviewRow::getDataCategory, true);
		String canonicalHome = firstPreviewValue(group, CsvPreviewRow::getHomeTeamName, true);
		String canonicalAway = firstPreviewValue(group, CsvPreviewRow::getAwayTeamName, true);

		for (CsvPreviewRow d : group) {
			if ((isBlank(d.getDataCategory()) || !hasRound(d.getDataCategory())) && !canonicalCategory.isBlank()) {
				d.setDataCategory(canonicalCategory);
			}
			if (isBlank(d.getHomeTeamName()) && !canonicalHome.isBlank()) {
				d.setHomeTeamName(canonicalHome);
			}
			if (isBlank(d.getAwayTeamName()) && !canonicalAway.isBlank()) {
				d.setAwayTeamName(canonicalAway);
			}
		}
	}

	private static String firstPreviewValue(
			List<CsvPreviewRow> group,
			java.util.function.Function<CsvPreviewRow, String> getter,
			boolean preferRoundRow) {

		if (group == null || group.isEmpty()) {
			return "";
		}

		if (preferRoundRow) {
			for (CsvPreviewRow d : group) {
				if (!hasRound(d.getDataCategory())) {
					continue;
				}
				String value = safe(getter.apply(d)).trim();
				if (!value.isEmpty()) {
					return value;
				}
			}
		}

		for (CsvPreviewRow d : group) {
			String value = safe(getter.apply(d)).trim();
			if (!value.isEmpty()) {
				return value;
			}
		}

		return "";
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

	/**
	 * upsertDataTeamList
	 * @param out
	 * @param succeeded
	 * @param failedRelativeKeys
	 * @throws IOException
	 */
	private void upsertDataTeamList(
			Path out,
			List<CsvOutputMeta> succeeded,
			Set<String> failedRelativeKeys) throws IOException {

		final String METHOD_NAME = "upsertDataTeamList";
		logInfo(METHOD_NAME, "開始 path=" + out
				+ ", succeeded.size=" + (succeeded == null ? 0 : succeeded.size())
				+ ", failedRelativeKeys.size=" + (failedRelativeKeys == null ? 0 : failedRelativeKeys.size()));

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
				String csvKey = canonicalizeCsvId(parts[0].trim());
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
				String normalizedKey = canonicalizeCsvId(csvKey);
				if (!normalizedKey.isBlank()) {
					csvKeyToLine.remove(normalizedKey);
				}
			}
		}

		if (succeeded != null) {
			for (CsvOutputMeta meta : succeeded) {
				if (meta == null) {
					continue;
				}

				String csvKey = canonicalizeCsvId(meta.getRelativeCsvKey());
				if (csvKey.isBlank()) {
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

				String desc = !dataCategory.isEmpty() ? dataCategory + " - " + vsPart : vsPart;
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

		logInfo(METHOD_NAME, "終了 path=" + out + ", outLines.size=" + outLines.size());
	}

	private Map<String, List<Integer>> canonicalizeCsvInfoMap(Map<String, List<Integer>> src) {
		Map<String, List<Integer>> result = new LinkedHashMap<>();
		if (src == null || src.isEmpty()) {
			return result;
		}

		for (Map.Entry<String, List<Integer>> e : src.entrySet()) {
			String normalizedKey = canonicalizeCsvId(e.getKey());
			List<Integer> normalizedSeqs = normalizeSeqListStatic(e.getValue());

			if (normalizedKey.isBlank() || normalizedSeqs.isEmpty()) {
				continue;
			}

			List<Integer> merged = new ArrayList<>();
			if (result.containsKey(normalizedKey)) {
				merged.addAll(result.get(normalizedKey));
			}
			merged.addAll(normalizedSeqs);

			result.put(normalizedKey, normalizeSeqListStatic(merged));
		}

		return result;
	}

	private String canonicalizeCsvId(String rawCsvId) {
		if (rawCsvId == null) {
			return "";
		}

		String value = Normalizer.normalize(rawCsvId, Normalizer.Form.NFKC)
				.trim()
				.replace('\\', '/');

		while (value.startsWith("/")) {
			value = value.substring(1);
		}
		value = value.replaceAll("/+", "/");

		if (value.isEmpty()) {
			return "";
		}

		String[] parts = value.split("/");
		List<String> normalizedParts = new ArrayList<>();

		for (int i = 0; i < parts.length; i++) {
			String part = safe(parts[i]).trim();
			if (part.isEmpty()) {
				continue;
			}

			if (i < parts.length - 1) {
				normalizedParts.add(canonicalizeFolderSegment(part));
			} else {
				normalizedParts.add(part);
			}
		}

		return String.join("/", normalizedParts);
	}

	private String canonicalizeFolderSegment(String segment) {
		String s = Normalizer.normalize(safe(segment), Normalizer.Form.NFKC).trim();
		if (s.isEmpty()) {
			return "";
		}

		s = s.replaceAll("\\s*:\\s*", "-");
		s = s.replaceAll("\\s*-\\s*", "-");
		s = s.replaceAll("-{2,}", "-");

		return s;
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

	private static void applyCanonicalMatchKeys(List<DataEntity> group) {
		if (group == null || group.isEmpty()) {
			return;
		}

		String canonicalCategory = firstDataValue(group, DataEntity::getDataCategory, true);
		String canonicalHome = firstDataValue(group, DataEntity::getHomeTeamName, true);
		String canonicalAway = firstDataValue(group, DataEntity::getAwayTeamName, true);
		String canonicalMatchId = firstDataValue(group, DataEntity::getMatchId, true);

		for (DataEntity d : group) {
			if ((isBlank(d.getDataCategory()) || !hasRound(d.getDataCategory())) && !canonicalCategory.isBlank()) {
				d.setDataCategory(canonicalCategory);
			}
			if (isBlank(d.getHomeTeamName()) && !canonicalHome.isBlank()) {
				d.setHomeTeamName(canonicalHome);
			}
			if (isBlank(d.getAwayTeamName()) && !canonicalAway.isBlank()) {
				d.setAwayTeamName(canonicalAway);
			}
			if (isBlank(d.getMatchId()) && !canonicalMatchId.isBlank()) {
				d.setMatchId(canonicalMatchId);
			}
		}
	}

	private static String firstDataValue(
			List<DataEntity> group,
			java.util.function.Function<DataEntity, String> getter,
			boolean preferRoundRow) {

		if (group == null || group.isEmpty()) {
			return "";
		}

		if (preferRoundRow) {
			for (DataEntity d : group) {
				if (!hasRound(d.getDataCategory())) {
					continue;
				}
				String value = safe(getter.apply(d)).trim();
				if (!value.isEmpty()) {
					return value;
				}
			}
		}

		for (DataEntity d : group) {
			String value = safe(getter.apply(d)).trim();
			if (!value.isEmpty()) {
				return value;
			}
		}

		return "";
	}

	private boolean needsCategoryBackfill(String category) {
	    return !isUsableCategory(category);
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

	/**
	 * putManageFilesEvenIfNoCsv
	 * @param statsBucket
	 * @param prefix
	 * @param baseDir
	 * @param localSeqPath
	 * @param localTeamPath
	 * @param currentGroups
	 * @throws IOException
	 */
	private void putManageFilesEvenIfNoCsv(
			String statsBucket,
			String prefix,
			Path baseDir,
			Path localSeqPath,
			Path localTeamPath,
			List<List<Integer>> currentGroups) throws IOException {

		final String METHOD_NAME = "putManageFilesEvenIfNoCsv";
		logInfo(METHOD_NAME, "開始 statsBucket=" + statsBucket
				+ ", prefix=" + prefix
				+ ", currentGroups.size=" + (currentGroups == null ? 0 : currentGroups.size()));

		upsertDataTeamList(localTeamPath, Collections.emptyList(), Collections.emptySet());
		logInfo(METHOD_NAME, "data_team_list 更新完了");
		fileExistsService.uploadDataTeamListIfExists(statsBucket, prefix);
		logInfo(METHOD_NAME, "data_team_list PUT完了");

		writeSeqListJson(localSeqPath, currentGroups);
		logInfo(METHOD_NAME, "seqListJson 更新完了");
		fileExistsService.uploadSeqListIfExists(statsBucket, prefix);
		logInfo(METHOD_NAME, "seqListJson PUT完了");

		logInfo(METHOD_NAME, "終了");
	}

	private void endLog(String method, String messageCd, String fillChar) {
		if (messageCd != null && fillChar != null) {
			this.manageLoggerComponent.debugInfoLog(PROJECT_NAME, CLASS_NAME, method, messageCd, fillChar);
		}
		this.manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, method, "end");
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

	private void logInfo(String method, String message) {
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, method,
				MessageCdConst.MCD00099I_LOG, message);
	}

	private void logWarn(String method, String message) {
		this.manageLoggerComponent.debugWarnLog(
				PROJECT_NAME, CLASS_NAME, method,
				MessageCdConst.MCD00099I_LOG, message);
	}

	private void logError(String method, String message, Exception e) {
		this.manageLoggerComponent.debugErrorLog(
				PROJECT_NAME, CLASS_NAME, method,
				MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION, e, message);
	}

	private static String summarizeIds(List<Integer> ids) {
		if (ids == null || ids.isEmpty()) {
			return "size=0";
		}
		Integer first = ids.get(0);
		Integer last = ids.get(ids.size() - 1);
		return "size=" + ids.size() + ", first=" + first + ", last=" + last;
	}

	private static String shortKey(String key) {
		if (key == null) {
			return "(null)";
		}
		if (key.length() <= 120) {
			return key;
		}
		return key.substring(0, 120) + "...";
	}

}
