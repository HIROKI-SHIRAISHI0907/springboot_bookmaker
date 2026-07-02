package dev.web.api.bm_a016;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dev.web.api.bm_a007.S3FileCountService;
import dev.web.repository.bm.MatchKeyRepository;
import dev.web.wrapper.BatchFileCheckItemWrapper;
import dev.web.wrapper.BatchFileCheckResponseWrapper;
import dev.web.wrapper.BatchFileCheckTaskWrapper;

/**
 * バッチ実行前のS3ファイル状態判定サービス
 */
@Service
public class BatchFileCheckService {

	/** バケット定義 */
	private static final String BUCKET_TEAM_MEMBER = "aws-s3-team-member-csv";
	private static final String BUCKET_SEASON = "aws-s3-season-csv";
	private static final String BUCKET_TEAM = "aws-s3-team-csv";
	private static final String BUCKET_FUTURE = "aws-s3-future-csv";
	private static final String BUCKET_STAT = "aws-s3-stat-csv";
	private static final String BUCKET_OUTPUTS = "aws-s3-outputs-csv";
	private static final String BUCKET_ALL_LEAGUE = "aws-s3-all-league-csv";
	private static final String BUCKET_GEOGRAFIC = "aws-s3-geografic-csv";

	/** 共通キー */
	private static final String JSON_B001_COUNTRY_LEAGUE = "json/b001_country_league.json";
	private static final String FILE_DATA_TEAM_LIST = "data_team_list.txt";
	private static final String FILE_SEQ_LIST = "seqList.txt";
	private static final String FILE_SEASON_DATA = "season_data.csv";
	private static final String FILE_ALL_LEAGUE_DATA = "all_league_master.csv";
	private static final String FILE_GEOGRAFIC_INPUT_DATA = "output/b015_team_location.csv";

	@Autowired
	private S3FileCountService s3FileCountService;

	@Autowired
	private MatchKeyRepository matchKeyRepository;

	/**
	 * 全タスク分の状態を返す
	 */
	public BatchFileCheckResponseWrapper getAllStatuses() {
		List<BatchFileCheckTaskWrapper> tasks = new ArrayList<>();

		tasks.add(buildB002());
		tasks.add(buildB003());
		tasks.add(buildB004());
		tasks.add(buildB005());
		tasks.add(buildB006());
		tasks.add(buildB007());
		tasks.add(buildB008());
		tasks.add(buildB010());
		tasks.add(buildB011());
		tasks.add(buildB012());

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
	 * B006
	 * aws-s3-stat-csv にある
	 * - data_team_list.txt
	 * - seqList.txt
	 * の存在有無
	 * および
	 * - 上記2ファイルを除外した直フォルダ数
	 * - 上記2ファイルを除外した直ファイル数
	 * を表示
	 */
	private BatchFileCheckTaskWrapper buildB006() {
		String bucket = BUCKET_STAT;

		boolean dataTeamListExists = exists(bucket, FILE_DATA_TEAM_LIST);
		boolean seqListExists = exists(bucket, FILE_SEQ_LIST);

		long directFolderCount = countDirectFoldersExcluding(bucket, Set.of());
		long directFileCount = countDirectFilesExcluding(bucket, Set.of(FILE_DATA_TEAM_LIST, FILE_SEQ_LIST));

		boolean ready = dataTeamListExists && seqListExists;

		List<BatchFileCheckItemWrapper> items = new ArrayList<>();
		items.add(fileItem("data_team_list.txt", bucket, FILE_DATA_TEAM_LIST, dataTeamListExists, true, "txt"));
		items.add(fileItem("seqList.txt", bucket, FILE_SEQ_LIST, seqListExists, true, "txt"));
		items.add(countItem("直フォルダ数（除外後）", bucket, directFolderCount, false, true));
		items.add(countItem("直ファイル数（data_team_list.txt / seqList.txt 除外後）", bucket, directFileCount, false, true));

		return task("B006", ready, ready ? "準備OK" : "必須不足", items);
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
	 * match_key_service テーブル件数が 0以上なら実行可
	 * = 件数取得に成功すれば ready
	 */
	private BatchFileCheckTaskWrapper buildB010() {
		long count = -1L;
		boolean success = false;

		try {
			count = this.matchKeyRepository.countAll();
			success = count >= 0;
		} catch (Exception e) {
			success = false;
		}

		boolean ready = success;

		List<BatchFileCheckItemWrapper> items = new ArrayList<>();
		items.add(countItem("match_key_service 件数", "DB", success ? count : null, false, success));

		return task("B010", ready, ready ? "準備OK" : "件数取得失敗", items);
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
	 * B012
	 * aws-s3-geografic-csv の outputs/b015_geografic_input.json が存在
	 */
	private BatchFileCheckTaskWrapper buildB012() {
		String bucket = BUCKET_GEOGRAFIC;

		boolean geograficDataExists = exists(bucket, FILE_GEOGRAFIC_INPUT_DATA);

		boolean ready = geograficDataExists;

		List<BatchFileCheckItemWrapper> items = new ArrayList<>();
		items.add(fileItem("b015_team_location.csv", bucket, FILE_GEOGRAFIC_INPUT_DATA, geograficDataExists, true, "csv"));

		return task("B012", ready, summary(ready), items);
	}

	// =========================================================
	// Helper
	// =========================================================

	private boolean exists(String bucket, String key) {
		try {
			return this.s3FileCountService.existsObject(bucket, key);
		} catch (Exception e) {
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
