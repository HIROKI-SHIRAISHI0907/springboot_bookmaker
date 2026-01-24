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
		String msg = buildMessage(projectName, className, methodName, "INFO:" + messageCd, null, fillChar);
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
		String msg = buildMessage(projectName, className, methodName, "WARN:" + messageCd, null, fillChar);
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
		String msg = buildMessage(projectName, className, methodName, "ERROR:" + errorCode, e, fillChar);
		logger.error(msg, e);
	}

	/**
	 * 共通メッセージ
	 * @param projectName プロジェクト名
	 * @param className クラス名
	 * @param methodName メソッド名
	 * @param messageCd メッセージコード
	 * @param e 例外
	 * @param fillChar 埋め字
	 * @return
	 */
	private static String buildMessage(String projectName, String className, String methodName, String messageCd,
			Exception e, String... fillChar) {
		// ★ 最後の名前だけ取得
	    String project = lastName(projectName);
	    String clazz   = lastName(className);
	    String method  = lastName(methodName);

		// ★ メッセージコード → 文言へ変換
	    String messageText = MessageSourceProvider.getMessage(messageCd, fillChar);

	    StringBuilder sb = new StringBuilder();
	    sb.append("[").append(project).append("]");
	    sb.append("[").append(clazz).append("#").append(method).append("]");
	    sb.append("[").append(messageCd);

	    // メッセージ本文を追加
	    if (!messageCd.equals(messageText)) {
	        sb.append(":").append(messageText);
	    }
	    sb.append("]");

	    if (e != null && e.toString().length() > 0) {
	        sb.append(" - ").append(e.toString());
	    }
	    return sb.toString();
	}

	/**
	 * パスやパッケージ名から末尾の名称だけ取得する
	 * 例:
	 *  - com.example.demo → demo
	 *  - dev.common.logger.BookMakerLogger → BookMakerLogger
	 *  - /usr/local/bin/app → app
	 */
	private static String lastName(String value) {
	    if (value == null || value.isEmpty()) {
	        return value;
	    }

	    // / 区切り対応
	    int slashIndex = value.lastIndexOf('/');
	    if (slashIndex >= 0) {
	        value = value.substring(slashIndex + 1);
	    }

	    // . 区切り対応
	    int dotIndex = value.lastIndexOf('.');
	    if (dotIndex >= 0) {
	        value = value.substring(dotIndex + 1);
	    }

	    return value;
	}

}
