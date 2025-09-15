package dev.common.constant;

/**
 * Commonプロジェクト定数クラス
 * @author shiraishitoshio
 *
 */
public class BookMakersCommonConst {

	/** エラーコード: 正常 */
	public static final String NORMAL_CD = "0000000000";

	/** エラーコード: ファイルが存在しません。 */
	public static final String ERR_CD_NO_FILE_EXISTS = "1000000001";

	/** エラーメッセージ: ファイルが存在しません。 */
	public static final String ERR_MESSAGE_NO_FILE_EXISTS = "ファイルが存在しません。";

	/** エラーコード: ファイルが読み込めません。 */
	public static final String ERR_CD_ERR_FILE_READS = "1000000002";

	/** エラーメッセージ: ファイルが読み込めません。*/
	public static final String ERR_MESSAGE_ERR_FILE_READS = "ファイルが読み込めません。";

	/** エラーコード: ファイルが変換できません。 */
	public static final String ERR_CD_ERR_FILE_CONVERTS = "1000000003";

	/** エラーメッセージ: ファイルが変換できません。*/
	public static final String ERR_MESSAGE_ERR_FILE_CONVERTS = "ファイルが変換できません。";

	/** エラーコード: フォルダ作成でエラーが発生しました。 */
	public static final String ERR_CD_ERR_FOLDER_MAKES = "1000000004";

	/** エラーメッセージ: フォルダ作成でエラーが発生しました。*/
	public static final String ERR_MESSAGE_ERR_FOLDER_MAKES = "フォルダ作成でエラーが発生しました。";

	/** エラーコード: ファイルコピーでエラーが発生しました。 */
	public static final String ERR_CD_ERR_FILE_COPY = "1000000005";

	/** エラーメッセージ: ファイルコピーでエラーが発生しました。*/
	public static final String ERR_MESSAGE_ERR_FILE_COPY = "ファイルコピーでエラーが発生しました。";

	/** エラーコード: ファイル削除でエラーが発生しました。 */
	public static final String ERR_CD_ERR_FILE_DELETES = "1000000006";

	/** エラーメッセージ: ファイル削除でエラーが発生しました。*/
	public static final String ERR_MESSAGE_ERR_FILE_DELETES = "ファイル削除でエラーが発生しました。";

	/** エラーコード: シートが存在しません。 */
	public static final String ERR_CD_NO_SHEET_EXISTS = "1000000007";

	/** エラーメッセージ: シートが存在しません。 */
	public static final String ERR_MESSAGE_NO_SHEET_EXISTS = "シートが存在しません。";

	/** エラーコード: 異常データが検出されました。 */
	public static final String ERR_CD_ABNORMALY_DATA = "1000000008";

	/** エラーメッセージ: 異常データが検出されました。 */
	public static final String ERR_MESSAGE_ABNORMALY_DATA = "異常データが検出されました。";

	/** 定数: 通知済 */
	public static final String FIN_NOTIFICATION = "通知済";

	/** 定数: 未通知 */
	public static final String NO_NOTIFICATION = "未通知";

	/** 定数: 終了済 */
	public static final String FIN = "終了済";

	/** 定数: ハーフタイム */
	public static final String HALF_TIME = "ハーフタイム";

	/** 定数: 第一ハーフタイム */
	public static final String FIRST_HALF_TIME = "第一ハーフ";

	/** 定数: 休憩時間 */
	public static final String REST = "休憩時間";

	/** 定数: アワーデッド */
	public static final String HOUR_DEAD = "アワーデッド";

	/** 定数: 放棄 */
	public static final String ABANDONED_MATCH = "放棄";

	/** 定数: 中断中 */
	public static final String SUPENDING_GAME = "中断中";

	/** 定数: 更新まち */
	public static final String WAITING_UPDATE = "更新まち";

	/** 定数: 更新待ち */
	public static final String WAITING_UPDATE_KANJI = "更新待ち";

	/** 定数: 延期 */
	public static final String POSTPONED = "延期";

	/** メール通知対象 */
	public static final String MAIL_TARGET = "メール通知対象";

	/** メール非通知対象 */
	public static final String MAIL_ANONYMOUS_TARGET = "メール非通知対象";

	/** メール通知成功 */
	public static final String MAIL_TARGET_SUCCESS = "メール通知成功";

	/** メール通知失敗 */
	public static final String MAIL_TARGET_FAIL = "メール通知失敗";

	/** 前メール通知情報結果不明 */
	public static final String MAIL_TARGET_TO_RESULT_UNKNOWN = "前メール通知情報結果不明";

	/** 前終了済データ無し結果不明 */
	public static final String MAIL_FIN_NO_DATA_TO_RESULT_UNKNOWN = "前終了済データ無し結果不明";

	/** ゴール取り消しデータ */
	public static final String GOAL_DELETE = "ゴール取り消し";

	/** ゴール取り消しによる通知非通知変更 */
	public static final String DUE_TO_GOAL_DELETE_MAIL_TARGET_MAIL_ANONYMOUS_TARGET_ALTER = "ゴール取り消しによる通知非通知変更";

	/** ゴール取り消しによる成功失敗変更 */
	public static final String DUE_TO_GOAL_DELETE_MAIL_TARGET_SUCCESS_MAIL_TARGET_FAIL_ALTER = "ゴール取り消しによる成功失敗変更";

	/** 結果不明 */
	public static final String RESULT_UNKNOWN = "結果不明";

	/** 予期せぬエラー */
	public static final String UNEXPECTED_ERROR = "予期せぬエラー";

	/** 取得エラー */
	public static final String GET_UNEXPECTED_ERROR = "取得エラー";

	/** output_ */
	public static final String OUTPUT_ = "output_";

	/** team_member_ */
	public static final String TEAM_MEMBER_ = "team_member_";

	/** future_ */
	public static final String FUTURE_ = "future_";

	/** season_data.xlsx */
	public static final String SEASON_XLSX = "season_data.xlsx";

	/** .xlsx */
	public static final String XLSX = ".xlsx";

	/** .csv */
	public static final String CSV = ".csv";

}
