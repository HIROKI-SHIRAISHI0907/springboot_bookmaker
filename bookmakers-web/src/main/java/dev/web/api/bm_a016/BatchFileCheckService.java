package dev.web.api.bm_a016;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dev.web.api.bm_a007.S3FileCountService;
import dev.web.wrapper.BatchFileCheckItemWrapper;
import dev.web.wrapper.BatchFileCheckResponseWrapper;
import dev.web.wrapper.BatchFileCheckTaskWrapper;
import lombok.extern.slf4j.Slf4j;

/**
 * バッチ実行前のS3ファイル状態判定サービス
 */
@Service
@Slf4j
public class BatchFileCheckService {

	/** バケット定義 */
	private static final String BUCKET_TEAM_MEMBER = "aws-s3-team-member-csv";
	private static final String BUCKET_SEASON = "aws-s3-season-csv";
	private static final String BUCKET_TEAM = "aws-s3-team-csv";
	private static final String BUCKET_FUTURE = "aws-s3-future-csv";
	private static final String BUCKET_OUTPUTS = "aws-s3-outputs-csv";
	private static final String BUCKET_ALL_LEAGUE = "aws-s3-all-league-csv";
	private static final String BUCKET_GEOGRAFIC = "aws-s3-geografic-csv";
	private static final String BUCKET_OUTPUTS_FIN = "aws-s3-outputs-fin-csv";
	private static final String BUCKET_UPLOAD_CHECK = "aws-s3-upload-check-csv";

	/** 共通キー */
	/** B010: 日付フォルダ名（yyyy-MM-dd） */
	private static final Pattern DATE_FOLDER = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

	/** B015: 判定前 zip の置き場所 */
	private static final String UPLOADED_PREFIX = "uploaded/";

	/** 再帰カウントの深さ上限 */
	private static final int MAX_DEPTH = 3;

	private static final String JSON_B001_COUNTRY_LEAGUE = "json/b001_country_league.json";
	private static final String FILE_SEASON_DATA = "season_data.csv";
	private static final String FILE_ALL_LEAGUE_DATA = "all_league_master.csv";

	/**
	 * B014 readyFlg=false で必要なCSV
	 * 基本は b015_team_location.csv を正とする
	 */
	private static final String FILE_GEOGRAFIC_OUTPUT_DATA = "b015_team_location.csv";

	/**
	 * 念のための代替候補
	 * 実装差異や将来の配置揺れ対策
	 */
	private static final String FILE_GEOGRAFIC_OUTPUT_DATA_ALT = "temp/b015_team_location.csv";

	@Autowired
	private S3FileCountService s3FileCountService;

	/**
	 * 全タスク分の状態を返す
	 */
	public BatchFileCheckResponseWrapper getAllStatuses() {
		List<BatchFileCheckTaskWrapper> tasks = new ArrayList<>();

		// 1 タスクの S3 参照が失敗（権限不足など）しても、他のタスクの判定は返す
		tasks.add(safe("B002", this::buildB002));
		tasks.add(safe("B003", this::buildB003));
		tasks.add(safe("B004", this::buildB004));
		tasks.add(safe("B005", this::buildB005));
		tasks.add(safe("B007", this::buildB007));
		tasks.add(safe("B008", this::buildB008));
		tasks.add(safe("B010", this::buildB010));
		tasks.add(safe("B011", this::buildB011));
		tasks.add(safe("B014T", this::buildB014ReadyFlgTrue));
		tasks.add(safe("B014F", this::buildB014ReadyFlgFalse));
		tasks.add(safe("B015", this::buildB015));

		return BatchFileCheckResponseWrapper.builder()
				.tasks(tasks)
				.build();
	}

	/**
	 * B002
	 * aws-s3-team-member-csv の json/b001_country_league.json が存在
	 * かつ json フォルダ以外の直ファイルが1以上
	 */
	private BatchFileCheckTaskWrapper buildB002() {
		String bucket = BUCKET_TEAM_MEMBER;

		boolean jsonExists = exists(bucket, JSON_B001_COUNTRY_LEAGUE);
		long directFileCount = countDirectFilesExcluding(bucket, Set.of());

		boolean ready = jsonExists && directFileCount >= 1;

		List<BatchFileCheckItemWrapper> items = new ArrayList<>();
		items.add(fileItem("入力JSON", bucket, JSON_B001_COUNTRY_LEAGUE, jsonExists, true, "json"));
		items.add(countItem("直ファイル数", bucket, directFileCount, true, directFileCount >= 1));

		return task("B002", ready, summary(ready), items);
	}

	/**
	 * B003
	 * aws-s3-season-csv の json/b001_country_league.json が存在
	 * かつ season_data.csv が直下に存在
	 */
	private BatchFileCheckTaskWrapper buildB003() {
		String bucket = BUCKET_SEASON;

		boolean jsonExists = exists(bucket, JSON_B001_COUNTRY_LEAGUE);
		boolean seasonDataExists = exists(bucket, FILE_SEASON_DATA);

		boolean ready = jsonExists && seasonDataExists;

		List<BatchFileCheckItemWrapper> items = new ArrayList<>();
		items.add(fileItem("入力JSON", bucket, JSON_B001_COUNTRY_LEAGUE, jsonExists, true, "json"));
		items.add(fileItem("season_data.csv", bucket, FILE_SEASON_DATA, seasonDataExists, true, "csv"));

		return task("B003", ready, summary(ready), items);
	}

	/**
	 * B004
	 * aws-s3-team-csv の json/b001_country_league.json が存在
	 * かつ json フォルダ以外の直ファイルが1以上
	 */
	private BatchFileCheckTaskWrapper buildB004() {
		String bucket = BUCKET_TEAM;

		boolean jsonExists = exists(bucket, JSON_B001_COUNTRY_LEAGUE);
		long directFileCount = countDirectFilesExcluding(bucket, Set.of());

		boolean ready = jsonExists && directFileCount >= 1;

		List<BatchFileCheckItemWrapper> items = new ArrayList<>();
		items.add(fileItem("入力JSON", bucket, JSON_B001_COUNTRY_LEAGUE, jsonExists, true, "json"));
		items.add(countItem("直ファイル数", bucket, directFileCount, true, directFileCount >= 1));

		return task("B004", ready, summary(ready), items);
	}

	/**
	 * B005
	 * aws-s3-future-csv の json/b001_country_league.json が存在
	 * かつ json フォルダ以外の直ファイルが1以上
	 */
	private BatchFileCheckTaskWrapper buildB005() {
		String bucket = BUCKET_FUTURE;

		boolean jsonExists = exists(bucket, JSON_B001_COUNTRY_LEAGUE);
		long directFileCount = countDirectFilesExcluding(bucket, Set.of());

		boolean ready = jsonExists && directFileCount >= 1;

		List<BatchFileCheckItemWrapper> items = new ArrayList<>();
		items.add(fileItem("入力JSON", bucket, JSON_B001_COUNTRY_LEAGUE, jsonExists, true, "json"));
		items.add(countItem("直ファイル数", bucket, directFileCount, true, directFileCount >= 1));

		return task("B005", ready, summary(ready), items);
	}

	/**
	 * B007
	 * aws-s3-all-league-csv の all_league_master.csv が存在
	 */
	private BatchFileCheckTaskWrapper buildB007() {
		String bucket = BUCKET_ALL_LEAGUE;

		boolean seasonDataExists = exists(bucket, FILE_ALL_LEAGUE_DATA);

		boolean ready = seasonDataExists;

		List<BatchFileCheckItemWrapper> items = new ArrayList<>();
		items.add(fileItem("all_league_master.csv", bucket, FILE_ALL_LEAGUE_DATA, seasonDataExists, true, "csv"));

		return task("B007", ready, summary(ready), items);
	}

	/**
	 * B008
	 * aws-s3-outputs-csv の json/b001_country_league.json が存在
	 * かつ json, fin 以外の直フォルダが1以上
	 */
	private BatchFileCheckTaskWrapper buildB008() {
		String bucket = BUCKET_OUTPUTS;

		boolean jsonExists = exists(bucket, JSON_B001_COUNTRY_LEAGUE);
		long directFolderCount = countDirectFoldersExcluding(bucket, Set.of("json", "fin"));

		boolean ready = jsonExists && directFolderCount >= 1;

		List<BatchFileCheckItemWrapper> items = new ArrayList<>();
		items.add(fileItem("入力JSON", bucket, JSON_B001_COUNTRY_LEAGUE, jsonExists, true, "json"));
		items.add(countItem("対象直フォルダ数（json / fin 除外後）", bucket, directFolderCount, true, directFolderCount >= 1));

		return task("B008", ready, summary(ready), items);
	}

	/**
	 * B010
	 * aws-s3-outputs-fin-csv 直下の日付フォルダ（yyyy-MM-dd/）配下にファイルが1件以上あれば実行可能
	 * （json / fin / list フォルダや直下のファイルは対象外）
	 */
	private BatchFileCheckTaskWrapper buildB010() {
		String bucket = BUCKET_OUTPUTS_FIN;
		List<String> dateFolders = new ArrayList<>();
		for (String name : this.s3FileCountService.listDirectFolderNames(bucket)) {
			String folder = trimSlash(name);
			if (DATE_FOLDER.matcher(folder).matches()) {
				dateFolders.add(folder);
			}
		}
		long fileCount = 0;
		for (String folder : dateFolders) {
			fileCount += countFilesRecursive(bucket, folder + "/", 0, null);
		}
		boolean ready = fileCount >= 1;
		List<BatchFileCheckItemWrapper> items = new ArrayList<>();
		items.add(countItem("日付フォルダ数", bucket, (long) dateFolders.size(), false, !dateFolders.isEmpty()));
		items.add(countItem("日付フォルダ配下のファイル数", bucket, fileCount, true, ready));
		return task("B010", ready, ready ? "準備OK" : "必須不足", items);
	}

	/**
	 * B011
	 * 必須情報なし
	 * 常時実行可
	 */
	private BatchFileCheckTaskWrapper buildB011() {
	    return task("B011", true, "準備OK", new ArrayList<>());
	}

	/**
	 * B014 readyFlg=true
	 * 前提条件なしで常時実行可
	 */
	private BatchFileCheckTaskWrapper buildB014ReadyFlgTrue() {
		List<BatchFileCheckItemWrapper> items = new ArrayList<>();
		return task("B014T", true, "前提条件なし", items);
	}

	/**
	 * B014 readyFlg=false
	 * aws-s3-geografic-csv の b015_team_location.csv が存在
	 *
	 * ※ 実環境差異に備えて b015_team_location.csv も代替候補として確認
	 */
	private BatchFileCheckTaskWrapper buildB014ReadyFlgFalse() {
		String bucket = BUCKET_GEOGRAFIC;

		boolean existsPrimary = exists(bucket, FILE_GEOGRAFIC_OUTPUT_DATA);
		boolean existsAlt = exists(bucket, FILE_GEOGRAFIC_OUTPUT_DATA_ALT);

		log.info("B014F primary key={} exists={}", FILE_GEOGRAFIC_OUTPUT_DATA, existsPrimary);
		log.info("B014F alt key={} exists={}", FILE_GEOGRAFIC_OUTPUT_DATA_ALT, existsAlt);

		String resolvedKey = existsPrimary
				? FILE_GEOGRAFIC_OUTPUT_DATA
				: (existsAlt ? FILE_GEOGRAFIC_OUTPUT_DATA_ALT : null);

		boolean geograficDataExists = resolvedKey != null;

		log.info("B014F resolvedKey={} ready={}", resolvedKey, geograficDataExists);

		List<BatchFileCheckItemWrapper> items = new ArrayList<>();
		items.add(fileItem(
				"b015_team_location.csv",
				bucket,
				resolvedKey != null ? resolvedKey : FILE_GEOGRAFIC_OUTPUT_DATA,
				geograficDataExists,
				true,
				"csv"));

		String summary = geograficDataExists
				? "準備OK"
				: "必須不足（b015_team_location.csv が見つかりません）";

		return task("B014F", geograficDataExists, summary, items);
	}

	/**
	 * B015
	 * uploaded/ 配下の zip ファイルが1件以上あれば実行可能
	 */
	private BatchFileCheckTaskWrapper buildB015() {
		String bucket = BUCKET_UPLOAD_CHECK;
		// uploaded/{種類}/{yyyyMMdd}/{状況管理ID}/{zip 名}（CsvUploadArchiver）
		long uploadedZipCount = countFilesRecursive(bucket, UPLOADED_PREFIX, 0, ".zip");
		boolean ready = uploadedZipCount >= 1;
		List<BatchFileCheckItemWrapper> items = new ArrayList<>();
		items.add(countItem("uploaded/ 内の zip ファイル数", bucket, uploadedZipCount, true, ready));
		return task("B015", ready, ready ? "準備OK" : "必須不足", items);
	}


	// =========================================================
	// Helper
	// =========================================================

	private boolean exists(String bucket, String key) {
		try {
			boolean result = this.s3FileCountService.existsObject(bucket, key);
			log.info("S3 exists check bucket={} key={} result={}", bucket, key, result);
			return result;
		} catch (Exception e) {
			log.warn("S3 exists check failed bucket={} key={}", bucket, key, e);
			return false;
		}
	}

	private long countDirectFilesExcluding(String bucket, Set<String> excludeFileNames) {
		List<String> keys = this.s3FileCountService.listDirectFileKeys(bucket);

		return keys.stream()
				.map(this::fileName)
				.filter(name -> !excludeFileNames.contains(name))
				.count();
	}

	private long countDirectFoldersExcluding(String bucket, Set<String> excludeFolderNames) {
		List<String> folderNames = this.s3FileCountService.listDirectFolderNames(bucket);

		return folderNames.stream()
				.map(this::trimSlash)
				.filter(name -> !excludeFolderNames.contains(name))
				.count();
	}

	/**
	 * 1 タスク分の判定。S3 参照で例外が出たら「確認失敗」として返す
	 */
	private BatchFileCheckTaskWrapper safe(String taskCode, Supplier<BatchFileCheckTaskWrapper> builder) {
		try {
			return builder.get();
		} catch (Exception e) {
			log.warn("batch file check failed task={} : {}", taskCode, e.getMessage());
			return task(taskCode, false, "確認失敗（S3 参照エラー）", new ArrayList<>());
		}
	}

	/**
	 * prefix 配下のファイル数（サブフォルダも MAX_DEPTH まで数える。suffix 指定時はその拡張子だけ）
	 */
	private long countFilesRecursive(String bucket, String prefix, int depth, String suffix) {
		long count = this.s3FileCountService.listDirectFileKeys(bucket, prefix).stream()
				.map(this::fileName)
				.filter(name -> suffix == null || name.toLowerCase().endsWith(suffix))
				.count();
		if (depth >= MAX_DEPTH) {
			return count;
		}
		for (String sub : this.s3FileCountService.listDirectFolderNames(bucket, prefix)) {
			count += countFilesRecursive(bucket, prefix + trimSlash(sub) + "/", depth + 1, suffix);
		}
		return count;
	}

	private String fileName(String key) {
		if (key == null || key.isBlank()) {
			return "";
		}
		int idx = key.lastIndexOf('/');
		return (idx >= 0) ? key.substring(idx + 1) : key;
	}

	private String trimSlash(String s) {
		if (s == null) {
			return "";
		}
		String v = s;
		while (v.endsWith("/")) {
			v = v.substring(0, v.length() - 1);
		}
		return v;
	}

	private String summary(boolean ready) {
		return ready ? "準備OK" : "必須不足";
	}

	private BatchFileCheckTaskWrapper task(
			String taskCode,
			boolean ready,
			String summary,
			List<BatchFileCheckItemWrapper> items) {

		return BatchFileCheckTaskWrapper.builder()
				.taskCode(taskCode)
				.ready(ready)
				.summary(summary)
				.items(items)
				.build();
	}

	private BatchFileCheckItemWrapper fileItem(
			String label,
			String bucket,
			String key,
			boolean exists,
			boolean required,
			String type) {

		return BatchFileCheckItemWrapper.builder()
				.label(label)
				.bucket(bucket)
				.key(key)
				.kind("file")
				.type(type)
				.exists(exists)
				.required(required)
				.count(null)
				.build();
	}

	private BatchFileCheckItemWrapper countItem(
			String label,
			String bucket,
			Long count,
			boolean required,
			boolean exists) {

		return BatchFileCheckItemWrapper.builder()
				.label(label)
				.bucket(bucket)
				.key(null)
				.kind("count")
				.type("count")
				.exists(exists)
				.required(required)
				.count(count)
				.build();
	}
}
