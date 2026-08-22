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
import dev.common.logger.ManageLoggerComponent;
import dev.common.s3.S3Operator;
import dev.common.util.ExecuteMainUtil;
/**
 * StatデータCSV出力ロジック（チャンク処理版）
 */
@Component
public class ExportCsvService {
	/** プロジェクト名 */
	private static final String PROJECT_NAME = ExportCsvService.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();
	/** クラス名 */
	private static final String CLASS_NAME = ExportCsvService.class.getName();
	/** 新規CSVグループの一時キーに付与するプレフィックス */
	private static final String CSV_NEW_PREFIX = "mk";
	/** seqList.txt（JSON形式）の読み書き用ObjectMapper */
	private static final com.fasterxml.jackson.databind.ObjectMapper SEQ_JSON = new com.fasterxml.jackson.databind.ObjectMapper();
	/** dataCategoryに「ラウンドN」が含まれているかを判定する正規表現 */
	private static final Pattern ROUND_TOKEN = Pattern.compile("ラウンド\\s*[0-9０-９]+");
	/** CSV出力ファイル名（末尾が数値.csv）から通し番号を取り出す正規表現 */
	private static final Pattern CSV_NO_PATTERN = Pattern.compile("(^|.*/)(\\d+)\\.csv$", Pattern.CASE_INSENSITIVE);
	/** 試合時間（times）が「ハーフタイム」であることを示す値 */
	private static final String TIMES_HALFTIME = "ハーフタイム";
	/** 試合時間（times）が「終了済」であることを示す値 */
	private static final String TIMES_FINISHED = "終了済";
	/** trueの場合、S3を使わずローカルのみでCSVを生成する */
	@Value("${exportcsv.local-only:false}")
	private boolean localOnly;
	/** S3出力時に付与する最終プレフィックス */
	@Value("${exportcsv.final-prefix:}")
	private String finalPrefix;
	/** 処理対象が0件でも管理ファイル（seqList/data_team_list）を必ずPUTするか */
	@Value("${exportcsv.always-put-manage-files:true}")
	private boolean alwaysPutManageFiles;
	/** workItemsを何件ずつのチャンクに分けて並列処理するか */
	@Value("${exportcsv.work-chunk-size:20}")
	private int workChunkSize;
	/** DBから対象グループを取得する際の1ページあたりの件数 */
	private static final int GROUP_PAGE_SIZE = 300;
	@Autowired
	private FileExistsService fileExistsService;
	@Autowired
	private CsvFileNameService csvFileNameService;
	@Autowired
	private S3Operator s3Operator;
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
	 * 実行メソッド。
	 * S3運用モード（localOnly=false）を前提に、DB上の最新グループと既存CSVレジストリ（csvInfoRow、
	 * 実際に存在するCSVの内容から取得）を必ず突き合わせて新規/再作成対象を判定し、CSVを生成してS3にPUTする。
	 * seqList.txtが存在しない場合（firstRun=true）でも、既存CSVの実体（csvInfoRow）は無視せず、
	 * 常に同じ突き合わせロジック（{@link #matchSeqCombPlan}）を通す。これにより、
	 * seqList.txtだけが誤って消えたケースでも既存CSVを重複生成しない。
	 * localOnly=true の場合は {@link #executeLocalOnly(Path)} に処理を委譲する。
	 *
	 * @throws IOException 管理ファイルの入出力に失敗した場合
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
		List<List<String>> currentGroups = normalizeGroups(sortSeqs());
		logInfo(METHOD_NAME, "sortSeqs() 終了 currentGroups.size=" + currentGroups.size());
		boolean firstRun = !seqExists || !Files.exists(localSeqPath);
		logInfo(METHOD_NAME, "firstRun判定 result=" + firstRun + ", localSeqPathExists=" + Files.exists(localSeqPath));
		// ※ firstRunはあくまで「参考情報（textGroupsを読むかどうか）」であり、
		//   plan構築を分岐させる材料にはしない。plan構築は必ずcsvInfoRow（実CSVの実体）を
		//   突き合わせて行う（下記 matchSeqCombPlan 呼び出し）。
		List<List<String>> textGroups = firstRun
				? Collections.emptyList()
				: normalizeGroups(readSeqListJson(localSeqPath));
		if (!firstRun) {
			logInfo(METHOD_NAME, "既存 seqListJson 読み込み完了 textGroups.size=" + textGroups.size());
		}
		Map<String, List<String>> csvInfoRow;
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
		// ★ firstRunであっても必ずcsvInfoRow（実CSVの実体）と突き合わせる。
		//   真にバケットが空の初回実行ではcsvInfoRowも空になるため、結果的に全件が
		//   newTargetsになり、従来のfirstRun専用「全部新規」分岐と同じ結果になる。
		//   一方、seqList.txtだけが誤って消えたケース（CSVは実在する）では、
		//   csvInfoRowにより既存CSVが正しく認識され、重複生成を防げる。
		logInfo(METHOD_NAME, "matchSeqCombPlan() 開始 textGroups.size=" + textGroups.size()
				+ ", currentGroups.size=" + currentGroups.size()
				+ ", csvInfoRow.size=" + csvInfoRow.size());
		CsvBuildPlan plan = matchSeqCombPlan(textGroups, currentGroups, csvInfoRow);
		logInfo(METHOD_NAME, "matchSeqCombPlan() 終了 recreateByCsvKey.size="
				+ (plan == null ? 0 : plan.recreateByCsvKey.size())
				+ ", newTargets.size=" + (plan == null ? 0 : plan.newTargets.size()));
		if (plan == null || (plan.recreateByCsvKey.isEmpty() && plan.newTargets.isEmpty())) {
			logInfo(METHOD_NAME, "処理対象なし plan空");
			if (alwaysPutManageFiles) {
				logInfo(METHOD_NAME, "alwaysPutManageFiles=true のため管理ファイルPUT開始");
				putManageFilesEvenIfNoCsv(statsBucket, prefix, LOCAL_DIR, localSeqPath, localTeamPath,
						csvInfoRow, currentGroups);
				logInfo(METHOD_NAME, "管理ファイルPUT終了");
			}
			String messageCd = MessageCdConst.MCD00014I_NO_MAP_DATA;
			String fillChar = "追加レコードがないため処理終了 (既存CSV数: " + csvInfoRow.size() + "件)";
			endLog(METHOD_NAME, messageCd, fillChar);
			return;
		}
		logInfo(METHOD_NAME, "buildWorkItems() 開始");
		List<CsvWorkItem> workItems = buildWorkItems(
		        LOCAL_DIR, plan, csvInfoRow, csvArtifactResource, METHOD_NAME);
		logInfo(METHOD_NAME, "buildWorkItems() 終了 workItems.size=" + workItems.size());
		if (workItems.isEmpty()) {
			logInfo(METHOD_NAME, "workItems=0 のためCSV生成なし");
			if (alwaysPutManageFiles) {
				logInfo(METHOD_NAME, "alwaysPutManageFiles=true のため管理ファイルPUT開始");
				putManageFilesEvenIfNoCsv(statsBucket, prefix, LOCAL_DIR, localSeqPath, localTeamPath,
						csvInfoRow, currentGroups);
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
			upsertDataTeamList(localTeamPath, csvInfoRow, processResult.succeeded, processResult.failedRelativeKeys);
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
	 * localOnly=true の場合のエントリポイント。
	 * S3への管理ファイルダウンロード/PUTを行わず、ローカルディレクトリのみを対象にCSVを生成する。
	 * {@link #execute()} と同様、firstRunであっても必ずcsvInfoRowと突き合わせて計画を立てる。
	 *
	 * @param outDir CSV出力先のローカルディレクトリ
	 * @throws IOException 管理ファイルの入出力に失敗した場合
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
			Map<String, List<String>> csvInfoRow;
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
			List<List<String>> currentGroups = normalizeGroups(sortSeqs());
			logInfo(METHOD_NAME, "sortSeqs() 終了 currentGroups.size=" + currentGroups.size());
			boolean firstRun = !Files.exists(localSeqPath);
			logInfo(METHOD_NAME, "firstRun=" + firstRun + ", localSeqPathExists=" + Files.exists(localSeqPath));
			List<List<String>> textGroups = firstRun
					? Collections.emptyList()
					: normalizeGroups(readSeqListJson(localSeqPath));
			if (!firstRun) {
				logInfo(METHOD_NAME, "既存 seqListJson 読み込み完了 textGroups.size=" + textGroups.size());
			}
			// ★ execute() と同様、firstRunでもcsvInfoRowと必ず突き合わせる。
			logInfo(METHOD_NAME, "matchSeqCombPlan() 開始");
			CsvBuildPlan plan = matchSeqCombPlan(textGroups, currentGroups, csvInfoRow);
			logInfo(METHOD_NAME, "matchSeqCombPlan() 終了 recreateByCsvKey.size="
					+ (plan == null ? 0 : plan.recreateByCsvKey.size())
					+ ", newTargets.size=" + (plan == null ? 0 : plan.newTargets.size()));
			if (plan == null || (plan.recreateByCsvKey.isEmpty() && plan.newTargets.isEmpty())) {
				logInfo(METHOD_NAME, "処理対象なしのため管理ファイルのみ更新");
				upsertDataTeamList(localTeamPath, csvInfoRow, Collections.emptyList(), Collections.emptySet());
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
			        LOCAL_DIR, plan, csvInfoRow, csvArtifactResource, METHOD_NAME);
			logInfo(METHOD_NAME, "buildWorkItems() 終了 workItems.size=" + workItems.size());
			if (workItems.isEmpty()) {
				logInfo(METHOD_NAME, "workItems=0 のため管理ファイルのみ更新");
				upsertDataTeamList(localTeamPath, csvInfoRow, Collections.emptyList(), Collections.emptySet());
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
			upsertDataTeamList(localTeamPath, csvInfoRow, processResult.succeeded, processResult.failedRelativeKeys);
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
	/**
	 * {@link CsvBuildPlan} の再作成対象・新規対象を、実際にCSVを生成するための
	 * {@link CsvWorkItem} のリストに変換する。
	 * 新規対象はdataCategoryから決定した「国: リーグ名[ - ラウンド名]」フォルダ単位でグルーピングし、
	 * ファイル名は「home_team_name-away_team_name.csv」とする。
	 * 同一フォルダ内で対戦カードが重複する場合（同じ2チームが複数回対戦する場合等）は、
	 * 既存CSV（csvInfoRow）およびこの呼び出し内で既に割り当てたキーと衝突しないよう、
	 * ファイル名に "_2" 等の連番を付与して区別する。
	 *
	 * @param localDir CSV出力先のローカルディレクトリ（未使用だが呼び出し規約上受け取る）
	 * @param plan 再作成対象・新規対象を保持するプラン
	 * @param csvInfoRow 既存CSVレジストリ（csvId→seqKeyリスト）。新規ファイル名の重複回避に使用
	 * @param csvArtifactResource CSV生成条件（スコア・国リーグ制限）
	 * @param parentMethod 呼び出し元メソッド名（ログ用）
	 * @return 生成対象のworkItem一覧（CSV相対キー順にソート済み）
	 */
	private List<CsvWorkItem> buildWorkItems(
	        Path localDir,
	        CsvBuildPlan plan,
	        Map<String, List<String>> csvInfoRow,
	        CsvArtifactResource csvArtifactResource,
	        String parentMethod) {
	    final String METHOD_NAME = "buildWorkItems";
	    logInfo(METHOD_NAME, "開始 recreateByCsvKey.size="
	            + (plan == null ? 0 : plan.recreateByCsvKey.size())
	            + ", newTargets.size=" + (plan == null ? 0 : plan.newTargets.size())
	            + ", localDir=" + localDir);
	    List<CsvWorkItem> workItems = new ArrayList<>();
	    Set<String> reservedRelativeKeys = new LinkedHashSet<>();
	    if (csvInfoRow != null) {
	        for (String k : csvInfoRow.keySet()) {
	            String ck = canonicalizeCsvId(k);
	            if (!ck.isBlank()) {
	                reservedRelativeKeys.add(ck);
	            }
	        }
	    }
	    for (Map.Entry<String, List<String>> entry : plan.recreateByCsvKey.entrySet()) {
	        String relativeKey = canonicalizeCsvId(entry.getKey());
	        List<String> ids = normalizeSeqList(entry.getValue());
	        if (relativeKey.isBlank() || ids.isEmpty()) {
	            logWarn(METHOD_NAME, "recreate skip relativeKey=" + shortKey(relativeKey)
	                    + ", ids=" + ids);
	            continue;
	        }
	        reservedRelativeKeys.add(relativeKey);
	        workItems.add(new CsvWorkItem(relativeKey, ids));
	        logInfo(METHOD_NAME, "recreate add relativeKey=" + shortKey(relativeKey)
	                + ", ids=" + ids);
	    }
	    logInfo(METHOD_NAME, "resolveNewTargetsByFolder() 開始");
	    Map<String, List<NewTargetGroup>> newTargetsByFolder = resolveNewTargetsByFolder(
	            plan.newTargets,
	            csvArtifactResource,
	            parentMethod);
	    logInfo(METHOD_NAME, "resolveNewTargetsByFolder() 終了 folderCount=" + newTargetsByFolder.size());
	    for (Map.Entry<String, List<NewTargetGroup>> e : newTargetsByFolder.entrySet()) {
	        String folderName = canonicalizeFolderSegment(e.getKey());
	        List<NewTargetGroup> groups = e.getValue();
	        groups.sort(Comparator.comparingInt(g -> minSeqOfIds(g.seqIds)));
	        for (NewTargetGroup group : groups) {
	            String relativeKey = buildUniqueCsvRelativeKey(
	                    folderName, group.homeTeamName, group.awayTeamName, reservedRelativeKeys);
	            workItems.add(new CsvWorkItem(relativeKey, group.seqIds));
	            logInfo(METHOD_NAME, "new add relativeKey=" + shortKey(relativeKey)
	                    + ", ids=" + group.seqIds);
	        }
	    }
	    workItems.sort((a, b) -> compareCsvRelativeKey(a.getRelativeKey(), b.getRelativeKey()));
	    logInfo(METHOD_NAME, "終了 workItems.size=" + workItems.size());
	    return workItems;
	}
	/**
	 * 新規対象グループ（一時キー→seqIds）を、dataCategoryから決定した
	 * 「国: リーグ名[ - ラウンド名]」フォルダ単位でグルーピングし直す。
	 * 「国: リーグ名」を最低限特定できないグループはCSV生成対象から除外する
	 * （※除外されたグループもDB上には残るため、別画面等でdata_categoryが修正された後の
	 * 次回実行時には自動的に再評価され、CSVが作られるようになる）。
	 *
	 * @param newTargets 新規対象（一時キー→seqIds）
	 * @param csvArtifactResource CSV生成条件（未使用だが呼び出し規約上受け取る）
	 * @param parentMethod 呼び出し元メソッド名（ログ用）
	 * @return フォルダ名（正規化前の「国: リーグ - ラウンド」形式）→対象一覧のマップ
	 */
	private Map<String, List<NewTargetGroup>> resolveNewTargetsByFolder(
	        Map<String, List<String>> newTargets,
	        CsvArtifactResource csvArtifactResource,
	        String parentMethod) {
	    final String METHOD_NAME = "resolveNewTargetsByFolder";
	    logInfo(METHOD_NAME, "開始 newTargets.size=" + (newTargets == null ? 0 : newTargets.size()));
	    Map<String, List<NewTargetGroup>> newTargetsByFolder = new LinkedHashMap<>();
	    if (newTargets == null || newTargets.isEmpty()) {
	        logInfo(METHOD_NAME, "newTargets 空のため終了");
	        return newTargetsByFolder;
	    }
	    int skippedByCategory = 0;
	    for (Map.Entry<String, List<String>> entry : newTargets.entrySet()) {
	        String tempKey = entry.getKey();
	        List<String> ids = normalizeSeqList(entry.getValue());
	        if (ids.isEmpty()) {
	            logWarn(METHOD_NAME, "skip ids empty tempKey=" + tempKey);
	            continue;
	        }
	        logInfo(METHOD_NAME, "preview fetch 開始 tempKey=" + tempKey + ", ids=" + ids);
	        List<CsvPreviewRow> preview = fetchPreview(ids, "resolveNewTargetsByFolder");
	        logInfo(METHOD_NAME, "preview fetch 終了 tempKey=" + tempKey
	                + ", preview.size=" + (preview == null ? 0 : preview.size()));
	        if (preview == null || preview.isEmpty()) {
	            logWarn(METHOD_NAME, "skip preview empty tempKey=" + tempKey);
	            continue;
	        }
	        CsvPreviewRow row = findPreviewRowWithTeams(preview);
	        String homeTeamName = safe(row.getHomeTeamName()).trim();
	        String awayTeamName = safe(row.getAwayTeamName()).trim();
	        if (homeTeamName.isEmpty()) {
	            homeTeamName = safe(firstPreviewValue(preview, CsvPreviewRow::getHomeTeamName, false)).trim();
	        }
	        if (awayTeamName.isEmpty()) {
	            awayTeamName = safe(firstPreviewValue(preview, CsvPreviewRow::getAwayTeamName, false)).trim();
	        }
	        String resolvedCategory = resolveCategoryWithFutureFallback(
	                safe(row.getDataCategory()).trim(),
	                homeTeamName,
	                awayTeamName,
	                METHOD_NAME);
	        String folderName = buildCountryLeagueRoundFolderName(resolvedCategory);
	        if (folderName == null) {
	            skippedByCategory++;
	            logWarn(METHOD_NAME, "「国: リーグ名」を特定できないためCSV生成をスキップ tempKey=" + tempKey
	                    + ", resolvedCategory=" + resolvedCategory
	                    + ", home=" + homeTeamName
	                    + ", away=" + awayTeamName
	                    + " (data_categoryが修正され次第、次回実行で自動的に対象になります)");
	            continue;
	        }
	        logInfo(METHOD_NAME, "folder resolve tempKey=" + tempKey + ", folderName=" + folderName);
	        newTargetsByFolder.computeIfAbsent(folderName, k -> new ArrayList<>())
	                .add(new NewTargetGroup(ids, homeTeamName, awayTeamName));
	    }
	    if (skippedByCategory > 0) {
	        logWarn(METHOD_NAME, "「国: リーグ名」不明によりCSV生成をスキップした件数=" + skippedByCategory);
	    }
	    logInfo(METHOD_NAME, "終了 folderCount=" + newTargetsByFolder.size());
	    return newTargetsByFolder;
	}
	/**
	 * dataCategoryから「国: リーグ名 - ラウンドN」形式のフォルダ名を組み立てる。
	 * 「国: リーグ名」を特定できない場合、または {@link #resolveEffectiveRoundName} で
	 * 数値ラウンドを特定できない場合は null を返す（＝CSV生成対象外の合図。
	 * ラウンド番号が付き次第、次回実行で自動的に対象になる）。
	 * 物理的なフォルダ名（S3キー/ローカルパス）としては、既存の {@link #canonicalizeFolderSegment(String)}
	 * によりコロンはハイフンに正規化される（例: "日本: J1リーグ - ラウンド5" -> "日本-J1リーグ-ラウンド5"）。
	 * これは季末削除処理（EachCsvTransaction 等）が前提とするハイフン正規形との整合を保つための仕様。
	 *
	 * @param category 判定対象のdataCategory
	 * @return 「国: リーグ名 - ラウンドN」形式の論理フォルダ名。特定できない場合はnull
	 */
	private String buildCountryLeagueRoundFolderName(String category) {
	    String normalized = safe(category).trim();
	    if (normalized.isEmpty()) {
	        return null;
	    }
	    List<String> countryLeague;
	    try {
	        countryLeague = ExecuteMainUtil.getCountryLeagueByRegex(normalized);
	    } catch (Exception e) {
	        return null;
	    }
	    if (countryLeague == null || countryLeague.size() < 2) {
	        return null;
	    }
	    String country = safe(countryLeague.get(0)).trim();
	    String league = safe(countryLeague.get(1)).trim();
	    if (country.isEmpty() || league.isEmpty()) {
	        return null;
	    }
	    String roundName = resolveEffectiveRoundName(normalized, country, league);
	    if (roundName.isEmpty()) {
	        // 数値ラウンドもステージ名も特定できない場合のみCSV生成をスキップする
	        return null;
	    }
	    return country + ": " + league + " - " + roundName;
	}
	/**
	 * dataCategoryから「ラウンドN」形式の数値ラウンド名を解決する。
	 * {@link CsvFileNameService#extractRoundName(String)} の戻り値が
	 * 「ラウンド」＋数字のみ（{@link #ROUND_TOKEN} に完全一致、前後に余計な文字を含まない）
	 * の場合だけ有効なラウンドとみなして採用する。
	 * "クラウスラ"/"アペルトゥラ" 等のステージ名のみで数値ラウンドが得られない場合や、
	 * 空/"unknown"/"不明"の場合は空文字を返す（＝CSV生成対象外の合図。
	 * dataCategoryに数値ラウンドが入り次第、次回実行で自動的に対象になる）。
	 *
	 * @param normalized 判定対象のdataCategory（trim済み）
	 * @param country 国名（現在未使用。呼び出し規約上受け取る）
	 * @param league リーグ名（現在未使用。呼び出し規約上受け取る）
	 * @return 「ラウンドN」形式のラウンド名。解決できない場合は空文字
	 */
	private String resolveEffectiveRoundName(String normalized, String country, String league) {
	    String roundName = safe(this.csvFileNameService.extractRoundName(normalized)).trim();
	    if (!roundName.isEmpty() && ROUND_TOKEN.matcher(roundName).matches()) {
	        return roundName;
	    }
	    return "";
	}
	/**
	 * チーム名をファイル名の一部として使えるようにサニタイズする。
	 * パス区切り文字・OSで問題になりやすい記号を "_" に置換し、連続空白を圧縮する。
	 *
	 * @param value 対象のチーム名
	 * @return サニタイズ後の文字列
	 */
	private static String sanitizeFileNameSegment(String value) {
	    String s = Normalizer.normalize(safe(value), Normalizer.Form.NFKC).trim();
	    if (s.isEmpty()) {
	        return "";
	    }
	    s = s.replaceAll("[\\\\/:*?\"<>|]", "_");
	    s = s.replaceAll("\\s+", " ").trim();
	    return s;
	}
	/** フォルダ決定後の新規CSV対象1件（対象seqId一覧・対戦カード）を表す内部クラス */
	private static final class NewTargetGroup {
	    private final List<String> seqIds;
	    private final String homeTeamName;
	    private final String awayTeamName;
	    private NewTargetGroup(List<String> seqIds, String homeTeamName, String awayTeamName) {
	        this.seqIds = seqIds;
	        this.homeTeamName = homeTeamName;
	        this.awayTeamName = awayTeamName;
	    }
	}
	/**
	 * フォルダ名・対戦カードから「home_team_name-away_team_name.csv」形式のCSV相対キーを組み立てる。
	 * 既に予約済み（既存CSV、またはこの呼び出し内で既に割り当て済み）のキーと衝突する場合は
	 * "_2", "_3", ... を付与して一意になるまで採番する。採番したキーは reservedRelativeKeys に登録する。
	 *
	 * @param folderName 正規化済みフォルダ名
	 * @param homeTeamName ホームチーム名
	 * @param awayTeamName アウェイチーム名
	 * @param reservedRelativeKeys 予約済みキー集合（このメソッド内で更新される）
	 * @return 一意なCSV相対キー
	 */
	private String buildUniqueCsvRelativeKey(
	        String folderName,
	        String homeTeamName,
	        String awayTeamName,
	        Set<String> reservedRelativeKeys) {
	    String home = sanitizeFileNameSegment(homeTeamName);
	    String away = sanitizeFileNameSegment(awayTeamName);
	    if (home.isEmpty()) {
	        home = "unknown-home";
	    }
	    if (away.isEmpty()) {
	        away = "unknown-away";
	    }
	    String baseFileName = home + "-" + away;
	    String relativeKey = canonicalizeCsvId(joinS3Key(folderName, baseFileName + BookMakersCommonConst.CSV));
	    int suffix = 2;
	    while (reservedRelativeKeys.contains(relativeKey)) {
	        relativeKey = canonicalizeCsvId(
	                joinS3Key(folderName, baseFileName + "_" + suffix + BookMakersCommonConst.CSV));
	        suffix++;
	    }
	    reservedRelativeKeys.add(relativeKey);
	    return relativeKey;
	}
	/**
	 * workItemsを {@link #workChunkSize} 件ずつのチャンクに分割し、
	 * チャンクごとに {@link ExecutorService} で並列処理してCSVを生成する。
	 *
	 * @param workItems 処理対象のworkItem一覧
	 * @param csvArtifactResource CSV生成条件
	 * @param pool 並列実行用スレッドプール
	 * @param baseDir CSV出力先のローカルディレクトリ
	 * @param localMode true の場合S3へのPUTを行わない
	 * @param bucket S3バケット名（localMode=trueの場合はnull可）
	 * @param prefix S3出力プレフィックス（localMode=trueの場合はnull可）
	 * @param parentMethod 呼び出し元メソッド名（ログ用）
	 * @return 成功/失敗/スキップの集計結果
	 */
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
						+ ", ids=" + item.getSeqIds());
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
	            + ", ids=" + item.getSeqIds()
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
	/**
	 * CSV化対象の行群に対し、dataCategory/home/awayが欠けている行を
	 * future_masterの情報で補完する。
	 * dataCategoryは「ラウンド情報を含む使用可能な値」が既にあればそれを優先し、
	 * 無ければfuture_masterから解決した値で全行を上書き補完する。
	 *
	 * @param result CSV化対象の行群（呼び出し元で書き換えられる）
	 * @param parentMethod 呼び出し元メソッド名（ログ用）
	 * @return 解決されたdataCategory（見つからない場合は空文字）
	 */
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
	/**
	 * CSV生成に成功した各グループについて、csv_detail_manageテーブルへ
	 * upsert（新規登録または既存更新）を行う。
	 *
	 * @param succeeded CSV生成に成功したグループのメタ情報一覧
	 * @param parentMethod 呼び出し元メソッド名（ログ用）
	 */
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
	/**
	 * dataCategoryまたはcsvIdからcountry/leagueを抽出し、
	 * country_league_season_masterからシーズン年を取得する。
	 * 抽出・取得に失敗しても例外にはせず、空文字を返して処理を継続させる。
	 *
	 * @param csvId 対象CSVの相対キー
	 * @param dataCategory 対象のデータカテゴリ
	 * @return シーズン年（取得できない場合は空文字）
	 */
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
	/**
	 * csv_detail_manageテーブルへ1件分の情報をupsertする。
	 * 既存行があればUPDATE、無ければINSERTし、更新/挿入件数が1件でなければ例外を投げる。
	 *
	 * @param csvId 対象CSVの相対キー
	 * @param dataCategory データカテゴリ
	 * @param season シーズン年
	 * @param home ホームチーム名
	 * @param away アウェイチーム名
	 * @param parentMethod 呼び出し元メソッド名（ログ用）
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
	/**
	 * ログ出力用に「dataCategory(season): home vs away」形式のコンテキスト文字列を組み立てる。
	 *
	 * @param dataCategory データカテゴリ
	 * @param season シーズン年
	 * @param home ホームチーム名
	 * @param away アウェイチーム名
	 * @return 組み立てたコンテキスト文字列
	 */
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
	/**
	 * dataCategoryから country/league が特定できなかった場合のフォールバックとして、
	 * csvIdのフォルダ名部分（例: "日本-J1リーグ-ラウンド5"）からcountry/leagueを抽出する。
	 *
	 * @param csvId 対象CSVの相対キー
	 * @return [0]=country, [1]=league（抽出できない場合はどちらも空文字）
	 */
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
	private void writeSeqListJson(Path out, List<List<String>> groups) throws IOException {
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
	private List<List<String>> readSeqListJson(Path path) {
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
				List<List<String>> result = SEQ_JSON.readValue(json,
						new com.fasterxml.jackson.core.type.TypeReference<List<List<String>>>() {
						});
				logInfo(METHOD_NAME, "JSON形式読込完了 groups.size=" + result.size());
				return result;
			}
			List<List<String>> result = new ArrayList<>();
			for (String line : json.split("\n")) {
				line = line.trim();
				if (line.isEmpty()) {
					continue;
				}
				List<String> group = new ArrayList<>();
				for (String s : line.split(",")) {
					s = s.trim();
					if (!s.isEmpty()) {
						try {
							group.add(s);
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
	/**
	 * ローカルに書き出したCSVファイルを、最終出力先のS3キーへアップロードする。
	 *
	 * @param bucket S3バケット名
	 * @param finalPrefix S3出力プレフィックス
	 * @param baseDir CSV出力先のローカルディレクトリ（相対キー計算の基準）
	 * @param localFile アップロード対象のローカルファイル
	 * @return アップロード先のS3キー
	 */
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
	/**
	 * 連番をソートする
	 * 改善版:
	 * - findGroupTargetsPage() は home/away/match_id 単位
	 * - 同一グループに対する findSeqListByGroup() の重複実行を防止
	 *
	 * @return DB上の現在の対象を home/away/matchId 単位でグルーピングした
	 *         seqKeyリストの一覧
	 */
	private List<List<String>> sortSeqs() {
		final String METHOD_NAME = "sortSeqs";
		logInfo(METHOD_NAME, "開始 GROUP_PAGE_SIZE=" + GROUP_PAGE_SIZE);
		List<List<String>> result = new ArrayList<>();
		int offset = 0;
		int pageNo = 1;
		// 同一グループの重複SQL実行防止
		Map<String, List<String>> seqCache = new LinkedHashMap<>();
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
				List<String> seqs = seqCache.get(groupKey);
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
							+ ", " + seqs);
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
	/**
	 * グループのリストに対し、各グループのseqKeyリストを {@link #normalizeSeqListStatic(List)}
	 * で正規化する（重複除去・空グループ除外）。
	 *
	 * @param groups 正規化前のグループ一覧
	 * @return 正規化後のグループ一覧
	 */
	private static List<List<String>> normalizeGroups(List<List<String>> groups) {
		if (groups == null) {
			return Collections.emptyList();
		}
		List<List<String>> out = new ArrayList<>();
		for (List<String> g : groups) {
			List<String> ng = normalizeSeqListStatic(g);
			if (!ng.isEmpty()) {
				out.add(ng);
			}
		}
		return out;
	}
	/**
	 * {@link #normalizeSeqListStatic(List)} のインスタンスメソッド版ラッパー。
	 *
	 * @param src 正規化前のseqKeyリスト
	 * @return 正規化後のseqKeyリスト
	 */
	private List<String> normalizeSeqList(List<String> src) {
		return normalizeSeqListStatic(src);
	}
	/**
	 * seqKey（例: "zRLNYw4L-37"）から末尾のハイフン以降の連番部分を数値として取り出す。
	 * ハイフンが無い場合は文字列全体を数値化する。数値化できない場合はInteger.MAX_VALUE（末尾扱い）。
	 *
	 * @param seqKey 対象のseqKey文字列
	 * @return 末尾連番部分の数値（数値化できない場合はInteger.MAX_VALUE）
	 */
	private static int extractSeqNo(String seqKey) {
		if (seqKey == null) {
			return Integer.MAX_VALUE;
		}
		int idx = seqKey.lastIndexOf('-');
		String numPart = (idx >= 0) ? seqKey.substring(idx + 1) : seqKey;
		try {
			return Integer.parseInt(numPart);
		} catch (NumberFormatException e) {
			return Integer.MAX_VALUE;
		}
	}
	/**
	 * seqKeyのリストから重複を除去し、末尾連番部分（{@link #extractSeqNo(String)}）を
	 * 数値として比較して昇順ソートする。
	 * ※ TreeSet&lt;String&gt; による文字列としての単純ソートは、連番が2桁以上になった時点で
	 *   "-10" が "-2" より前に来てしまうなど数値として正しい順序にならないため使用しない。
	 *
	 * @param src 正規化前のseqKeyリスト
	 * @return 重複除去・昇順ソート済みのseqKeyリスト
	 */
	private static List<String> normalizeSeqListStatic(List<String> src) {
		if (src == null || src.isEmpty()) {
			return Collections.emptyList();
		}
		return src.stream()
				.filter(Objects::nonNull)
				.distinct()
				.sorted(Comparator.comparingInt(ExportCsvService::extractSeqNo)
						.thenComparing(Comparator.naturalOrder()))
				.collect(Collectors.toList());
	}
	/**
	 * seqKeyの一覧からDataEntityを取得し、試合状態（times）による絞り込み・
	 * CSV化条件の判定・異常データ除去・スコア/対戦カード情報の補完までを行う。
	 * まず {@link #applyFinishedStateFilter(List)} で「それ以外」「ハーフタイム」「終了済」の
	 * 組み合わせに応じた対象行の絞り込み・スキップ判定を行い（詳細は同メソッドのJavaDoc参照）、
	 * その後 {@link CsvArtifactHelper#csvCondition}／{@link CsvArtifactHelper#abnormalChk} を適用する。
	 * それでも対象が空になった場合、addManualFlg="1"（手動確定）の行があれば、
	 * それ単体でCSV化対象として救済する。
	 *
	 * @param ids 対象のseqKey一覧
	 * @param csvArtifactResource CSV生成条件
	 * @param parentMethod 呼び出し元メソッド名（ログ用）
	 * @param label ログ識別用のラベル
	 * @return フィルタ・補完済みのDataEntity一覧（対象が無い/条件を満たさない場合はnull）
	 */
	private List<DataEntity> fetchAndFilter(
			List<String> ids,
			CsvArtifactResource csvArtifactResource,
			String parentMethod,
			String label) {
		final String METHOD_NAME = "fetchAndFilter";
		logInfo(METHOD_NAME, "開始 label=" + label + ", ids=" + ids);
		if (ids == null || ids.isEmpty()) {
			logInfo(METHOD_NAME, "ids empty のため null返却 label=" + label);
			return null;
		}
		List<DataEntity> raw;
		try {
			logInfo(METHOD_NAME, "findByData() 開始 label=" + label + ", ids=" + ids);
			raw = this.bookCsvDataRepository.findByData(ids);
			logInfo(METHOD_NAME, "findByData() 終了 label=" + label
					+ ", result.size=" + (raw == null ? 0 : raw.size()));
		} catch (Exception e) {
			logError(METHOD_NAME, "findByData() 失敗 label=" + label + ", ids=" + ids, e);
			throw e;
		}
		if (raw == null || raw.isEmpty()) {
		    logInfo(METHOD_NAME, "findByData() 結果なしのため null返却 label=" + label);
		    return null;
		}
		List<DataEntity> stateFiltered = applyFinishedStateFilter(raw);
		logInfo(METHOD_NAME, "applyFinishedStateFilter() 後 label=" + label
				+ ", raw.size=" + raw.size()
				+ ", stateFiltered.size=" + (stateFiltered == null ? 0 : stateFiltered.size()));
		if (stateFiltered == null || stateFiltered.isEmpty()) {
			logInfo(METHOD_NAME,
					"「それ以外」を含まない ハーフタイム+終了済のみの組み合わせのためCSV生成をスキップ label=" + label);
			return null;
		}
		List<DataEntity> result = stateFiltered;
		boolean condition = this.helper.csvCondition(result, csvArtifactResource);
		logInfo(METHOD_NAME, "csvCondition 判定 label=" + label + ", result=" + condition);
		if (!condition) {
			result = null;
		} else {
			result = this.helper.abnormalChk(result);
			logInfo(METHOD_NAME, "abnormalChk() 後 label=" + label
					+ ", result.size=" + (result == null ? 0 : result.size()));
		}
		if (result == null || result.isEmpty()) {
			List<DataEntity> manualRows = extractManualFlaggedRows(stateFiltered);
			if (manualRows.isEmpty()) {
				manualRows = extractManualFlaggedRows(raw);
			}
			if (manualRows.isEmpty()) {
				logInfo(METHOD_NAME, "csvCondition/abnormalChk 後 empty かつ手動確定行も無いため null返却 label=" + label);
				return null;
			}
			logWarn(METHOD_NAME, "csvCondition/abnormalChk では対象外だったが、"
					+ "addManualFlg=1 の行が" + manualRows.size() + "件あるため救済して続行 label=" + label);
			result = manualRows;
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
	/**
	 * 試合時間（times）の内容に応じて、CSV化対象の行を絞り込む/スキップ判定する。
	 * 行を「それ以外（通常のスナップショット）」「ハーフタイム」「終了済」の3種類に分類し、
	 * その組み合わせにより次のとおり判定する。
	 * <ul>
	 *   <li>「終了済」のみ（それ以外・ハーフタイムが無い） → 「終了済」の行だけを返す（単体CSV）</li>
	 *   <li>「それ以外」「ハーフタイム」「終了済」がすべて存在する → 全行をそのまま返す（通常どおりseqKey順でCSV作成）</li>
	 *   <li>「ハーフタイム」「終了済」のみ（それ以外が無い） → 空リストを返す（CSV作成しない）</li>
	 *   <li>「それ以外」「終了済」のみ（ハーフタイムが無い） → 「終了済」の行だけを返す（単体CSV）</li>
	 *   <li>上記いずれにも当てはまらない組み合わせ（試合進行中で「終了済」がまだ無い等） → 全行をそのまま返す</li>
	 * </ul>
	 *
	 * @param raw 対象の行一覧（未フィルタ）
	 * @return CSV化対象として採用する行一覧。CSV作成自体をしない場合は空リスト
	 */
	private static List<DataEntity> applyFinishedStateFilter(List<DataEntity> raw) {
		if (raw == null || raw.isEmpty()) {
			return raw;
		}
		List<DataEntity> otherRows = new ArrayList<>();
		List<DataEntity> finishedRows = new ArrayList<>();
		boolean hasHalftime = false;
		for (DataEntity d : raw) {
			if (d == null) {
				continue;
			}
			String t = safe(d.getTimes()).trim();
			if (TIMES_FINISHED.equals(t)) {
				finishedRows.add(d);
			} else if (TIMES_HALFTIME.equals(t)) {
				hasHalftime = true;
			} else {
				otherRows.add(d);
			}
		}
		boolean hasOther = !otherRows.isEmpty();
		boolean hasFinished = !finishedRows.isEmpty();
		if (!hasOther && !hasHalftime && hasFinished) {
			// 「終了済」のみ → 単体CSV
			return finishedRows;
		}
		if (hasOther && hasHalftime && hasFinished) {
			// 全部揃っている → 全行を通常どおり採用
			return raw;
		}
		if (!hasOther && hasHalftime && hasFinished) {
			// ハーフタイム＋終了済のみ（それ以外が無い） → CSV作成しない
			return Collections.emptyList();
		}
		if (hasOther && !hasHalftime && hasFinished) {
			// それ以外＋終了済のみ（ハーフタイムが無い） → 終了済のみの単体CSV
			return finishedRows;
		}
		// 試合進行中（終了済がまだ無い）等、上記に該当しない組み合わせは
		// 従来どおり全行を対象として通常の判定・CSV作成に進める。
		return raw;
	}
	/**
	 * 行一覧の中から addManualFlg="1"（手動確定）の行だけを取り出す。
	 *
	 * @param rows 対象の行一覧
	 * @return addManualFlg="1"の行一覧（無ければ空リスト）
	 */
	private static List<DataEntity> extractManualFlaggedRows(List<DataEntity> rows) {
		if (rows == null || rows.isEmpty()) {
			return Collections.emptyList();
		}
		List<DataEntity> out = new ArrayList<>();
		for (DataEntity d : rows) {
			if (d != null && "1".equals(safe(d.getAddManualFlg()).trim())) {
				out.add(d);
			}
		}
		return out;
	}
	/**
	 * CSV出力用のファイルパスと内容から {@link CsvArtifact} を組み立てる。
	 *
	 * @param path 出力先ファイルパス
	 * @param result CSVに出力する行データ
	 * @param resource CSV生成条件（未使用だが呼び出し規約上受け取る）
	 * @return 組み立てたCsvArtifact（resultが空の場合はnull）
	 */
	private CsvArtifact buildCsvArtifact(String path, List<DataEntity> result, CsvArtifactResource resource) {
		if (result == null || result.isEmpty()) {
			return null;
		}
		// ★ 書き出し直前の最終防御として、ここでも改めてseqKey末尾連番順に並べ直す。
		//   fetchAndFilter()内のbackfillScores()で既に正しい順序になっているはずだが、
		//   書き出し直前の呼び出し箇所を1つに集約しておくことで、
		//   将来的に他の経路からbuildCsvArtifactが呼ばれても順序保証が崩れないようにする。
		List<DataEntity> ordered = new ArrayList<>(result);
		ordered.sort(Comparator.comparingInt(
				(DataEntity d) -> extractSeqNo(Objects.toString(d.getSeqKey(), null))));
		return new CsvArtifact(path, ordered);
	}
	/**
	 * CsvArtifactの内容をローカルファイルへ書き出す。
	 * 既存ファイルがあれば事前に削除してから書き出す。
	 *
	 * @param art 書き出し対象のCsvArtifact
	 */
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
	/**
	 * 指定ディレクトリが存在しない場合は作成する。
	 *
	 * @param dir 対象ディレクトリ
	 * @throws IOException ディレクトリ作成に失敗した場合
	 */
	private static void ensureDir(Path dir) throws IOException {
		Files.createDirectories(dir);
	}
	/**
	 * 今回のDB状態（dbSeqs）と既存CSVレジストリ（csvInfoRow）を突き合わせ、
	 * 「既存CSVの再作成対象」と「新規CSV対象」に振り分ける。
	 * 既存CSVとの同一グループ判定は、正規化済みグループ全体の完全一致（groupKey）を優先し、
	 * 一致しない場合はグループの最小seqKey（min）が既存CSVの最小seqKeyと一致するかで
	 * 「同じ試合の更新（recreate）」か「新規」かを判定する。
	 * この判定は必ずcsvInfoRow（実際に存在するCSVの内容）を基準に行い、seqList.txtの
	 * 中身（textSeqs）そのものは判定には使わない（前回実行時の参考情報としてのみ受け取る）。
	 *
	 * @param textSeqs 前回のseqList.txtから読み込んだグループ一覧（現状は判定に未使用）
	 * @param dbSeqs 今回DBから取得した最新のグループ一覧
	 * @param csvInfoRow 既存CSVレジストリ（csvId→seqKeyリスト）
	 * @return 再作成対象・新規対象を保持するプラン
	 */
	private CsvBuildPlan matchSeqCombPlan(
			List<List<String>> textSeqs,
			List<List<String>> dbSeqs,
			Map<String, List<String>> csvInfoRow) {
		CsvBuildPlan plan = new CsvBuildPlan();
		Map<String, String> minSeqToCsvKey = new LinkedHashMap<>();
		Map<String, String> groupKeyToCsvKey = new LinkedHashMap<>();
		Map<String, List<String>> normalizedCsvInfo = canonicalizeCsvInfoMap(csvInfoRow);
		for (Map.Entry<String, List<String>> e : normalizedCsvInfo.entrySet()) {
			String csvKey = canonicalizeCsvId(e.getKey());
			List<String> ids = normalizeSeqListStatic(e.getValue());
			if (ids.isEmpty()) {
				continue;
			}
			String min = ids.get(0);
			minSeqToCsvKey.put(min, csvKey);
			groupKeyToCsvKey.put(groupKey(ids), csvKey);
		}
		for (List<String> dbGroup : dbSeqs) {
			if (dbGroup == null || dbGroup.isEmpty()) {
				continue;
			}
			String gk = groupKey(dbGroup);
			if (groupKeyToCsvKey.containsKey(gk)) {
				continue;
			}
			String min = dbGroup.get(0);
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
	private List<CsvPreviewRow> fetchPreview(List<String> ids, String label) {
		final String METHOD_NAME = "fetchPreview";
		logInfo(METHOD_NAME, "開始 label=" + label + ", ids=" + ids);
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
	 * 現在のカテゴリが「ラウンド情報を含む使用可能な値」でなければ、
	 * future_masterから対戦カード（home/away、逆順も含む）に対応する
	 * game_team_categoryを取得してフォールバックとして使用する。
	 *
	 * @param currentCategory 現在のdataCategory
	 * @param homeTeamName ホームチーム名
	 * @param awayTeamName アウェイチーム名
	 * @param parentMethod 呼び出し元メソッド名（ログ用）
	 * @return 解決されたdataCategory（フォールバックも失敗した場合は現在の値をそのまま返す）
	 */
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
	/**
	 * dataCategoryが「空でなく」「unknownでもなく」「ラウンド名を抽出できる」場合に
	 * 使用可能なカテゴリとみなす。
	 *
	 * @param category 判定対象のdataCategory
	 * @return 使用可能な場合true
	 */
	private boolean isUsableCategory(String category) {
	    String normalized = safe(category).trim();
	    if (normalized.isEmpty()) {
	        return false;
	    }
	    if ("unknown".equalsIgnoreCase(normalized)) {
	        return false;
	    }
	    List<String> countryLeague;
	    try {
	        countryLeague = ExecuteMainUtil.getCountryLeagueByRegex(normalized);
	    } catch (Exception e) {
	        countryLeague = null;
	    }
	    if (countryLeague == null || countryLeague.size() < 2) {
	        return false;
	    }
	    String country = safe(countryLeague.get(0)).trim();
	    String league = safe(countryLeague.get(1)).trim();
	    if (country.isEmpty() || league.isEmpty()) {
	        return false;
	    }
	    return !resolveEffectiveRoundName(normalized, country, league).isEmpty();
	}
	/**
	 * home/awayのいずれかが埋まっている最初の行を返す。無ければ先頭行を返す。
	 *
	 * @param list 検索対象の行一覧
	 * @return home/awayが埋まっている最初の行、無ければ先頭行
	 */
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
	/**
	 * recordTime→seq順にソートした上で、スコアが空の行に直前の非空スコアを補完する
	 * （プレビュー行版）。
	 *
	 * @param list 補完対象の行一覧（呼び出し元で書き換えられる）
	 */
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
	/**
	 * グループ内の代表的なdataCategory/home/awayを求め、欠けている行に補完する
	 * （プレビュー行版）。
	 *
	 * @param group 補完対象のグループ（呼び出し元で書き換えられる）
	 */
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
	/**
	 * グループ内で最初に見つかった非空の値を返す（プレビュー行版）。
	 * preferRoundRow=true の場合、まずラウンド情報を含む行を優先して探す。
	 *
	 * @param group 検索対象のグループ
	 * @param getter 取得したい値のgetter
	 * @param preferRoundRow ラウンド情報を含む行を優先するか
	 * @return 見つかった値（無ければ空文字）
	 */
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
	/**
	 * seqKeyのリストを結合してグループ全体の一意キー文字列を組み立てる。
	 * 既存CSVレジストリのグループと、今回計算したDBグループが完全一致するかの判定に使う。
	 *
	 * @param ids 対象のseqKeyリスト
	 * @return 結合したグループキー文字列
	 */
	private static String groupKey(List<String> ids) {
		StringBuilder sb = new StringBuilder();
		for (String n : ids) {
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
	/**
	 * CSVファイル名またはS3キー（例: "xxx/12.csv"）から末尾の数値部分（CSV番号）を取り出す。
	 *
	 * @param keyOrName 対象のファイル名またはキー
	 * @return CSV番号（抽出できない場合はnull）
	 */
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
	 * data_team_list.txt を更新する。
	 *
	 * 手順:
	 * 1. 既存ファイルの内容を読み込む（破損・欠落・無関係な内容の可能性がある）。
	 * 2. 今回失敗したCSV（failedRelativeKeys）の行を削除する。
	 * 3. 既存CSVレジストリ（csvInfoRow）と今回成功したCSV（succeeded）の
	 *    どちらにも属さない行を削除する（＝実在しないCSVを指す行、無関係な内容を除去する自己修復）。
	 * 4. 今回新規/再作成に成功したCSV（succeeded）の行を最新内容で追加/更新する。
	 * 5. csvInfoRowに存在するのに行が無いCSV（＝data_team_list.txtから一部行が欠落していた場合）は、
	 *    DBから該当seqKeyのプレビューを取得して description を再構築し補完する。
	 *
	 * @param out data_team_list.txt の出力先パス
	 * @param csvInfoRow 実際に存在するCSVレジストリ（csvId→seqKeyリスト、今回実行より前の状態）
	 * @param succeeded 今回CSV生成に成功したグループのメタ情報一覧
	 * @param failedRelativeKeys 今回CSV生成に失敗したCSVの相対キー一覧
	 * @throws IOException ファイルの読み書きに失敗した場合
	 */
	private void upsertDataTeamList(
			Path out,
			Map<String, List<String>> csvInfoRow,
			List<CsvOutputMeta> succeeded,
			Set<String> failedRelativeKeys) throws IOException {
		final String METHOD_NAME = "upsertDataTeamList";
		logInfo(METHOD_NAME, "開始 path=" + out
				+ ", csvInfoRow.size=" + (csvInfoRow == null ? 0 : csvInfoRow.size())
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
		// ★ 自己修復: 実在するCSV（csvInfoRow ∪ 今回成功分）に属さない行を削除する。
		//   これにより、破損・一部欠落・無関係な内容で上書きされていたファイルでも、
		//   実CSVと無関係な行はすべて除去される。
		Set<String> validKeys = new LinkedHashSet<>();
		if (csvInfoRow != null) {
			for (String k : csvInfoRow.keySet()) {
				String ck = canonicalizeCsvId(k);
				if (!ck.isBlank()) {
					validKeys.add(ck);
				}
			}
		}
		if (succeeded != null) {
			for (CsvOutputMeta meta : succeeded) {
				if (meta == null) {
					continue;
				}
				String ck = canonicalizeCsvId(meta.getRelativeCsvKey());
				if (!ck.isBlank()) {
					validKeys.add(ck);
				}
			}
		}
		int beforePurge = csvKeyToLine.size();
		csvKeyToLine.keySet().retainAll(validKeys);
		int purged = beforePurge - csvKeyToLine.size();
		if (purged > 0) {
			logWarn(METHOD_NAME, "実在しないCSVを指す行を削除 purged=" + purged);
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
		// ★ 自己修復: csvInfoRowに存在するのに行が無いCSV（一部欠落・全面的な書き換え等）を、
		//   DBのプレビューから description を再構築して補完する。
		if (csvInfoRow != null && !csvInfoRow.isEmpty()) {
			int repaired = 0;
			for (Map.Entry<String, List<String>> e : csvInfoRow.entrySet()) {
				String csvKey = canonicalizeCsvId(e.getKey());
				if (csvKey.isBlank() || csvKeyToLine.containsKey(csvKey)) {
					continue;
				}
				List<String> ids = normalizeSeqListStatic(e.getValue());
				if (ids.isEmpty()) {
					continue;
				}
				List<CsvPreviewRow> preview = fetchPreview(ids, "upsertDataTeamList(repair)");
				if (preview == null || preview.isEmpty()) {
					logWarn(METHOD_NAME, "data_team_list 補完スキップ（プレビュー取得失敗） csvKey=" + shortKey(csvKey));
					continue;
				}
				CsvPreviewRow row = findPreviewRowWithTeams(preview);
				String homeTeamName = safe(row.getHomeTeamName()).trim();
				String awayTeamName = safe(row.getAwayTeamName()).trim();
				if (homeTeamName.isEmpty()) {
					homeTeamName = safe(firstPreviewValue(preview, CsvPreviewRow::getHomeTeamName, false)).trim();
				}
				if (awayTeamName.isEmpty()) {
					awayTeamName = safe(firstPreviewValue(preview, CsvPreviewRow::getAwayTeamName, false)).trim();
				}
				String dataCategory = resolveCategoryWithFutureFallback(
						safe(row.getDataCategory()).trim(),
						homeTeamName,
						awayTeamName,
						METHOD_NAME);
				String vsPart;
				if (!homeTeamName.isEmpty() && !awayTeamName.isEmpty()) {
					vsPart = homeTeamName + "vs" + awayTeamName;
				} else if (!homeTeamName.isEmpty()) {
					vsPart = homeTeamName;
				} else if (!awayTeamName.isEmpty()) {
					vsPart = awayTeamName;
				} else {
					vsPart = "(team name empty)";
				}
				String desc = !dataCategory.isEmpty() ? dataCategory + " - " + vsPart : vsPart;
				csvKeyToLine.put(csvKey, csvKey + "\t" + desc);
				repaired++;
				logInfo(METHOD_NAME, "data_team_list 補完 csvKey=" + shortKey(csvKey)
						+ ", dataCategory=" + dataCategory
						+ ", home=" + homeTeamName
						+ ", away=" + awayTeamName);
			}
			if (repaired > 0) {
				logWarn(METHOD_NAME, "data_team_list 補完件数 repaired=" + repaired);
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
	/**
	 * 既存CSVレジストリのキー・seqKeyを正規化し、同一キーが複数あれば
	 * seqKeyをマージした上で重複除去・ソートする。
	 *
	 * @param src 正規化前のレジストリ（csvId→seqKeyリスト）
	 * @return 正規化後のレジストリ
	 */
	private Map<String, List<String>> canonicalizeCsvInfoMap(Map<String, List<String>> src) {
		Map<String, List<String>> result = new LinkedHashMap<>();
		if (src == null || src.isEmpty()) {
			return result;
		}
		for (Map.Entry<String, List<String>> e : src.entrySet()) {
			String normalizedKey = canonicalizeCsvId(e.getKey());
			List<String> normalizedSeqs = normalizeSeqListStatic(e.getValue());
			if (normalizedKey.isBlank() || normalizedSeqs.isEmpty()) {
				continue;
			}
			List<String> merged = new ArrayList<>();
			if (result.containsKey(normalizedKey)) {
				merged.addAll(result.get(normalizedKey));
			}
			merged.addAll(normalizedSeqs);
			result.put(normalizedKey, normalizeSeqListStatic(merged));
		}
		return result;
	}
	/**
	 * CSVの相対キー（S3キー/ローカルパス）をNFKC正規化し、区切り文字を"/"に統一、
	 * 先頭スラッシュ・連続スラッシュを除去し、フォルダ部分のみさらに
	 * {@link #canonicalizeFolderSegment(String)} で表記ゆれを吸収する。
	 *
	 * @param rawCsvId 正規化前のCSV相対キー
	 * @return 正規化後のCSV相対キー
	 */
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
	/**
	 * フォルダ名（国-リーグ-ラウンド表記）の表記ゆれを吸収する。
	 * 「国: リーグ」→「国-リーグ」への統一、ラウンド表記のハイフン・空白ゆれの統一、
	 * 全角数字の半角化、連続ハイフン/空白の圧縮を行う。
	 *
	 * @param segment 正規化前のフォルダ名
	 * @return 正規化後のフォルダ名
	 */
	private String canonicalizeFolderSegment(String segment) {
		String s = Normalizer.normalize(safe(segment), Normalizer.Form.NFKC).trim();
		if (s.isEmpty()) {
			return "";
		}
		// 国: リーグ を 国-リーグ に統一
		s = s.replaceAll("\\s*:\\s*", "-");
		// ラウンド表記を統一
		// 例:
		//   日本-J2リーグ - ラウンド 18
		//   日本-J2リーグ- ラウンド18
		//   日本-J2リーグ -ラウンド１８
		// -> 日本-J2リーグ-ラウンド18
		s = s.replaceAll("[ 　]*-[ 　]*ラウンド[ 　]*([0-9０-９]+)", "-ラウンド$1");
		// 全角数字を半角化
		s = toHalfWidthDigits(s);
		// ハイフン前後空白を除去
		s = s.replaceAll("\\s*-\\s*", "-");
		// 連続ハイフンを圧縮
		s = s.replaceAll("-{2,}", "-");
		// 連続半角空白を圧縮
		s = s.replaceAll(" {2,}", " ").trim();
		return s;
	}
	/**
	 * home/awayのいずれかが埋まっている最初の行を返す。無ければ先頭行を返す。
	 *
	 * @param list 検索対象の行一覧
	 * @return home/awayが埋まっている最初の行、無ければ先頭行
	 */
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
	/**
	 * nullを空文字に変換する（null安全なアクセサ）。
	 *
	 * @param s 対象の文字列
	 * @return sがnullなら空文字、そうでなければs
	 */
	private static String safe(String s) {
		return (s == null) ? "" : s;
	}
	/**
	 * recordTime→seqKey末尾連番順にソートした上で、スコアが空の行に
	 * 直前の非空スコアを補完する。
	 *
	 * @param list 補完対象の行一覧（呼び出し元で書き換えられる）
	 */
	private static void backfillScores(List<DataEntity> list) {
	    if (list == null || list.isEmpty()) {
	        return;
	    }
	    // ★ 並び順はseqKey末尾連番（採番順=記録順）を最優先にする。
	    //   recordTimeは書式次第で文字列比較の結果が実時刻と食い違うことがあるため、
	    //   同一連番内の補助的な比較キーとしてのみ使用する。
	    list.sort(Comparator
	            .comparingInt((DataEntity d) -> extractSeqNo(Objects.toString(d.getSeqKey(), null)))
	            .thenComparing((DataEntity d) -> {
	                String rt = d.getRecordTime();
	                return (rt == null) ? "" : rt;
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
	/**
	 * dataCategoryに「ラウンドN」が含まれているかを判定する。
	 *
	 * @param s 判定対象の文字列
	 * @return 「ラウンドN」を含む場合true
	 */
	private static boolean hasRound(String s) {
		return s != null && ROUND_TOKEN.matcher(s).find();
	}
	/**
	 * グループ内の代表的なdataCategory/home/away/matchIdを求め、
	 * 欠けている行に補完する（DataEntity版）。
	 *
	 * @param group 補完対象のグループ（呼び出し元で書き換えられる）
	 */
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
	/**
	 * グループ内で最初に見つかった非空の値を返す（DataEntity版）。
	 * preferRoundRow=true の場合、まずラウンド情報を含む行を優先して探す。
	 *
	 * @param group 検索対象のグループ
	 * @param getter 取得したい値のgetter
	 * @param preferRoundRow ラウンド情報を含む行を優先するか
	 * @return 見つかった値（無ければ空文字）
	 */
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
	/**
	 * カテゴリが「使用可能」でない（＝ラウンド情報などで補完が必要）かどうかを判定する。
	 *
	 * @param category 判定対象のdataCategory
	 * @return 補完が必要な場合true
	 */
	private boolean needsCategoryBackfill(String category) {
	    return !isUsableCategory(category);
	}
	/**
	 * CSV相対キーをフォルダ部分→CSV番号の順で比較する。
	 * フォルダが同じ場合はCSV番号（末尾の数値）を昇順比較し、
	 * 番号が抽出できないキーは末尾に回す。
	 *
	 * @param a 比較対象1
	 * @param b 比較対象2
	 * @return a, bの比較結果（{@link Comparator}の規約に準拠）
	 */
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
	/**
	 * キー文字列から最後の "/" より前の部分（フォルダ部分）を取り出す。
	 *
	 * @param key 対象のキー文字列
	 * @return フォルダ部分（"/"が無い場合は空文字）
	 */
	private static String parentPath(String key) {
		if (key == null) {
			return "";
		}
		int idx = key.lastIndexOf('/');
		return (idx >= 0) ? key.substring(0, idx) : "";
	}
	/**
	 * 文字列がnullまたは空白のみかを判定する。
	 *
	 * @param s 判定対象の文字列
	 * @return nullまたは空白のみの場合true
	 */
	private static boolean isBlank(String s) {
		return s == null || s.trim().isEmpty();
	}
	/**
	 * S3キーの先頭スラッシュを除去する。
	 *
	 * @param key 対象のS3キー
	 * @return 先頭スラッシュを除去したキー（keyがnullの場合はnull）
	 */
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
	/**
	 * プレフィックス文字列の前後のスラッシュを除去する。
	 *
	 * @param prefix 対象のプレフィックス
	 * @return 前後スラッシュを除去したプレフィックス（nullの場合は空文字）
	 */
	private static String normalizePrefix(String prefix) {
		if (prefix == null) {
			return "";
		}
		String p = prefix.trim();
		p = p.replaceAll("^/+", "");
		p = p.replaceAll("/+$", "");
		return p;
	}
	/**
	 * プレフィックスとファイル名を結合してS3キーを組み立てる。
	 *
	 * @param prefix S3プレフィックス（空可）
	 * @param fileName ファイル名
	 * @return 結合したS3キー
	 */
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
	/**
	 * seqKeyのリストの中から、末尾連番部分（{@link #extractSeqNo(String)}）の最小値を求める。
	 * 新規CSV対象グループをCSV番号順に採番する際のソートキーとして使用する。
	 *
	 * @param ids 対象のseqKeyリスト
	 * @return 最小の連番（リストが空の場合はInteger.MAX_VALUE）
	 */
	private static int minSeqOfIds(List<String> ids) {
	    if (ids == null || ids.isEmpty()) {
	        return Integer.MAX_VALUE;
	    }
	    int min = Integer.MAX_VALUE;
	    for (String id : ids) {
	        int n = extractSeqNo(id);
	        if (n < min) {
	            min = n;
	        }
	    }
	    return min;
	}
	/**
	 * 処理対象が0件だった場合でも、data_team_list.txt / seqList.txt を
	 * 最新のDB状態・既存CSVレジストリを基準に更新してS3へPUTする。
	 * これにより、CSVの追加/再作成が無くても、破損した管理ファイルの自己修復や
	 * 定常状態の維持が行われる。
	 *
	 * @param statsBucket S3バケット名
	 * @param prefix S3出力プレフィックス
	 * @param baseDir CSV出力先のローカルディレクトリ
	 * @param localSeqPath seqList.txtのローカルパス
	 * @param localTeamPath data_team_list.txtのローカルパス
	 * @param csvInfoRow 既存CSVレジストリ（csvId→seqKeyリスト）
	 * @param currentGroups 今回DBから取得した最新のグループ一覧
	 * @throws IOException ファイルの読み書きに失敗した場合
	 */
	private void putManageFilesEvenIfNoCsv(
			String statsBucket,
			String prefix,
			Path baseDir,
			Path localSeqPath,
			Path localTeamPath,
			Map<String, List<String>> csvInfoRow,
			List<List<String>> currentGroups) throws IOException {
		final String METHOD_NAME = "putManageFilesEvenIfNoCsv";
		logInfo(METHOD_NAME, "開始 statsBucket=" + statsBucket
				+ ", prefix=" + prefix
				+ ", currentGroups.size=" + (currentGroups == null ? 0 : currentGroups.size()));
		upsertDataTeamList(localTeamPath, csvInfoRow, Collections.emptyList(), Collections.emptySet());
		logInfo(METHOD_NAME, "data_team_list 更新完了");
		fileExistsService.uploadDataTeamListIfExists(statsBucket, prefix);
		logInfo(METHOD_NAME, "data_team_list PUT完了");
		writeSeqListJson(localSeqPath, currentGroups);
		logInfo(METHOD_NAME, "seqListJson 更新完了");
		fileExistsService.uploadSeqListIfExists(statsBucket, prefix);
		logInfo(METHOD_NAME, "seqListJson PUT完了");
		logInfo(METHOD_NAME, "終了");
	}
	/**
	 * 処理終了時のログを出力する。messageCd/fillCharが両方指定されている場合のみ
	 * 情報ログを追加出力し、最後に必ず終了ログを出す。
	 *
	 * @param method 呼び出し元メソッド名
	 * @param messageCd 出力するメッセージコード（不要な場合はnull）
	 * @param fillChar メッセージの埋め込み文字列（不要な場合はnull）
	 */
	private void endLog(String method, String messageCd, String fillChar) {
		if (messageCd != null && fillChar != null) {
			this.manageLoggerComponent.debugInfoLog(PROJECT_NAME, CLASS_NAME, method, messageCd, fillChar);
		}
		this.manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, method, "end");
	}
	/** CSV1件分の生成対象（出力先の相対キーと対象seqKey一覧）を表す内部クラス */
	private static final class CsvWorkItem {
		private final String relativeKey;
		private final List<String> seqIds;
		private CsvWorkItem(String relativeKey, List<String> seqIds) {
			this.relativeKey = relativeKey;
			this.seqIds = seqIds;
		}
		public String getRelativeKey() {
			return relativeKey;
		}
		public List<String> getSeqIds() {
			return seqIds;
		}
	}
	/** CSV生成成功時のメタ情報（csv_detail_manage登録・data_team_list更新に使用）を表す内部クラス */
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
	/** workItem1件の処理結果ステータス */
	private enum CsvTaskStatus {
		SUCCESS,
		FAILED,
		SKIPPED
	}
	/** workItem1件分の処理結果（ステータス・相対キー・成功時のメタ情報）を表す内部クラス */
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
	/** 一連のworkItem処理結果の集計（成功/失敗/スキップ件数と詳細）を保持する内部クラス */
	private static final class ProcessResult {
		private int successCount;
		private int failedCount;
		private int skippedCount;
		private final List<CsvOutputMeta> succeeded = new ArrayList<>();
		private final Set<String> failedRelativeKeys = new LinkedHashSet<>();
	}
	/**
	 * 情報レベルのログを出力する。
	 *
	 * @param method 呼び出し元メソッド名
	 * @param message ログメッセージ
	 */
	private void logInfo(String method, String message) {
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, method,
				MessageCdConst.MCD00099I_LOG, message);
	}
	/**
	 * 警告レベルのログを出力する。
	 *
	 * @param method 呼び出し元メソッド名
	 * @param message ログメッセージ
	 */
	private void logWarn(String method, String message) {
		this.manageLoggerComponent.debugWarnLog(
				PROJECT_NAME, CLASS_NAME, method,
				MessageCdConst.MCD00099I_LOG, message);
	}
	/**
	 * エラーレベルのログを出力する。
	 *
	 * @param method 呼び出し元メソッド名
	 * @param message ログメッセージ
	 * @param e 発生した例外
	 */
	private void logError(String method, String message, Exception e) {
		this.manageLoggerComponent.debugErrorLog(
				PROJECT_NAME, CLASS_NAME, method,
				MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION, e, message);
	}
	/**
	 * ログ出力用にキー文字列を120文字までに丸める。
	 *
	 * @param key 対象のキー文字列
	 * @return 120文字以内に丸めた文字列（nullの場合は"(null)"）
	 */
	private static String shortKey(String key) {
		if (key == null) {
			return "(null)";
		}
		if (key.length() <= 120) {
			return key;
		}
		return key.substring(0, 120) + "...";
	}
	/**
	 * 文字列中の全角数字を半角数字に変換する。
	 *
	 * @param in 変換対象の文字列
	 * @return 半角数字に変換した文字列
	 */
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
}