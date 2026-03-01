package dev.common.constant;

/**
 * メッセージコード定数クラス
 * @author shiraishitoshio
 *
 */
public class MessageCdConst {

	/** MCD00001I: 処理が正常終了しました。(バッチ名: {1}) */
	public static final String MCD00001I_BATCH_EXECUTION_GREEN_FIN = "MCD00001I";

	/** MCD00002I: 処理が途中で正常終了しました。(備考: {1}) */
	public static final String MCD00002I_BATCH_EXECUTION_SKIP = "MCD00002I";

	/** MCD00003I: 処理をスキップします。(備考: {1}) */
	public static final String MCD00003I_EXECUTION_SKIP = "MCD00003I";

	/** MCD00004I: 処理が正常終了しました。(備考: {1}) */
	public static final String MCD00004I_OTHER_EXECUTION_GREEN_FIN = "MCD00004I";

	/** MCD00005I: 登録処理が成功しました。(備考: {1}) */
	public static final String MCD00005I_INSERT_SUCCESS = "MCD00005I";

	/** MCD00006I: 更新処理が成功しました。(備考: {1}) */
	public static final String MCD00006I_UPDATE_SUCCESS = "MCD00006I";

	/** MCD00007I: 削除処理が成功しました。(備考: {1}) */
	public static final String MCD00007I_DELETE_SUCCESS = "MCD00007I";

	/** MCD00008I: 得点が発生しませんでしたのでスキップします。(備考: {1}) */
	public static final String MCD00008I_NO_SCORE_SKIP = "MCD00008I";

	/** MCD00009I: 重複or競合が起きたので再取得登録しました。(備考: {1}) */
	public static final String MCD00009I_REINSERT_DUE_TO_DUPLICATION_OR_COMPETITION = "MCD00009I";

	/** MCD00010I: キュー監視モニター用ログです。(備考: {1}) */
	public static final String MCD00010I_MONITORING_QUEUE_LOG = "MCD00010I";

	/** MCD00011I: バルク登録処理が成功しました。(備考: {1}) */
	public static final String MCD00011I_BULKINSERT_SUCCESS = "MCD00011I";

	/** MCD00012I: 件数反映処理が成功しました。(備考: {1}) */
	public static final String MCD00012I_COUNTER_REFLECTION_SUCCESS = "MCD00012I";

	/** MCD00013I: 「終了済」データがありません。*/
	public static final String MCD00013I_NO_FIN_DATA = "MCD00013I";

	/** MCD00014I: マップデータがありませんでしたのでスキップします。(備考: {1}) */
	public static final String MCD00014I_NO_MAP_DATA = "MCD00014I";

	/** MCD00015I: バッチ処理を受け付けました。(備考: {1}) */
	public static final String MCD00015I_BATCH_ACCEPTED = "MCD00015I";

	/** MCD00016I: ファイルを削除しました。(備考: {1},ファイルパス: {2})) */
	public static final String MCD00016I_FILE_DELETED = "MCD00016I";

	/** MCD00017I: 削除対象のファイルがありませんでした。(備考: {1}) */
	public static final String MCD00017I_NO_FILE_DELETED = "MCD00017I";

	/** MCD00099I: 途中ログ (備考: {1}) */
	public static final String MCD00099I_LOG = "MCD00099I";

	/** MCD00001W: 国リーグ分割失敗警告です。(備考: {1}) */
	public static final String MCD00001W_COUNTRY_LEAGUE_SPLIT_FAIL_WARNING = "MCD00001W";

	/** MCD00002W: 重複警告です。(備考: {1}) */
	public static final String MCD00002W_DUPLICATION_WARNING = "MCD00002W";

	/** MCD00001E: 処理が異常終了しました。(バッチ名: {1}) */
	public static final String MCD00001E_BATCH_EXECUTION_GREEN_FIN = "MCD00001E";

	/** MCD00002E: 処理が途中で異常終了しました。(理由: {1}) */
	public static final String MCD00002E_BATCH_EXECUTION_SKIP = "MCD00002E";

	/** MCD00003E: 処理をスキップします。(理由: {1}) */
	public static final String MCD00003E_EXECUTION_SKIP = "MCD00003E";

	/** MCD00004E: スレッドに割り込みが発生しました。処理を中断します。(理由: {1}) */
	public static final String MCD00004E_THREAD_INTERRUPTION = "MCD00004E";

	/** MCD00005E: 処理が異常終了しました。(理由: {1}) */
	public static final String MCD00005E_OTHER_EXECUTION_GREEN_FIN = "MCD00005E";

	/** MCD00006E: 非同期エラーが発生しました。処理を中断します。(理由: {1}) */
	public static final String MCD00006E_ASYNCHRONOUS_ERROR = "MCD00006E";

	/** MCD00007E: 登録処理が失敗しました。(理由: {1}) */
	public static final String MCD00007E_INSERT_FAILED = "MCD00007E";

	/** MCD00008E: 更新処理が失敗しました。(理由: {1}) */
	public static final String MCD00008E_UPDATE_FAILED = "MCD00008E";

	/** MCD00009E: 削除処理が失敗しました。(理由: {1}) */
	public static final String MCD00009E_DELETE_FAILED = "MCD00009E";

	/** MCD00010E: テーブル名の解決ができませんでした。(理由: {1}) */
	public static final String MCD00010E_TABLE_NAME_ERROR = "MCD00010E";

	/** MCD00011E: バルク登録処理が失敗しました。(理由: {1}) */
	public static final String MCD00011E_BULKINSERT_FAILED = "MCD00011E";

	/** MCD00012E: 件数反映処理が失敗しました。(理由: {1}) */
	public static final String MCD00012E_COUNTER_REFLECTION_FAILED = "MCD00012E";

	/** MCD00013E: 初期化エラーが発生しました。(理由: {1}) */
	public static final String MCD00013E_INITILIZATION_ERROR = "MCD00013E";

	/** MCD00014E: リフレクションエラーが発生しました。(理由: {1}) */
	public static final String MCD00014E_REFLECTION_ERROR = "MCD00014E";

	/** MCD00015E: 数値変換エラーが発生しました。(理由: {1}) */
	public static final String MCD00015E_NUMBERFORMAT_ERROR = "MCD00015E";

	/** MCD00016E: 形式変換エラーが発生しました。(理由: {1}) */
	public static final String MCD00016E_FORMAT_ERROR = "MCD00016E";

	/** MCD00017E: 暗号化に失敗しました。(理由: {1}) */
	public static final String MCD00017E_ENCRYPTION_ERROR = "MCD00017E";

	/** MCD00018E: マージ失敗しました。(理由: {1}) */
	public static final String MCD00018E_MERGE_ERROR = "MCD00018E";

	/** MCD00019E: 重複エラーです。(理由: {1}) */
	public static final String MCD00019E_DUPLICATION_ERROR = "MCD00019E";

	/** MCD00020E: 日付エラーです。(理由: {1}) */
	public static final String MCD00020E_DATE_ERROR = "MCD00020E";

	/** MCD00021E: ファイルの削除に失敗しました。(備考: {1},ファイルパス: {2}) */
	public static final String MCD00021E_FILE_DELETED_FAILED = "MCD00021E";

	/** MCD00022E: CSVファイルが見つかりませんでした。(備考: {1}) */
	public static final String MCD00022E_NO_FOUND_CSV_FILE = "MCD00022E";

	/** MCD00023E: S3へのアップロードが失敗しました。(バケット名: {1}, ファイル名: {2}) */
	public static final String MCD00023E_S3_UPLOAD_FAILED = "MCD00023E";

	/** MCD00024E: ファイル記入エラーが発生しました。(ファイル名: {1}) */
	public static final String MCD00024E_WRITE_FILE_FAILED = "MCD00024E";

	/** MCD00025E: S3からのダウンロードが失敗しました。(バケット名: {1}, ファイル名: {2}) */
	public static final String MCD00025E_S3_DOWNLOAD_FAILED = "MCD00025E";

	/** MCD00099E: 予期せぬ例外が発生しました。(理由: {1}) */
	public static final String MCD00099E_UNEXPECTED_EXCEPTION = "MCD00099E";

}
