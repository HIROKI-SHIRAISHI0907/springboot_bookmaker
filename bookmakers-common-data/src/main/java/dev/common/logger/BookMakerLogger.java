package dev.common.logger;

import org.apache.logging.log4j.ThreadContext;

import lombok.extern.slf4j.Slf4j;

/**
 * ログ出力基盤処理,設定クラス
 * @author shiraishitoshio
 *
 */
@Slf4j
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
	private BookMakerLogger() {}

	// ======================
	// ThreadContext
	// ======================

	public static void init(String exeMode, String logicCd, String country, String league) {
		clear();
		ThreadContext.put(LOG_STAT, exeMode);
		ThreadContext.put(LOG_LOGIC_CD, logicCd);
		ThreadContext.put(LOG_COUNTRY, country);
		ThreadContext.put(LOG_LEAGUE, league);
	}

	public static void init(String exeMode, String info) {
		clear();
		ThreadContext.put(LOG_STAT, exeMode);
		ThreadContext.put(LOG_INFO, info);
	}

	public static void remove() {
		ThreadContext.remove(LOG_STAT);
		ThreadContext.remove(LOG_LOGIC_CD);
		ThreadContext.remove(LOG_COUNTRY);
		ThreadContext.remove(LOG_LEAGUE);
		ThreadContext.remove(LOG_INFO);
	}

	public static void clear() {
		ThreadContext.clearMap();
	}

	// ======================
	// Log APIs
	// ======================

	public static void info(String projectName, String className, String methodName, String messageCd,
			String... fillChar) {
		String msg = buildMessage(projectName, className, methodName, "INFO", messageCd, null, fillChar);
		log.info("[INFO MESSAGE: {}]", msg);
	}

	public static void warn(String projectName, String className, String methodName, String messageCd,
			String... fillChar) {
		String msg = buildMessage(projectName, className, methodName, "WARN", messageCd, null, fillChar);
		log.warn("[WARN MESSAGE: {}]", msg);
	}

	public static void error(String projectName, String className, String methodName, String errorCode, Exception e,
			String... fillChar) {
		String msg = buildMessage(projectName, className, methodName, "ERROR", errorCode, e, fillChar);
		log.error("[ERROR MESSAGE: {}]", msg, e);
	}

	// ======================
	// Build message
	// ======================

	/**
	 * 期待する出力:
	 * [project][Class#method][INFO:MCD00001:メッセージ本文] - Exception...
	 *
	 * messageCd が null/blank のときは:
	 * [project][Class#method][INFO] もしくは [..][..][INFO:埋め字...]（必要なら）
	 */
	private static String buildMessage(String projectName, String className, String methodName,
			String level, String messageCd, Exception e, String... fillChar) {

		String messageText = null;
		if (messageCd != null && !messageCd.isBlank()) {
			messageText = MessageSourceProvider.getMessage(messageCd, fillChar);
		}

		StringBuilder sb = new StringBuilder();
		sb.append("[").append(lastName(projectName)).append("]");
		sb.append("[").append(lastName(className)).append("#").append(lastName(methodName)).append("]");

		// 先にレベル
		sb.append("[").append(level);

		// messageCd（キー）は表示したいので key を出す（messageCd が INFO:付きで来ても key を表示）
		if (messageCd != null && !messageCd.isBlank()) {
			sb.append(":").append(messageCd);

			// 文言が取れたときだけ表示（取れない場合はコードのみ）
			if (messageText != null && !messageText.isBlank() && !messageText.equals(messageCd)) {
				sb.append(":").append(messageText);
			}
		}

		sb.append("]");

		if (e != null) {
			sb.append(" - ").append(e);
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

		int slashIndex = value.lastIndexOf('/');
		if (slashIndex >= 0) {
			value = value.substring(slashIndex + 1);
		}

		int dotIndex = value.lastIndexOf('.');
		if (dotIndex >= 0) {
			value = value.substring(dotIndex + 1);
		}

		return value;
	}
}
