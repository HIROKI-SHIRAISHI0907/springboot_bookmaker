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
		Logger logger = LoggerFactory.getLogger(className);
		String msg = buildMessage(projectName, className, methodName, "INFO", messageCd, null, fillChar);
		logger.info(msg);
	}

	public static void warn(String projectName, String className, String methodName, String messageCd,
			String... fillChar) {
		Logger logger = LoggerFactory.getLogger(className);
		String msg = buildMessage(projectName, className, methodName, "WARN", messageCd, null, fillChar);
		logger.warn(msg);
	}

	public static void error(String projectName, String className, String methodName, String errorCode, Exception e,
			String... fillChar) {
		Logger logger = LoggerFactory.getLogger(className);
		String msg = buildMessage(projectName, className, methodName, "ERROR", errorCode, e, fillChar);
		logger.error(msg, e);
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

		// MessageSource のキーは "MCDxxxx" のみを渡す（INFO: などを混ぜない）
		String key = normalizeMessageKey(messageCd);

		String messageText = null;
		if (key != null && !key.isBlank()) {
			messageText = MessageSourceProvider.getMessage(key, fillChar);
		}

		StringBuilder sb = new StringBuilder();
		sb.append("[").append(lastName(projectName)).append("]");
		sb.append("[").append(lastName(className)).append("#").append(lastName(methodName)).append("]");

		// 先にレベル
		sb.append("[").append(level);

		// messageCd（キー）は表示したいので key を出す（messageCd が INFO:付きで来ても key を表示）
		if (key != null && !key.isBlank()) {
			sb.append(":").append(key);

			// 文言が取れたときだけ表示（取れない場合はコードのみ）
			if (messageText != null && !messageText.isBlank() && !messageText.equals(key)) {
				sb.append(":").append(messageText);
			}
		}

		// 埋め字だけ出したいケース（messageCd が空で fillChar がある時）
		// 例: debugStartInfoLog / debugEndInfoLog の fillChar を見たい
		if ((key == null || key.isBlank()) && fillChar != null && fillChar.length > 0) {
			String joined = joinFillChars(fillChar);
			if (!joined.isBlank()) {
				sb.append(":").append(joined);
			}
		}

		sb.append("]");

		if (e != null) {
			sb.append(" - ").append(e);
		}
		return sb.toString();
	}

	/**
	 * MessageSource lookup 用のキーを正規化
	 * - "INFO:MCDxxxx" / "WARN:MCDxxxx" / "ERROR:MCDxxxx" が来ても "MCDxxxx" に直す
	 * - null/blank はそのまま
	 */
	private static String normalizeMessageKey(String messageCd) {
		if (messageCd == null) return null;
		String s = messageCd.trim();
		if (s.isEmpty()) return "";
		// 先頭の LEVEL: を剥がす
		return s.replaceFirst("^(?i)(INFO|WARN|ERROR)\\s*:\\s*", "");
	}

	private static String joinFillChars(String... fillChar) {
		if (fillChar == null || fillChar.length == 0) return "";
		StringBuilder sb = new StringBuilder();
		for (String c : fillChar) {
			if (c == null) continue;
			String t = c.trim();
			if (t.isEmpty()) continue;
			if (sb.length() > 0) sb.append(", ");
			sb.append(t);
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
