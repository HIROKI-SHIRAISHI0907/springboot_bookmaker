package dev.common.logger;

/**
 * ロガー管理クラス
 * @author shiraishitoshio
 *
 */
public interface ManageLoggerComponent {

	/**
	 * ThreadContextを初期化する
	 * @param exeMode 実行モード
	 * @param logicCd ロジックコード
	 * @param country 国
	 * @param league リーグ
	 */
	void init(String exeMode, String logicCd, String country, String league);

	/**
	 * ThreadContextを初期化する
	 * @param exeMode 実行モード
	 * @param info 補足情報
	 */
	void init(String exeMode, String info);

	/**
	 * ThreadContextを削除初期化する
	 */
	void clear();

	/**
	 * Debugログ出力
	 * @param projectName プロジェクト名
	 * @param className クラス名
	 * @param methodName メソッド名
	 * @param messageCd メッセージコード
	 * @param fillChar 埋め字
	 */
	void debugInfoLog(String projectName, String className, String methodName, String messageCd, String... fillChar);

	/**
	 * Debug警告ログ
	 * @param projectName プロジェクト名
	 * @param className クラス名
	 * @param methodName メソッド名
	 * @param messageCd メッセージコード
	 * @param fillChar 埋め字
	 */
    void debugWarnLog(String projectName, String className, String methodName, String messageCd, String... fillChar);

    /**
     * Debugエラーログ
	 * @param projectName プロジェクト名
	 * @param className クラス名
	 * @param methodName メソッド名
	 * @param messageCd メッセージコード
	 * @param exception 例外
	 * @param fillChar 埋め字
     */
    void debugErrorLog(String projectName, String className, String methodName, String errorCode, Exception exception, String... fillChar);

    /**
	 * Debug開始ログ出力
	 * @param projectName プロジェクト名
	 * @param className クラス名
	 * @param methodName メソッド名
	 * @param fillChar 埋め字
	 */
	void debugStartInfoLog(String projectName, String className, String methodName, String... fillChar);

	/**
	 * Debug終了ログ出力
	 * @param projectName プロジェクト名
	 * @param className クラス名
	 * @param methodName メソッド名
	 * @param fillChar 埋め字
	 */
	void debugEndInfoLog(String projectName, String className, String methodName, String... fillChar);

	/**
     * BusinessExceptionログ
	 * @param projectName プロジェクト名
	 * @param className クラス名
	 * @param methodName メソッド名
	 * @param messageCd メッセージコード
	 * @param exception 例外
     */
	void createBusinessException(String projectName, String className, String methodName, String errorCode,
			Throwable exception);

	/**
     * SystemExceptionログ
	 * @param projectName プロジェクト名
	 * @param className クラス名
	 * @param methodName メソッド名
	 * @param messageCd メッセージコード
	 * @param exception 例外
     */
	void createSystemException(String projectName, String className, String methodName, String errorCode,
			Throwable exception);
}
