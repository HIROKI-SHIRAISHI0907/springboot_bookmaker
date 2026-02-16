package dev.web.api.bm_w020;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
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
 * StatデータCSV出力ロジック
 * - グルーピング: data_category, home_team_name, away_team_name 単位で seq をまとめる
 * - 既存テキスト(seqList.txt)と照合して、再作成/新規を分類
 * - CSV名は <連番番号>.csv （連番=グループ内の最小seq）
 * - 生成は並列、書き込みは順序保証で直列
 */
@Component
public class ExportCsv {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = ExportCsv.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = ExportCsv.class.getSimpleName();

	/** 新規でCSV作成をするときのダミー文字列 */
	private static final String CSV_NEW_PREFIX = "mk";

	/** S3Operatorクラス */
	@Autowired
	private S3Operator s3Operator;

	/** Configクラス */
	@Autowired
	private PathConfig config;

	/** ReaderCurrentCsvInfoBeanクラス */
	@Autowired
	private ReaderCurrentCsvInfoBean bean;

	/** CsvArtifactHelperクラス */
	@Autowired
	private CsvArtifactHelper helper;

	/** BookCsvDataRepositoryレポジトリクラス */
	@Autowired
	private BookCsvDataRepository bookCsvDataRepository;

	/** ログ管理 */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * CSV作成処理
	 * @throws IOException
	 */
	public void execute() throws IOException {
		final String METHOD_NAME = "execute";
		this.manageLoggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		// statsバケット名（aws-s3-stat-csv）
		String statsBucket = config.getS3BucketsStats();

		// S3キー（直下なら ""、stats/ 配下なら "stats" を入れる）
		String statsPrefix = "";

		String seqKey = s3Operator.buildKey(statsPrefix, "seqList.txt");
		String teamKey = s3Operator.buildKey(statsPrefix, "data_team_list.txt");

		// ローカルに落とす先（既存のFileMngWrapper/Files.existsがそのまま使える）
		Path localSeqPath = Paths.get(config.getCsvFolder(), "seqList.txt");
		Path localTeamPath = Paths.get(config.getCsvFolder(), "data_team_list.txt");

		// S3 → ローカルへダウンロード（S3に無い場合は例外になるので、必要ならcatchして firstRun 扱いに）
		try {
			s3Operator.downloadToFile(statsBucket, seqKey, localSeqPath);
		} catch (Exception ignore) {
			// seqList.txt がS3に無い = 初回相当
			String messageCd = MessageCdConst.MCD00099I_LOG;
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, ignore,
					"seqList.txtがありません。(statsBucket: " + statsBucket +
							"seqKey: " + seqKey + "localSeqPath: " + localSeqPath + ")");
		}
		try {
			s3Operator.downloadToFile(statsBucket, teamKey, localTeamPath);
		} catch (Exception ignore) {
			// data_team_list.txt がS3に無い場合もあり得る
			String messageCd = MessageCdConst.MCD00099I_LOG;
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, ignore,
					"data_team_list.txtがありません。(statsBucket: " + statsBucket +
							"teamKey: " + teamKey + "localTeamPath: " + localTeamPath + ")");
		}

		// 連番組み合わせリスト（過去のグルーピングを保存）
		final String SEQ_LIST = localSeqPath.toString();

		// データチームリスト
		final String DATA_TEAM_LIST_TXT = localTeamPath.toString();

		// パス
		final Path CSV_FOLDER = Paths.get(config.getCsvFolder());

		// 0) 現在作成済みのCSV読み込み
		bean.init();

		// 1) 現在のグルーピングをDBから作る
		List<List<Integer>> currentGroups = sortSeqs();

		// 2) 既存の seqList を読み込み or 初回作成
		FileMngWrapper fileIO = new FileMngWrapper();
		Path seqListPath = Paths.get(SEQ_LIST);
		List<List<Integer>> textGroups;

		boolean firstRun = !Files.exists(seqListPath);
		this.manageLoggerComponent.debugInfoLog(
			    PROJECT_NAME, CLASS_NAME, METHOD_NAME,
			    MessageCdConst.MCD00099I_LOG,
			    "firstRun=" + firstRun
			      + ", seqListExists=" + Files.exists(seqListPath)
			      + ", hasLocalCsv=" + (!firstRun ? "unknown" : "maybe false")
			);
		if (firstRun) {
			// 初回：全て新規対象。先に現在の定義を書き出しておく（異常終了でも痕跡が残る）
			fileIO.write(SEQ_LIST, currentGroups.toString());
			textGroups = Collections.emptyList();
		} else {
			// 1つ前のアプリケーション起動時に記入したseqList(作成されたCSV番号と同組み合わせとは限らない)
			textGroups = fileIO.readSeqBuckets(SEQ_LIST);
			// csvが作成されていなければfirstRunをtrueに再変換
			firstRun = (!anyFileMatch(CSV_FOLDER)) ? true : false;
		}

		// 既存CSV(すでに作成されている対戦チーム-CSV番号キー,通番リスト)
		Map<String, List<Integer>> csvInfoRow = (bean != null ? bean.getCsvInfo() : null);

		this.manageLoggerComponent.debugInfoLog(
			    PROJECT_NAME, CLASS_NAME, METHOD_NAME,
			    MessageCdConst.MCD00099I_LOG,
			    "calling matchSeqCombPlan"
			);

		// 3) 照合して「完全一致なら終了」/「新規・再作成」に分類
		CsvBuildPlan plan = null;
		if (firstRun) {
			CsvBuildPlan plans = new CsvBuildPlan();
			for (List<Integer> curr : currentGroups) {
				plans.onlyNew(CSV_NEW_PREFIX, curr);
			}
			plan = plans;
		} else {
			plan = matchSeqCombPlan(textGroups, currentGroups, csvInfoRow);
		}

		// 4) stat_size_finalize_masterからフラグ条件(0)に当てはまるものを取得(0-0以降,1-0以降など)
		CsvArtifactResource csvArtifactResource = null;
		try {
			csvArtifactResource = this.helper.getData();
		} catch (Exception e) {
			String messageCd = MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION;
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e);
			throw e;
		}

		// ここまでで作成するCSVグルーピングとものによっては再作成するCSVの番号が確定しているはず

		// 5) 生成キューを作成（再作成→新規の順で、連番昇順に処理）
		List<SimpleEntry<String, List<DataEntity>>> ordered = new ArrayList<>();
		if (plan != null) {
			for (Map.Entry<Integer, List<Integer>> rt : plan.recreateByCsvNo.entrySet()) {
				String path = CSV_FOLDER.resolve(rt.getKey() + BookMakersCommonConst.CSV).toString();
				List<Integer> ids = new ArrayList<>(rt.getValue().size());
				for (Integer n : new TreeSet<>(rt.getValue())) { // 昇順
					if (n != null)
						ids.add(n);
				}
				List<DataEntity> result = null;
				try {
					result = this.bookCsvDataRepository.findByData(ids);
				} catch (Exception e) {
					String messageCd = MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION;
					this.manageLoggerComponent.debugErrorLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e,
							"生成キューを作成(this.bookCsvDataRepository.findByData)");
					throw e;
				}
				if (!this.helper.csvCondition(result, csvArtifactResource)) {
					continue;
				}
				// 異常データの判定（終了済の後にゴミデータが混入,通番通りだが時系列データになっていないなど）
				result = this.helper.abnormalChk(result);
				if (result.isEmpty())
					continue;
				backfillScores(result);
				ordered.add(new SimpleEntry<>(path, result));
			}

			// 6) 新規（Map<String, List<List<Integer>>> を二重ループ）csv番号を最大採番から連番でつける
			int maxNumber = searchMaxCsvFileNumber(CSV_FOLDER.toString()) + 1;
			int diff = 0;
			for (Map.Entry<String, List<Integer>> entry : plan.newTargets.entrySet()) {
				List<Integer> newList = entry.getValue();
				if (newList == null || newList.isEmpty())
					continue;

				int key = maxNumber + diff;
				String path = CSV_FOLDER.resolve(key + BookMakersCommonConst.CSV).toString();
				List<Integer> ids = new ArrayList<>(newList.size());
				for (Integer n : new TreeSet<>(newList)) { // 昇順
					if (n != null)
						ids.add(n);
				}
				List<DataEntity> result = null;
				try {
					result = this.bookCsvDataRepository.findByData(ids);
				} catch (Exception e) {
					String messageCd = MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION;
					this.manageLoggerComponent.debugErrorLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
							e, "csv番号を最大採番から連番でつける(this.bookCsvDataRepository.findByData)");
					throw e;
				}
				if (!this.helper.csvCondition(result, csvArtifactResource)) {
					continue;
				}
				// 異常データの判定（終了済の後にゴミデータが混入,通番通りだが時系列データになっていないなど）
				result = this.helper.abnormalChk(result);
				if (result.isEmpty())
					continue;
				backfillScores(result);
				ordered.add(new SimpleEntry<>(path, result));
				diff++;
			}
		}

		// 7) 既存CSVの組み合わせ通番データとフラグ条件に該当する通番データが一致するか
		List<SimpleEntry<String, List<DataEntity>>> newOrdered = new ArrayList<>();
		for (Map.Entry<String, List<DataEntity>> ord : ordered) {
			boolean match = matchCsvInfo(csvInfoRow, ord.getValue());
			// 一致しないものがあった時点で作成対象
			if (!match) {
				newOrdered.add(new SimpleEntry<>(ord.getKey(), ord.getValue()));
			}
		}

		// firstRunがfalseの時のみ判定状態
		if (newOrdered.isEmpty() && ordered.isEmpty()) {
			// 完全一致 → 作業不要
			String messageCd = MessageCdConst.MCD00014I_NO_MAP_DATA;
			String fillChar = "追加レコードがないため処理終了 (既存CSV数: " + csvInfoRow.size() + "件)";
			endLog(METHOD_NAME, messageCd, fillChar);
			return;
		}

		// 新規作成,既存CSVの書き換えを設定済

		// 8) 並列でCSV内容生成 → 連番順に直列書き込み
		ensureDir(CSV_FOLDER.toString());
		if (ordered.isEmpty()) {
			String messageCd = MessageCdConst.MCD00014I_NO_MAP_DATA;
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, "CSV作成結果(対象なし (0件))");
		} else {
			int threads = Math.max(2, Runtime.getRuntime().availableProcessors() - 1);
			ExecutorService pool = Executors.newFixedThreadPool(threads);

			// ordered（<csvNo, group>）の順序で Future を作成
			List<CompletableFuture<CsvArtifact>> futures = new ArrayList<>(
					ordered.size());
			for (SimpleEntry<String, List<DataEntity>> e : ordered) {
				final CsvArtifactResource resource = csvArtifactResource;
				final String path = e.getKey();
				final List<DataEntity> group = e.getValue();
				futures.add(
						CompletableFuture.supplyAsync(() -> buildCsvArtifact(path, group, resource), pool));
			}

			int success = 0, failed = 0;
			List<SimpleEntry<String, List<DataEntity>>> succeeded = new ArrayList<>();
			List<SimpleEntry<String, List<DataEntity>>> failedEntries = new ArrayList<>();
			// join も ordered と同じ順番で行う → 書き込み順序が連番昇順で保証される
			for (int i = 0; i < futures.size(); i++) {
				try {
					CsvArtifact art = futures.get(i).join(); // 生成完了待ち
					if (art == null) {
						failed++;
						continue;
					}
					// 条件付きだった場合
					if (art.getContent().isEmpty()) {
						continue;
					}
					// S3へアップロード
					writeCsvArtifact(art, statsBucket, statsPrefix);
					success++;
					succeeded.add(ordered.get(i));
					// エラーの場合は次のアプリケーション起動時に作成される想定
				} catch (Exception ex) {
					failed++;
					failedEntries.add(ordered.get(i));
					String messageCd = MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION;
					this.manageLoggerComponent.debugErrorLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, ex, "書き込み順序が連番昇順で保証(CSV作成失敗)");
				}
			}
			pool.shutdown();
			try {
				pool.awaitTermination(1, TimeUnit.MINUTES);
			} catch (InterruptedException ignore) {
				Thread.currentThread().interrupt();
			}

			String messageCd = MessageCdConst.MCD00099I_LOG;
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
					"CSV作成結果 (成功: " + success + "件, 失敗: " + failed + "件, 合計: " + (success + failed) + "件)");

			// 9) data_team_list.txt を “成功分を反映し、失敗には『作成失敗』を付加”して原子置換
			try {
				upsertDataTeamList(Paths.get(DATA_TEAM_LIST_TXT), this.config.getCsvFolder(), succeeded, failedEntries);
			} catch (IOException ex) {
				messageCd = MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION;
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, ex, "data_team_list.txt 更新失敗");
				throw ex;
			}

		}

		// 10) 処理完了後、seqList.txt を最新状態で上書き（将来の差分計算基準）
		if (!firstRun) {
			// 上書きで保存（追記はNG）
			fileIO.overwrite(SEQ_LIST, currentGroups.toString());

			// S3へアップロード（最新状態を保存）
			try {
				s3Operator.uploadFile(statsBucket, seqKey, localSeqPath);
			} catch (Exception e) {
				String messageCd = MessageCdConst.MCD00023E_S3_UPLOAD_FAILED;
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e,
						statsBucket, "seqList.txt");
			}
		}

		// data_team_list.txt も更新後にS3へアップロード
		// upsertDataTeamList(...) の直後（成功/失敗問わずファイルが更新されうるタイミング）で:
		try {
			if (Files.exists(localTeamPath)) {
				s3Operator.uploadFile(statsBucket, teamKey, localTeamPath);
			}
		} catch (Exception e) {
			String messageCd = MessageCdConst.MCD00023E_S3_UPLOAD_FAILED;
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, statsBucket, "data_team_list.txt");
		}

		endLog(METHOD_NAME, null, null);
	}

	/**
	 * DB から同一データの組み合わせに並び替える
	 */
	private List<List<Integer>> sortSeqs() {
		List<SeqWithKey> rows = this.bookCsvDataRepository.findAllSeqsWithKey();
		List<List<Integer>> result = new ArrayList<>();
		List<Integer> bucket = new ArrayList<>();
		String prevCat = null, prevHome = null, prevAway = null;

		for (SeqWithKey r : rows) {
			boolean newGroup = prevCat == null
					|| !prevCat.equals(r.getDataCategory())
					|| !prevHome.equals(r.getHomeTeamName())
					|| !prevAway.equals(r.getAwayTeamName());

			if (newGroup) {
				if (!bucket.isEmpty()) {
					bucket.sort(Comparator.naturalOrder()); // ★ ここで昇順ソート
					result.add(bucket);
				}
				bucket = new ArrayList<>();
				prevCat = r.getDataCategory();
				prevHome = r.getHomeTeamName();
				prevAway = r.getAwayTeamName();
			}
			// null 予防を入れるなら以下の if でガード
			if (r.getSeq() != null) {
				bucket.add(r.getSeq());
			}
		}
		if (!bucket.isEmpty()) {
			result.add(bucket);
		}
		return result;
	}

	// ======== 照合・分類 ========

	/**
	 * text と db の“組み合わせ集合（順不同）”が完全一致なら null（早期終了）。
	 * 一致しない場合は、db を「再作成」/「新規」に分類し返す。
	 * 再作成判定：ReaderCurrentCsvInfoBean#getCsvInfo() の情報を用い、
	 *             グループ内の通番が 1 つでも既存CSVに含まれれば該当。
	 * その際、どの <CSV番号>.csv に載っていたかを保持（最小CSV番号を採用）。
	 */
	private CsvBuildPlan matchSeqCombPlan(List<List<Integer>> textSeqs, List<List<Integer>> dbSeqs,
			Map<String, List<Integer>> csvInfoRow) {
		final String METHOD_NAME = "matchSeqCombPlan";

		textSeqs = (textSeqs != null) ? textSeqs : Collections.emptyList();
		dbSeqs = (dbSeqs != null) ? dbSeqs : Collections.emptyList();

		// 1) 追加通番を取得
		Set<String> textKeys = toKeySet(textSeqs);
		Set<String> dbKeys = toKeySet(dbSeqs);

		// 2) 組み合わせに入っていない通番を判定する。ただしdbKeysの方が少なければスキップ
		List<String> onlyInDb = new ArrayList<>(dbKeys);
		if (onlyInDb.size() > textKeys.size()) {
			onlyInDb.removeAll(textKeys);
			String messageCd = MessageCdConst.MCD00099I_LOG;
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null,
					"組み合わせに入っていない通番による追加通番件数 (" + onlyInDb.size() + "件)");
		}

		CsvBuildPlan plan = new CsvBuildPlan();
		// 2) DB側の組み合わせと既存CSV側の組み合わせを比較し存在する通番リスト群に紐づくCSV番号を取得
		int same = 0;
		int contains = 0;
		int news = 0;
		for (List<Integer> comb : dbSeqs) {
			Integer priorCsvNo = null;
			// 既存のCSVのCSV番号キーと組み合わせ通番
			String flg = "";
			for (Map.Entry<String, List<Integer>> e : csvInfoRow.entrySet()) {
				String versus_csvNo = e.getKey();
				List<Integer> existed = e.getValue();
				// 通番組み合わせが含まれている場合は対象
				flg = chkComb(comb, existed);
				// 一致
				if ("S".equals(flg)) {
					same++;
					break;
					// 含まれている
				} else if ("T".equals(flg)) {
					try {
						priorCsvNo = Integer.parseInt(versus_csvNo.split("_")[2]);
					} catch (NumberFormatException | ArrayIndexOutOfBoundsException ex) {
						String messageCd = MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION;
						this.manageLoggerComponent.debugErrorLog(
								PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, ex,
								"照合・分類の整数変換でエラー (" + versus_csvNo + ")");
					}
					contains++;
					break;
				}
			}

			if ("F".equals(flg)) {
				news++;
			}

			if (priorCsvNo != null) {
				plan.onlyRecreate(priorCsvNo, comb); // 再作成
			} else if ("F".equals(flg)) {
				plan.onlyNew(CSV_NEW_PREFIX, comb); // 新規
			}
		}

		String messageCd = MessageCdConst.MCD00099I_LOG;
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
				String.format("同一: %d件, 含有: %d件, 新規: %d件, 全体作成可能数: %d件",
						same, contains, news, dbSeqs.size()));
		return plan;
	}

	/** groups を正規化して“昇順キー集合”にする */
	private static Set<String> toKeySet(List<List<Integer>> groups) {
		if (groups == null)
			return new TreeSet<>(Comparator.comparingLong(Long::parseLong));
		return groups.stream()
				.filter(Objects::nonNull)
				.flatMap(List::stream)
				.filter(Objects::nonNull)
				.map(Object::toString)
				.collect(Collectors.toCollection(
						() -> new TreeSet<>(Comparator.comparingLong(Long::parseLong))));
	}

	/**
	 * 通番リストが完全に含まれているかを調査
	 * @param dbComb DB側の通番リスト
	 * @param existedCsvComb 既存CSV側の通番リスト
	 * @return S: 同一データ, T: 含まれる, F: 含まれていない
	 */
	private String chkComb(List<Integer> dbComb, List<Integer> existedCsvComb) {
		int dbs = dbComb.get(0);
		int csvs = existedCsvComb.get(0);
		if (!existedCsvComb.contains(dbs) && !dbComb.contains(csvs)) {
			return "F";
		}

		String dbCombData = "";
		for (Integer d : dbComb) {
			if (dbCombData.length() > 0) {
				dbCombData += "-";
			}
			dbCombData += d;
		}
		String existedCsvCombData = "";
		for (Integer e : existedCsvComb) {
			if (existedCsvCombData.length() > 0) {
				existedCsvCombData += "-";
			}
			existedCsvCombData += e;
		}

		if (dbCombData.equals(existedCsvCombData)) {
			return "S";
		}

		return (dbCombData.contains(existedCsvCombData)
				|| existedCsvCombData.contains(dbCombData)) ? "T" : "F";
	}

	/**
	 * 既存CSVの組み合わせ通番データとフラグ条件に該当する通番データが一致するか
	 * @param csvInfoRow
	 * @param resource 条件で絞ったresource
	 * @return
	 */
	private boolean matchCsvInfo(Map<String, List<Integer>> csvInfoRow,
			List<DataEntity> resource) {
		StringBuilder resourceBuilder = new StringBuilder();
		for (DataEntity d : resource) {
			if (resourceBuilder.toString().length() > 0) {
				resourceBuilder.append("-");
			}
			resourceBuilder.append(d.getSeq());
		}
		for (Map.Entry<String, List<Integer>> list : csvInfoRow.entrySet()) {
			StringBuilder sBuilder = new StringBuilder();
			for (Integer d : list.getValue()) {
				if (sBuilder.toString().length() > 0) {
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

	/**
	 * CSV存在
	 * @param CSV_FOLDER
	 * @return
	 * @throws IOException
	 */
	private boolean anyFileMatch(Path CSV_FOLDER) throws IOException {
		try (Stream<Path> stream = Files.list(CSV_FOLDER)) { // 深さ1（再帰しない）
			boolean hasCsv = stream
					.filter(Files::isRegularFile)
					.map(p -> p.getFileName().toString().toLowerCase())
					.anyMatch(name -> name.endsWith(BookMakersCommonConst.CSV));
			return hasCsv;
		}
	}

	/**
	 * 最大の数字を持つCSV番号を探す（現仕様では未使用。必要なら利用可能）
	 */
	private int searchMaxCsvFileNumber(String dirs) {
		final String METHOD_NAME = "searchMaxCsvFileNumber";
		File directory = new File(dirs);
		File[] files = directory.listFiles((dir, name) -> name.matches("\\d+\\.csv"));
		int maxFileNumber = Integer.MIN_VALUE;
		if (files != null && files.length > 0) {
			for (File f : files) {
				String name = f.getName();
				int dot = name.indexOf('.');
				int num = Integer.parseInt(name.substring(0, dot));
				if (num > maxFileNumber)
					maxFileNumber = num;
			}
			if (maxFileNumber != Integer.MIN_VALUE) {
				this.manageLoggerComponent.debugInfoLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, "", null,
						"最大のファイル番号は: " + maxFileNumber + ".csv");
			} else {
				String messageCd = MessageCdConst.MCD00022E_NO_FOUND_CSV_FILE;
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null);
				this.manageLoggerComponent.createSystemException(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null, null);
			}
		} else {
			return 0;
		}
		return maxFileNumber;
	}

	// ======== CSV 生成・書き込み ========

	/**
	 * 並列ステージ：CSVの中身を構築して返す（重い処理はここで）
	 * @param path
	 * @param seqGroup
	 * @param csvArtifactResource
	 * @return
	 */
	private CsvArtifact buildCsvArtifact(String path, List<DataEntity> result,
			CsvArtifactResource csvArtifactResource) {
		if (result == null || result.isEmpty())
			return null;
		return new CsvArtifact(path, result);
	}

	/** 直列ステージ：ファイルへ書き込み（上書き可・順序保証） */
	private void writeCsvArtifact(CsvArtifact art, String bucket, String prefix) {
		final String METHOD_NAME = "writeCsvArtifact";
		FileMngWrapper fw = new FileMngWrapper();
		fw.csvWrite(art.getFilePath(), art.getContent());

		// ここからS3アップロード
		Path local = Paths.get(art.getFilePath());
		String fileName = local.getFileName().toString(); // 例: "123.csv"
		String key = s3Operator.buildKey(prefix, fileName); // 例: "stats/123.csv" or "123.csv"

		try {
			s3Operator.uploadFile(bucket, key, local);
		} catch (Exception e) {
			// ログは出すが例外エラーにはしない
			String messageCd = MessageCdConst.MCD00023E_S3_UPLOAD_FAILED;
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, bucket, fileName);
		}
	}

	/** ディレクトリ作成（存在すれば何もしない） */
	private static void ensureDir(String dir) {
		try {
			Files.createDirectories(Paths.get(dir));
		} catch (Exception ignore) {
			/* no-op */ }
	}

	/**
	 * data_team_list.txt を更新（原子置換）。
	 * - 既存行は維持
	 * - 成功した CSV は該当ファイル行を「<file>: <round>-<home>vs<away>」で上書き/追加
	 * - 失敗した CSV は上記末尾に「 作成失敗」を付加して上書き/追加
	 * - ファイル名の数値順（例: 12.csv -> 12）で整列
	 */
	private void upsertDataTeamList(
			Path out,
			String baseFolder,
			List<SimpleEntry<String, List<DataEntity>>> succeeded,
			List<SimpleEntry<String, List<DataEntity>>> failed) throws IOException {

		if (out.getParent() != null)
			Files.createDirectories(out.getParent());

		// 既存を読み取り（無ければ空）
		List<String> existing = Files.exists(out)
				? Files.readAllLines(out, java.nio.charset.StandardCharsets.UTF_8)
				: new ArrayList<>();

		// 既存を map<file, line> に（キーは "123.csv" の部分）
		Map<String, String> byFile = existing.stream()
				.filter(s -> s != null && !s.isBlank())
				.map(String::trim)
				.collect(Collectors.toMap(
						s -> s.split(":")[0].trim(), // 例: "123.csv: ..."
						s -> s,
						(a, b) -> b));

		// 失敗行を反映（行末に「 作成失敗」を付与）
		if (failed != null) {
			for (SimpleEntry<String, List<DataEntity>> e : failed) {
				String file = e.getKey().replace(baseFolder, "");
				List<DataEntity> v = e.getValue();
				if (v == null || v.isEmpty())
					continue;
				String round = v.get(0).getDataCategory();
				String homeTeams = v.get(0).getHomeTeamName();
				String awayTeams = v.get(0).getAwayTeamName();
				String line = file + ": " + round + "-" + homeTeams + "vs" + awayTeams + " 作成失敗";
				byFile.put(file, line);
			}
		}

		// 成功行を反映（成功が失敗より優先して上書きされるよう、最後に put）
		if (succeeded != null) {
			for (SimpleEntry<String, List<DataEntity>> e : succeeded) {
				String file = e.getKey().replace(baseFolder, "");
				List<DataEntity> v = e.getValue();
				if (v == null || v.isEmpty())
					continue;
				String round = v.get(0).getDataCategory();
				String homeTeams = v.get(0).getHomeTeamName();
				String awayTeams = v.get(0).getAwayTeamName();
				String line = file + ": " + round + "-" + homeTeams + "vs" + awayTeams;
				byFile.put(file, line);
			}
		}

		// ファイル名の数値で並べ替え（"12.csv" → 12）
		Comparator<String> byNum = Comparator.comparingInt(s -> {
			String name = s.trim();
			int dot = name.indexOf('.');
			String num = (dot > 0 ? name.substring(0, dot) : name).replaceAll("\\D+", "");
			return num.isEmpty() ? Integer.MAX_VALUE : Integer.parseInt(num);
		});

		List<String> lines = byFile.entrySet().stream()
				.sorted(Map.Entry.comparingByKey(byNum))
				.map(Map.Entry::getValue)
				.collect(Collectors.toList());

		// 一時ファイルに書いてから原子置換（途中失敗でも旧ファイルが残る）
		Path tmp = out.resolveSibling(out.getFileName().toString() + ".tmp");
		Files.write(tmp, lines, java.nio.charset.StandardCharsets.UTF_8,
				java.nio.file.StandardOpenOption.CREATE,
				java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
		Files.move(tmp, out,
				java.nio.file.StandardCopyOption.ATOMIC_MOVE,
				java.nio.file.StandardCopyOption.REPLACE_EXISTING);
	}

	/** 空のスコアを直前レコードの値で補完（グループ内で前方参照） */
	private static void backfillScores(List<DataEntity> list) {
		if (list == null || list.isEmpty())
			return;

		// 念のため seq 昇順に整える（seq は String のため数値化）
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
			// 先に前回値を反映
			if (isBlank(d.getHomeScore()) && lastHome != null)
				d.setHomeScore(lastHome);
			if (isBlank(d.getAwayScore()) && lastAway != null)
				d.setAwayScore(lastAway);

			// 現在値を次レコードのために保持（この時点で null/blank でなければ更新）
			if (!isBlank(d.getHomeScore()))
				lastHome = d.getHomeScore();
			if (!isBlank(d.getAwayScore()))
				lastAway = d.getAwayScore();
		}
	}

	/** null/空白を判定 */
	private static boolean isBlank(String s) {
		return s == null || s.trim().isEmpty();
	}

	/** 終了ログ */
	private void endLog(String method, String messageCd, String fillChar) {
		if (messageCd != null && fillChar != null) {
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, method, messageCd, fillChar);
		}
		this.manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, method);
	}
}
