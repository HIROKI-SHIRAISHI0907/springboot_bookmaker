package dev.common.logger;

import org.apache.logging.log4j.ThreadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ログ出力基盤処理,設定クラス
 * @author shiraishitoshio
 *
 */
public class BookMakerLogger {

	/** LOG_STAT */
	private static final String LOG_STAT = "STAT";

	/** LOGIC_CD */
	private static final String LOG_LOGIC_CD = "LOGIC_CD";

	/** LOG_COUNTRY */
	private static final String LOG_COUNTRY = "COUNTRY";

	/** LOG_LEAGUE */
	private static final String LOG_LEAGUE = "LEAGUE";

	/** LOG_INFO */
	private static final String LOG_INFO = "INFO";

	/** コンストラクタ生成禁止 */
	private BookMakerLogger() {
	}

	/**
	 * ログ共通項目設定処理
	 * @param exeMode 実行モード
	 * @param logicCd ロジックコード
	 * @param country 国
	 * @param league リーグ
	 */
	public static void init(String exeMode, String logicCd,
			String country, String league) {
		clear();
		ThreadContext.put(LOG_STAT, exeMode);
		ThreadContext.put(LOG_LOGIC_CD, logicCd);
		ThreadContext.put(LOG_COUNTRY, country);
		ThreadContext.put(LOG_LEAGUE, league);
	}

	/**
	 * ログ共通項目設定処理
	 * @param exeMode 実行モード
	 * @param info 補足情報
	 */
	public static void init(String exeMode,
			String info) {
		clear();
		ThreadContext.put(LOG_STAT, exeMode);
		ThreadContext.put(LOG_INFO, info);
	}

	/**
	 * ログ共通項目削除処理
	 */
	public static void remove() {
		ThreadContext.remove(LOG_STAT);
		ThreadContext.remove(LOG_LOGIC_CD);
		ThreadContext.remove(LOG_COUNTRY);
		ThreadContext.remove(LOG_LEAGUE);
		ThreadContext.remove(LOG_INFO);
	}

	/**
	 * ThreadContextの初期化
	 */
	public static void clear() {
		ThreadContext.clearMap();
	}

	/**
	 * 正常メッセージ
	 * @param projectName プロジェクト名
	 * @param className クラス名
	 * @param methodName メソッド名
	 * @param messageCd メッセージコード
	 * @param fillChar 埋め字
	 */
	public static void info(String projectName, String className, String methodName, String messageCd,
			String... fillChar) {
		Logger logger = LoggerFactory.getLogger(className);
		String msg = buildMessage(projectName, className, methodName, messageCd, fillChar);
		logger.info(msg);
	}

	/**
	 * 警告メッセージ
	 * @param projectName プロジェクト名
	 * @param className クラス名
	 * @param methodName メソッド名
	 * @param messageCd メッセージコード
	 * @param fillChar 埋め字
	 */
	public static void warn(String projectName, String className, String methodName, String messageCd,
			String... fillChar) {
		Logger logger = LoggerFactory.getLogger(className);
		String msg = buildMessage(projectName, className, methodName, messageCd, fillChar);
		logger.warn(msg);
	}

	/**
	 * エラーメッセージ
	 * @param projectName プロジェクト名
	 * @param className クラス名
	 * @param methodName メソッド名
	 * @param messageCd メッセージコード
	 * @param e 例外
	 * @param fillChar 埋め字
	 */
	public static void error(String projectName, String className, String methodName, String errorCode, Exception e,
			String... fillChar) {
		Logger logger = LoggerFactory.getLogger(className);
		String msg = buildMessage(projectName, className, methodName, "ERROR:" + errorCode, fillChar);
		logger.error(msg, e);
	}

	/**
	 * 共通メッセージ
	 * @param projectName プロジェクト名
	 * @param className クラス名
	 * @param methodName メソッド名
	 * @param messageCd メッセージコード
	 * @param fillChar 埋め字
	 * @return
	 */
	private static String buildMessage(String projectName, String className, String methodName, String messageCd,
			String... fillChar) {
		StringBuilder sb = new StringBuilder();
		sb.append("[").append(projectName).append("]");
		sb.append("[").append(className).append("#").append(methodName).append("]");
		sb.append("[").append(messageCd).append("]");
		if (fillChar != null && fillChar.length > 0) {
			sb.append(" - ").append(String.join(", ", fillChar));
		}
		return sb.toString();
	}

}
