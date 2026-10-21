package dev.batch.bm_b015;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * B015（ファイルアップロードの DB チェック・反映）で使う定数
 *
 * <p>状態・エラーの段階・S3 のフォルダ・CSV の列名は web 側（CSV アップロード画面・状況管理画面）とそろえる。</p>
 * @author shiraishitoshio
 */
public final class FileUploadBatchConst {

	private FileUploadBatchConst() {
	}

	// =========================================================
	// file_upload_status_manage.status / error_phase
	// =========================================================

	/** 判定前（web のバリデーションを通り uploaded/ に置かれたもの。B015 の処理対象） */
	public static final String STATUS_PENDING = "PENDING";

	/** 判定中（B015 が処理している） */
	public static final String STATUS_PROCESSING = "PROCESSING";

	/** 正常終了（反映して success/ に置いた） */
	public static final String STATUS_SUCCESS = "SUCCESS";

	/** 異常終了（error/ に _error.zip を置いた） */
	public static final String STATUS_ERROR = "ERROR";

	/** エラーの段階: S3 からファイルを取り出せない */
	public static final String PHASE_STORE = "STORE";

	/** エラーの段階: DB チェック・反映 */
	public static final String PHASE_DB = "DB";

	// =========================================================
	// 種類
	// =========================================================

	public static final String MASTER_SEASON = "season";
	public static final String MASTER_TEAM = "team";
	public static final String MASTER_FUTURE = "future";
	public static final String MASTER_REALTIME = "realtime";
	/** データカテゴリ統一（static_data・future_master のカテゴリ書き換え。2026-10-21 追加） */
	public static final String MASTER_DATA_CATEGORY = "dataCategory";

	// =========================================================
	// S3（aws-s3-upload-check-csv）
	// =========================================================

	public static final String DIR_UPLOADED = "uploaded/";
	public static final String DIR_SUCCESS = "success/";
	public static final String DIR_ERROR = "error/";

	/** エラーファイルの名前の末尾 */
	public static final String ERROR_SUFFIX = "_error";

	/** エラーファイルに足す列 */
	public static final String ERROR_COLUMN = "エラー詳細";

	// =========================================================
	// 実行の設定
	// =========================================================

	/** 1 回の実行で処理する件数の上限 */
	public static final int MAX_PER_RUN = 50;

	/** 判定中のまま止まっているとみなす時間（分） */
	public static final int STALE_PROCESSING_MINUTES = 60;

	/** 状況管理に残すエラー行の上限 */
	public static final int MAX_SAVED_ERRORS = 1000;

	/** DB の register_id / update_id */
	public static final String CSV_USER = "CSV_UPLOAD";

	/** 状況管理の update_id */
	public static final String BATCH_USER = "B015";

	/** del_flg */
	public static final String DEL_FLG_SHOW = "0";
	public static final String DEL_FLG_HIDE = "1";

	// =========================================================
	// CSV の列名（フォーマットの見出し）
	// =========================================================

	public static final String COL_OPERATION = "処理区分";
	public static final String COL_ID = "ID";
	public static final String COL_COUNTRY = "国";
	public static final String COL_LEAGUE = "リーグ";
	public static final String COL_SEASON_YEAR = "シーズン年";
	public static final String COL_START_SEASON_DATE = "シーズン開始日";
	public static final String COL_END_SEASON_DATE = "シーズン終了日";
	public static final String COL_ROUND = "ラウンド数";
	public static final String COL_PATH = "パス";
	public static final String COL_ICON = "アイコン";
	public static final String COL_VALID_FLG = "有効フラグ";
	public static final String COL_TEAM = "チーム";
	public static final String COL_LINK = "リンク";
	public static final String COL_GAME_TEAM_CATEGORY = "試合国及びカテゴリ";
	public static final String COL_FUTURE_TIME = "試合予定時間";
	public static final String COL_HOME_RANK = "ホーム順位";
	public static final String COL_AWAY_RANK = "アウェー順位";
	public static final String COL_HOME_TEAM = "ホームチーム";
	public static final String COL_AWAY_TEAM = "アウェーチーム";
	public static final String COL_HOME_MAX_SCORER = "ホームチーム最大得点取得者";
	public static final String COL_AWAY_MAX_SCORER = "アウェーチーム最大得点取得者";
	public static final String COL_HOME_TEAM_HOME_SCORE = "ホームチームホーム得点";
	public static final String COL_HOME_TEAM_HOME_LOST = "ホームチームホーム失点";
	public static final String COL_AWAY_TEAM_HOME_SCORE = "アウェーチームホーム得点";
	public static final String COL_AWAY_TEAM_HOME_LOST = "アウェーチームホーム失点";
	public static final String COL_HOME_TEAM_AWAY_SCORE = "ホームチームアウェー得点";
	public static final String COL_HOME_TEAM_AWAY_LOST = "ホームチームアウェー失点";
	public static final String COL_AWAY_TEAM_AWAY_SCORE = "アウェーチームアウェー得点";
	public static final String COL_AWAY_TEAM_AWAY_LOST = "アウェーチームアウェー失点";
	public static final String COL_GAME_LINK = "試合リンク文字列";
	public static final String COL_DATA_TIME = "データ取得時間";
	public static final String COL_DATA_CATEGORY = "データカテゴリ";
	public static final String COL_CURRENT_DATA_CATEGORY = "現在のデータカテゴリ";
	public static final String COL_NEW_DATA_CATEGORY = "新しいデータカテゴリ";

	/** 種類ごとの列（この列が見出しに無ければファイルのエラー） */
	public static List<String> columnsOf(String masterName) {
		if (MASTER_SEASON.equals(masterName)) {
			return Arrays.asList(COL_OPERATION, COL_ID, COL_COUNTRY, COL_LEAGUE, COL_SEASON_YEAR,
					COL_START_SEASON_DATE, COL_END_SEASON_DATE, COL_ROUND, COL_PATH, COL_ICON, COL_VALID_FLG);
		}
		if (MASTER_TEAM.equals(masterName)) {
			return Arrays.asList(COL_OPERATION, COL_ID, COL_COUNTRY, COL_LEAGUE, COL_TEAM, COL_LINK);
		}
		if (MASTER_FUTURE.equals(masterName)) {
			return Arrays.asList(COL_OPERATION, COL_ID, COL_GAME_TEAM_CATEGORY, COL_FUTURE_TIME,
					COL_HOME_RANK, COL_AWAY_RANK, COL_HOME_TEAM, COL_AWAY_TEAM, COL_HOME_MAX_SCORER, COL_AWAY_MAX_SCORER,
					COL_HOME_TEAM_HOME_SCORE, COL_HOME_TEAM_HOME_LOST, COL_AWAY_TEAM_HOME_SCORE, COL_AWAY_TEAM_HOME_LOST,
					COL_HOME_TEAM_AWAY_SCORE, COL_HOME_TEAM_AWAY_LOST, COL_AWAY_TEAM_AWAY_SCORE, COL_AWAY_TEAM_AWAY_LOST,
					COL_GAME_LINK, COL_DATA_TIME);
		}
		if (MASTER_REALTIME.equals(masterName)) {
			return Arrays.asList(COL_OPERATION, COL_HOME_TEAM, COL_AWAY_TEAM, COL_DATA_CATEGORY);
		}
		if (MASTER_DATA_CATEGORY.equals(masterName)) {
			// 後ろの参考の列（件数・国リーグ判定など）は読まない
			return Arrays.asList(COL_OPERATION, COL_HOME_TEAM, COL_AWAY_TEAM, COL_CURRENT_DATA_CATEGORY,
					COL_NEW_DATA_CATEGORY);
		}
		return Collections.emptyList();
	}

	// =========================================================
	// メッセージ
	// =========================================================

	public static final String MSG_PROCESSING = "判定中です。（B015 で DB チェック・反映をしています）";
	public static final String MSG_SUCCESS = "正常終了しました。（登録 %d 件 / 更新 %d 件 / 削除 %d 件 / 非表示 %d 件 / 再表示 %d 件）";
	public static final String MSG_SUCCESS_STORE_FAILED = "（success フォルダへの移動に失敗しました。DB への反映は済んでいます）";
	public static final String MSG_DB_CHECK_ERROR = "DB チェックでエラーがあったため反映しませんでした。（エラー %d 件）エラー内容を書き足したファイルをダウンロードできます。";
	public static final String MSG_DB_APPLY_ERROR = "DB への反映に失敗したため、すべて元に戻しました。（エラー %d 件）エラー内容を書き足したファイルをダウンロードできます。";
	public static final String MSG_ERROR_FILE_STORE_FAILED = "（エラーファイルの保管に失敗しました）";
	public static final String MSG_DOWNLOAD_FAILED = "アップロードファイルを S3 から取り出せなかったため、処理できませんでした。もう一度アップロードしてください。（%s）";
	public static final String MSG_UNKNOWN_MASTER = "種類が正しくありません。（%s）";
	public static final String MSG_STALE = "判定中のまま " + STALE_PROCESSING_MINUTES + " 分以上たったため、異常終了にしました。反映されたかどうかを確認してから、必要ならもう一度アップロードしてください。";

	public static final String MSG_NO_DATA = "データ行がありません。";
	public static final String MSG_FILE_KEY = "ファイル";
	public static final String ERROR_DELIMITER = "／";
}
