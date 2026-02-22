package dev.web.api.bm_w020;

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
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

import dev.common.config.PathConfig;
import dev.common.constant.BookMakersCommonConst;
import dev.common.constant.MessageCdConst;
import dev.common.entity.DataEntity;
import dev.common.filemng.FileMngWrapper;
import dev.common.logger.ManageLoggerComponent;
import dev.common.s3.S3Operator;
import dev.web.repository.bm.BookCsvDataRepository;
import dev.web.util.CsvArtifactHelper;

/**
 * StatデータCSV出力ロジック（S3 tmpステージング版）
 *
 * 流れ：
 * 1) S3（本番prefix）から seqList.txt / data_team_list.txt をローカルへDL
 * 2) DBからグルーピング作成 → 差分計画（recreate/new）作成
 * 3) 生成対象CSVのみローカルに作成
 * 4) 生成物を S3 tmpPrefix へ一旦 put（CSV + seqList + teamList）
 * 5) 全部成功したら tmp → 本番へ copy → tmp delete（コミット）
 *
 * localOnly=true のとき：
 * - S3操作（download/list/upload/copy/delete）を一切行わず、ローカルのみで完結
 * - seqList.txt は従来どおり「カンマ区切り」（1行=1グループ）
 * - data_team_list.txt は「N.csv\t説明」のTSV形式で更新
 */
@Component
public class ExportCsv {

	private static final String PROJECT_NAME = ExportCsv.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	private static final String CLASS_NAME = ExportCsv.class.getName();

	private static final String CSV_NEW_PREFIX = "mk";

	// 「ラウンド12」や「ラウンド 12」「ラウンド１２」も拾いたいなら少し広めに取る
	private static final Pattern ROUND_TOKEN = Pattern.compile("ラウンド\\s*[0-9０-９]+");

	// 末尾が "<数字>.csv" のキーをCSVとみなす
	private static final Pattern CSV_KEY_PATTERN = Pattern.compile("^.*?(\\d+)\\.csv$", Pattern.CASE_INSENSITIVE);

	// "4710.csv" / "/path/4710.csv" から 4710 を取り出す
	private static final Pattern CSV_NO_PATTERN = Pattern.compile("(^|.*/)(\\d+)\\.csv$", Pattern.CASE_INSENSITIVE);

	@Value("${exportcsv.local-only:false}")
	private boolean localOnly;

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
	private ManageLoggerComponent manageLoggerComponent;

	public void execute() throws IOException {
		final String METHOD_NAME = "execute";
		this.manageLoggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, "start");

		// 出力先（localOnly時はここにすべて作る）
		Path outDir = Paths.get(config.getCsvFolder()).toAbsolutePath().normalize();
		Files.createDirectories(outDir);

		if (localOnly) {
			executeLocalOnly(outDir);
			return;
		}

		// ====== 設定 ======
		final String statsBucket = config.getS3BucketsStats();

		// 本番prefix（直下なら ""、stats/ 配下なら "stats" 等）
		final String finalPrefix = ""; // 必要なら config から取得に変更

		// S3管理ファイルキー（本番側）
		final String seqKeyFinal = s3Operator.buildKey(finalPrefix, "seqList.txt");
		final String teamKeyFinal = s3Operator.buildKey(finalPrefix, "data_team_list.txt");

		// ローカル作業場所（ECSコンテナのローカル）。S3 tmpとは別物。
		final Path LOCAL_DIR = Paths.get(config.getCsvFolder());
		ensureDir(LOCAL_DIR);

		final Path localSeqPath = LOCAL_DIR.resolve("seqList.txt");
		final Path localTeamPath = LOCAL_DIR.resolve("data_team_list.txt");

		// S3 tmp prefix（実行単位でユニークにする）
		final String runId = String.valueOf(System.currentTimeMillis());
		final String prefixLabel = (finalPrefix == null || finalPrefix.isBlank())
				? "root"
				: finalPrefix.replaceAll("/+$", "");
		final String tmpPrefix = "tmp/" + prefixLabel + "/" + runId;

		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG,
				"bucket=" + statsBucket + ", finalPrefix=" + (finalPrefix == null ? "" : finalPrefix)
						+ ", tmpPrefix=" + tmpPrefix + ", localDir=" + LOCAL_DIR);

		// ====== 1) 本番S3→ローカルへ管理ファイルDL（無ければ初回扱い） ======
		boolean seqExists = downloadIfExists(statsBucket, seqKeyFinal, localSeqPath, "seqList.txt download");
		downloadIfExists(statsBucket, teamKeyFinal, localTeamPath, "data_team_list.txt download");

		// ====== 2) 現在作成済みCSV読み込み（既存ロジック） ======
		bean.init();

		// ====== 3) DBから現在のグルーピングを作る ======
		List<List<Integer>> currentGroups = sortSeqs(); // seqでグルーピング
		currentGroups = normalizeGroups(currentGroups);

		// ====== 4) 既存 seqList 読み込み or 初回作成 ======
		FileMngWrapper fileIO = new FileMngWrapper();
		final String SEQ_LIST = localSeqPath.toString();
		final String DATA_TEAM_LIST_TXT = localTeamPath.toString();

		boolean firstRun = !seqExists || !Files.exists(localSeqPath);
		List<List<Integer>> textGroups;

		if (firstRun) {
			// 初回：従来互換（カンマ区切りで書く）
			writeSeqListCommaLines(localSeqPath, currentGroups);
			textGroups = Collections.emptyList();
		} else {
			textGroups = fileIO.readSeqBuckets(SEQ_LIST);
			textGroups = normalizeGroups(textGroups);
		}

		// ====== 5) 既存CSV情報 ======
		Map<String, List<Integer>> csvInfoRow = (bean != null ? bean.getCsvInfo() : null);
		csvInfoRow = (csvInfoRow != null) ? csvInfoRow : Collections.emptyMap();

		// ====== 6) 照合して plan 作成 ======
		CsvBuildPlan plan;
		if (firstRun) {
			// ★初回は「全部 newTargets」に積む
			CsvBuildPlan plans = new CsvBuildPlan();
			int i = 0;
			for (List<Integer> curr : currentGroups) {
				plans.newTargets.put(CSV_NEW_PREFIX + "-" + (i++), curr);
			}
			plan = plans;
		} else {
			plan = matchSeqCombPlan(textGroups, currentGroups, csvInfoRow);
		}

		// ====== 7) 条件マスタ取得 ======
		CsvArtifactResource csvArtifactResource;
		try {
			csvArtifactResource = this.helper.getData();
		} catch (Exception e) {
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION, e);
			throw e;
		}

		// ====== 8) 生成キュー作成（再作成→新規） ======
		List<SimpleEntry<String, List<DataEntity>>> ordered = new ArrayList<>();

		if (plan != null) {
			// 8-1) 再作成
			for (Map.Entry<Integer, List<Integer>> rt : plan.recreateByCsvNo.entrySet()) {
				String path = LOCAL_DIR.resolve(rt.getKey() + BookMakersCommonConst.CSV).toString();
				List<Integer> ids = normalizeSeqList(rt.getValue());

				List<DataEntity> result = fetchAndFilter(ids, csvArtifactResource, METHOD_NAME, "recreate findByData");
				if (result == null)
					continue;

				ordered.add(new SimpleEntry<>(path, result));
			}

			// 8-2) 新規（最大CSV番号はS3本番prefixから取得）
			int maxOnS3 = getMaxCsvNoFromS3(statsBucket, finalPrefix);
			int nextNo = maxOnS3 + 1;

			int diff = 0;
			for (Map.Entry<String, List<Integer>> entry : plan.newTargets.entrySet()) {
				List<Integer> ids = normalizeSeqList(entry.getValue());
				if (ids.isEmpty())
					continue;

				int csvNo = nextNo + diff;
				String path = LOCAL_DIR.resolve(csvNo + BookMakersCommonConst.CSV).toString();

				List<DataEntity> result = fetchAndFilter(ids, csvArtifactResource, METHOD_NAME, "new findByData");
				if (result == null)
					continue;

				ordered.add(new SimpleEntry<>(path, result));
				diff++;
			}
		}

		// ====== 9) 既存CSVと一致するものは除外 ======
		List<SimpleEntry<String, List<DataEntity>>> toCreate = new ArrayList<>();
		for (SimpleEntry<String, List<DataEntity>> ord : ordered) {
			boolean match = matchCsvInfo(csvInfoRow, ord.getValue());
			if (!match)
				toCreate.add(ord);
		}

		if (toCreate.isEmpty()) {
			String messageCd = MessageCdConst.MCD00014I_NO_MAP_DATA;
			String fillChar = "追加レコードがないため処理終了 (既存CSV数: " + csvInfoRow.size() + "件)";
			endLog(METHOD_NAME, messageCd, fillChar);
			return;
		}

		// ====== 10) 並列生成→ローカル書込→S3 tmpへPUT ======
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

		// tmpへPUTするキーを収集（commit対象）
		List<String> tmpPutKeys = new ArrayList<>();

		for (int i = 0; i < futures.size(); i++) {
			try {
				CsvArtifact art = futures.get(i).join();
				if (art == null || art.getContent() == null || art.getContent().isEmpty()) {
					continue;
				}

				// 1) ローカルへ書く
				writeLocalCsv(art);

				// 2) S3 tmpへPUT（ステージング）
				String tmpKey = putLocalFileToTmp(statsBucket, tmpPrefix, Paths.get(art.getFilePath()));
				tmpPutKeys.add(tmpKey);

				success++;
				succeeded.add(toCreate.get(i));
			} catch (Exception ex) {
				failed++;
				failedEntries.add(toCreate.get(i));
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION, ex,
						"CSV作成/PUT(tmp) 失敗");
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
				"CSVステージング結果(tmp put) (成功: " + success + "件, 失敗: " + failed + "件)");

		// ====== 11) data_team_list.txt 更新（ローカル）→ S3 tmpへPUT ======
		try {
			upsertDataTeamList(Paths.get(DATA_TEAM_LIST_TXT), succeeded, failedEntries);
			String tmpKeyTeam = putLocalFileToTmp(statsBucket, tmpPrefix, localTeamPath);
			tmpPutKeys.add(tmpKeyTeam);
		} catch (Exception e) {
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION, e,
					"data_team_list.txt 更新/PUT(tmp) 失敗");
			cleanupTmp(statsBucket, tmpPrefix);
			throw (e instanceof IOException) ? (IOException) e : new IOException(e);
		}

		// ====== 12) seqList.txt 更新（ローカル）→ S3 tmpへPUT ======
		try {
			// 従来互換（カンマ区切り）
			writeSeqListCommaLines(localSeqPath, currentGroups);
			String tmpKeySeq = putLocalFileToTmp(statsBucket, tmpPrefix, localSeqPath);
			tmpPutKeys.add(tmpKeySeq);
		} catch (Exception e) {
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION, e,
					"seqList.txt 更新/PUT(tmp) 失敗");
			cleanupTmp(statsBucket, tmpPrefix);
			throw (e instanceof IOException) ? (IOException) e : new IOException(e);
		}

		// ====== 13) commit: tmp → 本番へ copy & tmp delete ======
		if (failed > 0) {
			this.manageLoggerComponent.debugWarnLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00099I_LOG,
					"失敗があるためcommitしません。tmpPrefixに残します: " + tmpPrefix);
			endLog(METHOD_NAME, null, null);
			return;
		}

		try {
			commitTmpToFinal(statsBucket, tmpPrefix, finalPrefix);
		} catch (Exception e) {
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION, e,
					"commit(tmp->final) 失敗 tmpPrefix=" + tmpPrefix);
			throw (e instanceof IOException) ? (IOException) e : new IOException(e);
		}

		endLog(METHOD_NAME, null, null);
	}

	// =========================================================
	// localOnly（S3一切なし）
	// =========================================================
	private void executeLocalOnly(Path outDir) throws IOException {
		final String METHOD_NAME = "executeLocalOnly";

		// ローカル作業場所
		final Path LOCAL_DIR = outDir;
		ensureDir(LOCAL_DIR);

		final Path localSeqPath = LOCAL_DIR.resolve("seqList.txt");
		final Path localTeamPath = LOCAL_DIR.resolve("data_team_list.txt");

		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG,
				"localOnly=true: S3処理を完全にスキップします。localDir=" + LOCAL_DIR);

		// localOnlyでは GetStatInfo 経由のS3参照を避けたい場合があるため、ここは安全に try
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

		// DBから現在のグルーピング
		List<List<Integer>> currentGroups = sortSeqs();
		currentGroups = normalizeGroups(currentGroups);

		// seqList.txt 既存読み込み（ローカル基準）
		FileMngWrapper fileIO = new FileMngWrapper();
		final String SEQ_LIST = localSeqPath.toString();
		final String DATA_TEAM_LIST_TXT = localTeamPath.toString();

		boolean firstRun = !Files.exists(localSeqPath);
		List<List<Integer>> textGroups;

		if (firstRun) {
			writeSeqListCommaLines(localSeqPath, currentGroups);
			textGroups = Collections.emptyList();
		} else {
			textGroups = fileIO.readSeqBuckets(SEQ_LIST);
			textGroups = normalizeGroups(textGroups);
		}

		// plan 作成
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

		// 条件マスタ取得
		CsvArtifactResource csvArtifactResource;
		try {
			csvArtifactResource = this.helper.getData();
		} catch (Exception e) {
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION, e);
			throw (e instanceof IOException) ? (IOException) e : new IOException(e);
		}

		// 生成キュー（再作成→新規）
		List<SimpleEntry<String, List<DataEntity>>> ordered = new ArrayList<>();

		if (plan != null) {
			// 再作成
			for (Map.Entry<Integer, List<Integer>> rt : plan.recreateByCsvNo.entrySet()) {
				String path = LOCAL_DIR.resolve(rt.getKey() + BookMakersCommonConst.CSV).toString();
				List<Integer> ids = normalizeSeqList(rt.getValue());

				List<DataEntity> result = fetchAndFilter(ids, csvArtifactResource, METHOD_NAME, "recreate findByData");
				if (result == null)
					continue;

				ordered.add(new SimpleEntry<>(path, result));
			}

			// 新規（S3最大番号ではなく、ローカル最大番号）
			int maxLocal = getMaxCsvNoFromLocal(LOCAL_DIR);
			int nextNo = maxLocal + 1;

			int diff = 0;
			for (Map.Entry<String, List<Integer>> entry : plan.newTargets.entrySet()) {
				List<Integer> ids = normalizeSeqList(entry.getValue());
				if (ids.isEmpty())
					continue;

				int csvNo = nextNo + diff;
				String path = LOCAL_DIR.resolve(csvNo + BookMakersCommonConst.CSV).toString();

				List<DataEntity> result = fetchAndFilter(ids, csvArtifactResource, METHOD_NAME, "new findByData");
				if (result == null)
					continue;

				ordered.add(new SimpleEntry<>(path, result));
				diff++;
			}
		}

		// 既存CSVと一致するものは除外
		List<SimpleEntry<String, List<DataEntity>>> toCreate = new ArrayList<>();
		for (SimpleEntry<String, List<DataEntity>> ord : ordered) {
			boolean match = matchCsvInfo(csvInfoRow, ord.getValue());
			if (!match)
				toCreate.add(ord);
		}

		if (toCreate.isEmpty()) {
			String messageCd = MessageCdConst.MCD00014I_NO_MAP_DATA;
			String fillChar = "追加レコードがないため処理終了 (既存CSV数: " + csvInfoRow.size() + "件)";
			endLog(METHOD_NAME, messageCd, fillChar);
			return;
		}

		// 並列生成→ローカル書込（S3 PUTなし）
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

		// data_team_list.txt 更新（ローカルTSV）
		try {
			upsertDataTeamList(Paths.get(DATA_TEAM_LIST_TXT), succeeded, failedEntries);
		} catch (Exception e) {
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION, e,
					"data_team_list.txt 更新 失敗");
			throw (e instanceof IOException) ? (IOException) e : new IOException(e);
		}

		// seqList.txt 更新（ローカル、カンマ区切り）
		try {
			writeSeqListCommaLines(localSeqPath, currentGroups);
		} catch (Exception e) {
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION, e,
					"seqList.txt 更新 失敗");
			throw (e instanceof IOException) ? (IOException) e : new IOException(e);
		}

		if (failed > 0) {
			this.manageLoggerComponent.debugWarnLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00099I_LOG,
					"localOnly: 失敗があるため一部未生成の可能性があります。localDir=" + LOCAL_DIR);
		}

		endLog(METHOD_NAME, null, null);
	}

	// =========================================================
	// S3 tmp ステージング関連
	// =========================================================

	private String putLocalFileToTmp(String bucket, String tmpPrefix, Path localFile) {
		final String METHOD_NAME = "putLocalFileToTmp";
		String fileName = localFile.getFileName().toString();
		String tmpKey = s3Operator.buildKey(tmpPrefix, fileName);

		try {
			s3Operator.uploadFile(bucket, tmpKey, localFile);
			return tmpKey;
		} catch (Exception e) {
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00023E_S3_UPLOAD_FAILED, e,
					bucket, tmpKey);
			throw e;
		}
	}

	private void commitTmpToFinal(String bucket, String tmpPrefix, String finalPrefix) {
		final String METHOD_NAME = "commitTmpToFinal";

		List<String> tmpKeys = s3Operator.listKeys(bucket, tmpPrefix);
		for (String tmpKey : tmpKeys) {
			if (tmpKey == null)
				continue;

			String fileName = Paths.get(tmpKey).getFileName().toString();
			String finalKey = s3Operator.buildKey(finalPrefix, fileName);

			s3Operator.copy(bucket, tmpKey, bucket, finalKey);
			s3Operator.delete(bucket, tmpKey);
		}

		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME,
				MessageCdConst.MCD00099I_LOG,
				"commit完了 tmpPrefix=" + tmpPrefix + " -> finalPrefix=" + (finalPrefix == null ? "" : finalPrefix));
	}

	private void cleanupTmp(String bucket, String tmpPrefix) {
		try {
			List<String> tmpKeys = s3Operator.listKeys(bucket, tmpPrefix);
			for (String k : tmpKeys) {
				try {
					s3Operator.delete(bucket, k);
				} catch (Exception ignore) {
				}
			}
		} catch (Exception ignore) {
		}
	}

	/**
	 * S3（本番prefix）にある CSV の最大番号を取得する
	 */
	private int getMaxCsvNoFromS3(String bucket, String finalPrefix) {
		final String METHOD_NAME = "getMaxCsvNoFromS3";
		String prefix = (finalPrefix == null) ? "" : finalPrefix;

		List<String> keys = s3Operator.listKeys(bucket, prefix);
		int max = 0;

		for (String key : keys) {
			if (key == null)
				continue;
			Matcher m = CSV_KEY_PATTERN.matcher(key);
			if (!m.matches())
				continue;

			try {
				int n = Integer.parseInt(m.group(1));
				if (n > max)
					max = n;
			} catch (NumberFormatException ignore) {
			}
		}

		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG,
				"S3上の最大CSV番号=" + max + " (bucket=" + bucket + ", prefix=" + prefix + ")");
		return max;
	}

	/**
	 * localOnly用：ローカルディレクトリにある CSV の最大番号を取得する
	 */
	private int getMaxCsvNoFromLocal(Path localDir) {
		final String METHOD_NAME = "getMaxCsvNoFromLocal";
		int max = 0;

		try {
			if (localDir == null || !Files.isDirectory(localDir)) {
				return 0;
			}

			try (var stream = Files.list(localDir)) {
				for (Path p : stream.collect(Collectors.toList())) {
					if (p == null)
						continue;
					String name = p.getFileName().toString();
					Matcher m = CSV_NO_PATTERN.matcher(name);
					if (!m.find())
						continue;

					try {
						int n = Integer.parseInt(m.group(2));
						if (n > max)
							max = n;
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
		if (groups == null)
			return Collections.emptyList();
		List<List<Integer>> out = new ArrayList<>();
		for (List<Integer> g : groups) {
			List<Integer> ng = normalizeSeqListStatic(g);
			if (!ng.isEmpty())
				out.add(ng);
		}
		return out;
	}

	private List<Integer> normalizeSeqList(List<Integer> src) {
		return normalizeSeqListStatic(src);
	}

	private static List<Integer> normalizeSeqListStatic(List<Integer> src) {
		if (src == null || src.isEmpty())
			return Collections.emptyList();
		List<Integer> ids = new ArrayList<>(src.size());
		for (Integer n : new TreeSet<>(src)) {
			if (n != null)
				ids.add(n);
		}
		return ids;
	}

	private List<DataEntity> fetchAndFilter(
			List<Integer> ids,
			CsvArtifactResource csvArtifactResource,
			String parentMethod,
			String label) {

		if (ids == null || ids.isEmpty())
			return null;

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

		if (!this.helper.csvCondition(result, csvArtifactResource))
			return null;

		result = this.helper.abnormalChk(result);
		if (result == null || result.isEmpty())
			return null;

		backfillScores(result);

		// ★ここを追加（CSV書き込み前に data_category を揃える）
		applyCanonicalCategory(result);

		return result;
	}

	private CsvArtifact buildCsvArtifact(String path, List<DataEntity> result, CsvArtifactResource resource) {
		if (result == null || result.isEmpty())
			return null;
		return new CsvArtifact(path, result);
	}

	private void writeLocalCsv(CsvArtifact art) {
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
			if (resourceBuilder.length() > 0)
				resourceBuilder.append("-");
			resourceBuilder.append(d.getSeq());
		}
		for (Map.Entry<String, List<Integer>> list : csvInfoRow.entrySet()) {
			List<Integer> vals = list.getValue();
			if (vals == null || vals.isEmpty())
				continue;

			StringBuilder sBuilder = new StringBuilder();
			for (Integer d : vals) {
				if (sBuilder.length() > 0)
					sBuilder.append("-");
				sBuilder.append(d);
			}
			if (resourceBuilder.toString().equals(sBuilder.toString())) {
				return true;
			}
		}
		return false;
	}

	// =========================================================
	// 差分計画（recreate/new）生成
	// =========================================================

	private CsvBuildPlan matchSeqCombPlan(
			List<List<Integer>> textSeqs,
			List<List<Integer>> dbSeqs,
			Map<String, List<Integer>> csvInfoRow) {

		CsvBuildPlan plan = new CsvBuildPlan();

		// 既存CSV情報 -> csvNoマップ
		Map<Integer, Integer> minSeqToCsvNo = new LinkedHashMap<>();
		Map<String, Integer> groupKeyToCsvNo = new LinkedHashMap<>();

		for (Map.Entry<String, List<Integer>> e : csvInfoRow.entrySet()) {
			Integer csvNo = parseCsvNo(e.getKey());
			if (csvNo == null)
				continue;

			List<Integer> ids = normalizeSeqListStatic(e.getValue());
			if (ids.isEmpty())
				continue;

			int min = ids.get(0);
			minSeqToCsvNo.put(min, csvNo);
			groupKeyToCsvNo.put(groupKey(ids), csvNo);
		}

		// DBグループ -> 既存一致チェック
		for (List<Integer> dbGroup : dbSeqs) {
			if (dbGroup == null || dbGroup.isEmpty())
				continue;

			String gk = groupKey(dbGroup);

			// 既存CSVに完全一致するなら何もしない
			if (groupKeyToCsvNo.containsKey(gk)) {
				continue;
			}

			// minSeqが一致するCSVがあるなら、そのCSV番号を再生成対象にする
			int min = dbGroup.get(0);
			Integer csvNo = minSeqToCsvNo.get(min);
			if (csvNo != null) {
				plan.recreateByCsvNo.put(csvNo, dbGroup);
			} else {
				// 新規
				plan.newTargets.put(CSV_NEW_PREFIX + "-" + min, dbGroup);
			}
		}

		return plan;
	}

	private static String groupKey(List<Integer> ids) {
		StringBuilder sb = new StringBuilder();
		for (Integer n : ids) {
			if (n == null)
				continue;
			if (sb.length() > 0)
				sb.append('-');
			sb.append(n);
		}
		return sb.toString();
	}

	private static Integer parseCsvNo(String keyOrName) {
		if (keyOrName == null)
			return null;
		Matcher m = CSV_NO_PATTERN.matcher(keyOrName);
		if (!m.find())
			return null;
		try {
			return Integer.valueOf(m.group(2));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	// =========================================================
	// data_team_list.txt 更新（TSV: "N.csv\t説明"）
	// =========================================================

	/**
	 * data_team_list.txt を CSV番号単位で upsert する。
	 *
	 * フォーマット（希望形式）：
	 *   1.csv\t説明
	 *   2.csv\t説明
	 *
	 * succeeded: 追加/更新
	 * failed   : 該当csvNo行を削除
	 */
	private void upsertDataTeamList(
			Path out,
			List<SimpleEntry<String, List<DataEntity>>> succeeded,
			List<SimpleEntry<String, List<DataEntity>>> failed) throws IOException {

		Map<Integer, String> csvNoToLine = new LinkedHashMap<>();

		// 既存読み込み（TSV）
		if (Files.exists(out)) {
			List<String> lines = Files.readAllLines(out, StandardCharsets.UTF_8);
			for (String line : lines) {
				if (line == null)
					continue;
				String t = line.trim();
				if (t.isEmpty())
					continue;

				String[] parts = t.split("\t", 2);
				if (parts.length < 1)
					continue;

				Integer csvNo = parseCsvNo(parts[0].trim());
				if (csvNo == null)
					continue;

				String desc = (parts.length >= 2) ? parts[1] : "";
				csvNoToLine.put(csvNo, (csvNo + BookMakersCommonConst.CSV) + "\t" + desc);
			}
		} else {
			Files.createDirectories(out.getParent());
			Files.writeString(out, "", StandardCharsets.UTF_8);
		}

		// failed は削除
		if (failed != null) {
			for (SimpleEntry<String, List<DataEntity>> e : failed) {
				Integer csvNo = csvNoFromFilePath(e.getKey());
				if (csvNo != null) {
					csvNoToLine.remove(csvNo);
				}
			}
		}

		// succeeded は upsert（説明は最低限：カテゴリ - Home vs Away）
		if (succeeded != null) {
			for (SimpleEntry<String, List<DataEntity>> e : succeeded) {
				Integer csvNo = csvNoFromFilePath(e.getKey());
				List<DataEntity> list = e.getValue();
				if (csvNo == null || list == null || list.isEmpty())
					continue;

				DataEntity head = list.get(0);

				String dataCategory = safe(head.getDataCategory()).trim();
				String home = safe(head.getHomeTeamName()).trim();
				String away = safe(head.getAwayTeamName()).trim();

				String vsPart;
				if (!home.isEmpty() && !away.isEmpty()) {
					vsPart = home + " vs " + away;
				} else if (!home.isEmpty()) {
					vsPart = home;
				} else if (!away.isEmpty()) {
					vsPart = away;
				} else {
					vsPart = "";
				}

				String desc;
				if (!dataCategory.isEmpty() && !vsPart.isEmpty()) {
					desc = dataCategory + " - " + vsPart;
				} else if (!dataCategory.isEmpty()) {
					desc = dataCategory;
				} else {
					desc = vsPart; // どれも空なら空行になるが、最低限の形は保つ
				}

				String line = (csvNo + BookMakersCommonConst.CSV) + "\t" + desc;
				csvNoToLine.put(csvNo, line);
			}
		}

		// 書き戻し（csvNo昇順）
		List<Map.Entry<Integer, String>> entries = new ArrayList<>(csvNoToLine.entrySet());
		entries.sort(Map.Entry.comparingByKey());

		List<String> outLines = new ArrayList<>();
		for (Map.Entry<Integer, String> en : entries) {
			outLines.add(en.getValue());
		}

		Files.write(out, outLines, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
	}

	private static Integer csvNoFromFilePath(String filePath) {
		if (filePath == null)
			return null;
		// filePath は "/.../4710.csv" のはず
		return parseCsvNo(filePath);
	}

	private static String safe(String s) {
		return (s == null) ? "" : s;
	}

	// =========================================================
	// score補完など
	// =========================================================

	private static void backfillScores(List<DataEntity> list) {
		if (list == null || list.isEmpty())
			return;

		list.sort(Comparator.comparingInt(d -> {
			try {
				return Integer.parseInt(Objects.toString(d.getSeq(), "0"));
			} catch (NumberFormatException e) {
				return Integer.MAX_VALUE;
			}
		}));

		String lastHome = null;
		String lastAway = null;
		for (DataEntity d : list) {
			if (isBlank(d.getHomeScore()) && lastHome != null)
				d.setHomeScore(lastHome);
			if (isBlank(d.getAwayScore()) && lastAway != null)
				d.setAwayScore(lastAway);

			if (!isBlank(d.getHomeScore()))
				lastHome = d.getHomeScore();
			if (!isBlank(d.getAwayScore()))
				lastAway = d.getAwayScore();
		}
	}

	private static boolean hasRound(String s) {
		return s != null && ROUND_TOKEN.matcher(s).find();
	}

	private static String pickCanonicalCategory(List<DataEntity> group) {
		if (group == null || group.isEmpty())
			return "";

		// 1) ラウンド入りの文字列を優先して代表値にする
		for (DataEntity d : group) {
			String cat = d.getDataCategory();
			if (hasRound(cat))
				return cat.trim();
		}
		// 2) 無ければ先頭をフォールバック
		String first = group.get(0).getDataCategory();
		return first == null ? "" : first.trim();
	}

	private static void applyCanonicalCategory(List<DataEntity> group) {
		String canonical = pickCanonicalCategory(group);
		if (canonical.isBlank())
			return;

		for (DataEntity d : group) {
			String cat = d.getDataCategory();
			// 「ラウンドが消えてる」or「空」を canonical に差し替え
			if (cat == null || cat.trim().isEmpty() || !hasRound(cat)) {
				d.setDataCategory(canonical);
			}
		}
	}

	/**
	 * seqList.txt を従来互換（カンマ区切り）で書く
	 * 1行=1グループ
	 */
	private void writeSeqListCommaLines(Path out, List<List<Integer>> groups) throws IOException {
		String body = groups.stream()
				.map(list -> list.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")))
				.collect(java.util.stream.Collectors.joining("\n")) + "\n";
		Files.writeString(out, body, StandardCharsets.UTF_8,
				StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
	}

	private static boolean isBlank(String s) {
		return s == null || s.trim().isEmpty();
	}

	private void endLog(String method, String messageCd, String fillChar) {
		if (messageCd != null && fillChar != null) {
			this.manageLoggerComponent.debugInfoLog(PROJECT_NAME, CLASS_NAME, method, messageCd, fillChar);
		}
		this.manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, method, "end");
	}
}
