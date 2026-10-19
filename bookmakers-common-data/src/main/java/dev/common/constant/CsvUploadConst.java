package dev.common.constant;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CSV アップロード（web の受付・バリデーション／バッチサービスの反映／状況管理画面）で共通に使う定数
 *
 * <ul>
 *   <li>{@link Status}  状況管理（file_upload_status_manage.status）の状態</li>
 *   <li>{@link Phase}   異常終了した段階（file_upload_status_manage.error_phase）</li>
 *   <li>{@link Master}  アップロードの種類（URL・master_name・S3 のフォルダ名）</li>
 *   <li>{@link Prefix}  zip・CSV の名前の接頭辞</li>
 *   <li>{@link Table}   反映先テーブル（説明・ログ用）</li>
 *   <li>{@link S3Dir}   S3（aws-s3-upload-check-csv）のフォルダ</li>
 *   <li>{@link Message} 状況管理・画面に出すメッセージ</li>
 * </ul>
 *
 * @author shiraishitoshio
 */
public final class CsvUploadConst {

	private CsvUploadConst() {
	}

	// =========================================================
	// 状態（file_upload_status_manage.status）
	// =========================================================

	/**
	 * 状況管理の状態
	 * <pre>
	 * RECEIVED（受付） ─ バリデーション OK・S3 保管 OK → PENDING（判定前）─ バッチ反映 OK → SUCCESS（正常終了）
	 *                  └ バリデーション NG / S3 保管 NG → ERROR（異常終了）  └ バッチ反映 NG → ERROR（異常終了）
	 * </pre>
	 * バッチサービスが拾うのは PENDING だけ。
	 */
	public static final class Status {
		private Status() {
		}

		/** 受付（状況管理に登録した直後。バリデーション・S3 保管の前） */
		public static final String RECEIVED = "RECEIVED";
		/** 判定前（バリデーション OK。uploaded/ に置いた。バッチサービス待ち） */
		public static final String PENDING = "PENDING";
		/** 正常終了（バッチサービスで反映した） */
		public static final String SUCCESS = "SUCCESS";
		/** 異常終了（どの行も反映していない） */
		public static final String ERROR = "ERROR";

		/** 状態 → 表示名（画面の表示・絞り込み用。並び順もこの順） */
		public static final Map<String, String> LABELS;
		static {
			Map<String, String> m = new LinkedHashMap<>();
			m.put(RECEIVED, "受付");
			m.put(PENDING, "判定前");
			m.put(SUCCESS, "正常終了");
			m.put(ERROR, "異常終了");
			LABELS = Collections.unmodifiableMap(m);
		}

		/** 表示名（未知の値はそのまま返す） */
		public static String labelOf(String status) {
			return status == null ? "" : LABELS.getOrDefault(status, status);
		}

		/** 終わった状態か（正常終了・異常終了） */
		public static boolean isFinished(String status) {
			return SUCCESS.equals(status) || ERROR.equals(status);
		}
	}

	// =========================================================
	// 異常終了した段階（file_upload_status_manage.error_phase）
	// =========================================================

	/** 異常終了した段階 */
	public static final class Phase {
		private Phase() {
		}

		/** web：ファイル名・zip の中身・CSV の形・行チェック（DB は見ない） */
		public static final String VALIDATION = "VALIDATION";
		/** web：S3（uploaded/）への保管 */
		public static final String STORE = "STORE";
		/** バッチ：DB を見たチェック（キーの重複・存在しない行など） */
		public static final String DB_CHECK = "DB_CHECK";
		/** バッチ：DB への反映（すべて元に戻した） */
		public static final String APPLY = "APPLY";

		/** 段階 → 表示名 */
		public static final Map<String, String> LABELS;
		static {
			Map<String, String> m = new LinkedHashMap<>();
			m.put(VALIDATION, "バリデーション");
			m.put(STORE, "ファイル保管");
			m.put(DB_CHECK, "DBチェック");
			m.put(APPLY, "DB反映");
			LABELS = Collections.unmodifiableMap(m);
		}

		/** 表示名（未知の値はそのまま返す） */
		public static String labelOf(String phase) {
			return phase == null ? "" : LABELS.getOrDefault(phase, phase);
		}
	}

	// =========================================================
	// アップロードの種類
	// =========================================================

	/** アップロードの種類（URL の {masterName}・master_name 列・S3 のフォルダ名） */
	public static final class Master {
		private Master() {
		}

		/** シーズンデータ */
		public static final String SEASON = "season";
		/** チームデータ */
		public static final String TEAM = "team";
		/** 試合予定データ */
		public static final String FUTURE = "future";
		/** リアルタイムデータ（非表示・再表示のみ） */
		public static final String REALTIME = "realtime";

		/** 種類 → 表示名 */
		public static final Map<String, String> LABELS;
		static {
			Map<String, String> m = new LinkedHashMap<>();
			m.put(SEASON, "シーズンデータ");
			m.put(TEAM, "チームデータ");
			m.put(FUTURE, "試合予定データ");
			m.put(REALTIME, "リアルタイムデータ");
			LABELS = Collections.unmodifiableMap(m);
		}

		/** 表示名（未知の値はそのまま返す） */
		public static String labelOf(String masterName) {
			return masterName == null ? "" : LABELS.getOrDefault(masterName, masterName);
		}

		/** 決まった種類か */
		public static boolean isValid(String masterName) {
			return masterName != null && LABELS.containsKey(masterName);
		}
	}

	// =========================================================
	// zip・CSV の名前の接頭辞
	// =========================================================

	/** zip・CSV の名前の接頭辞（例: season_20261018.zip の中に season_20261018.csv） */
	public static final class Prefix {
		private Prefix() {
		}

		public static final String SEASON = Master.SEASON + "_";
		public static final String TEAM = Master.TEAM + "_";
		public static final String FUTURE = Master.FUTURE + "_";
		public static final String REALTIME = Master.REALTIME + "_";

		/** 種類 → 接頭辞（未知の種類は null） */
		public static String of(String masterName) {
			return Master.isValid(masterName) ? masterName + "_" : null;
		}
	}

	// =========================================================
	// 反映先テーブル
	// =========================================================

	/** 反映先テーブル（説明・ログ用） */
	public static final class Table {
		private Table() {
		}

		/** シーズンデータ（soccer_bm_master） */
		public static final String SEASON = "country_league_season_master";
		/** チームデータ（soccer_bm_master） */
		public static final String TEAM = "country_league_master";
		/** 試合予定データ（soccer_bm_master） */
		public static final String FUTURE = "future_master";
		/** リアルタイムデータ（soccer_bm） */
		public static final String REALTIME = "static_data";
		/** 状況管理（soccer_bm） */
		public static final String STATUS_MANAGE = "file_upload_status_manage";
	}

	// =========================================================
	// S3 のフォルダ
	// =========================================================

	/**
	 * S3（process.s3.buckets.uploadCheck ＝ aws-s3-upload-check-csv）のフォルダ
	 * <p>キーは {フォルダ}/{種類}/{yyyyMMdd}/{状況管理ID}/{ファイル名}</p>
	 */
	public static final class S3Dir {
		private S3Dir() {
		}

		/** バリデーションを通った zip（バッチサービスが取りに来る） */
		public static final String UPLOADED = "uploaded";
		/** バリデーション NG・反映 NG（{名前}_error.zip） */
		public static final String ERROR = "error";
		/** バッチサービスで反映できたもの */
		public static final String SUCCESS = "success";
	}

	// =========================================================
	// ファイル名
	// =========================================================

	/** ファイル名まわり */
	public static final class FileName {
		private FileName() {
		}

		public static final String ZIP_EXT = ".zip";
		public static final String CSV_EXT = ".csv";
		/** エラーファイルの接尾辞（{名前}_error.csv / {名前}_error.zip） */
		public static final String ERROR_SUFFIX = "_error";
		/** フォーマット名の日時部分（日本時間。例: season_20261018203000.zip） */
		public static final String FORMAT_TIMESTAMP_PATTERN = "uuuuMMddHHmmss";
		/** S3 キーの日付フォルダ（日本時間） */
		public static final String S3_DATE_PATTERN = "uuuuMMdd";
		/** タイムゾーン */
		public static final String ZONE_ID = "Asia/Tokyo";
		/** CSV の文字コード */
		public static final String CHARSET = "UTF-8";
	}

	// =========================================================
	// 上限
	// =========================================================

	/** 上限値 */
	public static final class Limit {
		private Limit() {
		}

		/** 状況管理（error_detail）に残すエラー行の上限（error フォルダの CSV には全行書く） */
		public static final int MAX_SAVED_ERRORS = 1000;
		/** file_name / csv_file_name の最大文字数 */
		public static final int FILE_NAME_LENGTH = 255;
	}

	// =========================================================
	// メッセージ
	// =========================================================

	/** 状況管理・画面に出すメッセージ */
	public static final class Message {
		private Message() {
		}

		/** 判定前（バリデーション OK） */
		public static final String PENDING = "判定前です。（バリデーションを通りました。バッチサービスで反映します）";
		/** 未ログイン（401） */
		public static final String UNAUTHORIZED = "ログインしてからアップロードしてください。";
		/** データ行なし */
		public static final String NO_DATA = "データ行がありません。";
		/** バリデーション NG（%d にエラー件数） */
		public static final String VALIDATION_ERROR = "バリデーションエラーのため受け付けませんでした。（エラー %d 件）"
				+ "エラー内容を書き足したファイルを状況管理画面からダウンロードできます。";
		/** バリデーション NG で、エラーファイルの保管にも失敗したときに足す文 */
		public static final String ERROR_FILE_STORE_FAILED = "（エラーファイルの保管に失敗しました）";
		/** S3 保管 NG */
		public static final String STORE_ERROR = "ファイルの保管に失敗したため受け付けませんでした。時間をおいてもう一度アップロードしてください。";
		/** ファイル全体のエラーを表す行のキー表示 */
		public static final String FILE_ERROR_KEY = "ファイル";
		/** 1 行に複数のエラーがあるときの区切り */
		public static final String ERROR_DELIMITER = "／";
	}
}