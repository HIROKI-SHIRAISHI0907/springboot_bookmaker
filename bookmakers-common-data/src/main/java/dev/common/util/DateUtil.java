package dev.common.util;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.regex.Pattern;

/**
 * 日付管理機能
 * @author shiraishitoshio
 *
 */
public class DateUtil {

	/**
	 * 年月日(yyyyMMdd)の入力チェック文字列
	 */
	private static final Pattern YMD_PATTERN = Pattern.compile("^\\d{4}\\d{2}\\d{2}$");
	/**
	 * 年月日(yyyy/MM/dd)のデリミタ入力チェック文字列
	 */
	private static final Pattern YMD_DELIMITER_PATTERN = Pattern.compile("^\\d{4}/\\d{2}/\\d{2}$");
	/**
	 * 年月日時分(yyyyMMddHHmm)の入力チェック文字列
	 */
	private static final Pattern YMDHM_PATTERN = Pattern.compile("^\\d{4}\\d{2}\\d{2}\\d{2}\\d{2}$");
	/**
	 * 年月日時分(yyyy/MM/dd HH:mm)のデリミタ入力チェック文字列
	 */
	private static final Pattern YMDHM_DELIMITER_PATTERN = Pattern.compile("^\\d{4}/\\d{2}/\\d{2} \\d{2}:\\d{2}$");
	/**
	 * 年月日時分秒(yyyyMMddHHmmss)の入力チェック文字列
	 */
	private static final Pattern YMDHMS_PATTERN = Pattern.compile("^\\d{4}\\d{2}\\d{2}\\d{2}\\d{2}\\d{2}$");
	/**
	 * 年月日時分秒(yyyy/MM/dd HH:mm:ss)のデリミタ入力チェック文字列
	 */
	private static final Pattern YMDHMS_DELIMITER_PATTERN = Pattern
			.compile("^\\d{4}/\\d{2}/\\d{2} \\d{2}:\\d{2}:\\d{2}$");
	/**
	 * 年月日時分秒ミリ秒(yyyyMMddHHmmssSSS)の入力チェック文字列
	 */
	private static final Pattern YMDHMSS_PATTERN = Pattern.compile("^\\d{4}\\d{2}\\d{2}\\d{2}\\d{2}\\d{2}\\d{3}$");
	/**
	 * 年月日時分秒ミリ秒(yyyy/MM/dd HH:mm:ss.SSS)のデリミタ入力チェック文字列
	 */
	private static final Pattern YMDHMSS_DELIMITER_PATTERN = Pattern
			.compile("^\\d{4}/\\d{2}/\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}$");

	/**
	 * JST固定
	 */
	private static final ZoneId JST = ZoneId.of("Asia/Tokyo");

	/**
	 * ドイツ形式 → 例: "22.07.2025 19:30"
	 */
	private static final DateTimeFormatter GERMAN_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
	/**
	 * 日本形式（MySQL DATETIME 対応） → 例: "2025-07-22 19:30:00"
	 */
	private static final DateTimeFormatter JAPANESE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	/**
	 * dd.MM.yyyy
	 */
	private static final DateTimeFormatter PATTERN_DD_MM_YYYY = DateTimeFormatter.ofPattern("dd.MM.yyyy");

	/**
	 * 日本形式（秒なし）
	 */
	private static final DateTimeFormatter JAPANESE_FORMAT_NO_SEC
	        = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

	/**
	 * スラッシュ区切り
	 */
	private static final DateTimeFormatter JAPANESE_SLASH_NO_SEC
	        = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");
	private static final DateTimeFormatter JAPANESE_SLASH_SEC
	        = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
	private static final DateTimeFormatter JAPANESE_SLASH_MILLIS
	        = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss.SSS");

	/**
	 * yyyy-MM-dd
	 */
	private static final DateTimeFormatter JAPANESE_DATE_ONLY
	        = DateTimeFormatter.ofPattern("yyyy-MM-dd");

	/**
	 * yyyy/MM/dd
	 */
	private static final DateTimeFormatter JAPANESE_SLASH_DATE_ONLY
	        = DateTimeFormatter.ofPattern("yyyy/MM/dd");

	/**
	 * yyyyMMdd
	 */
	private static final DateTimeFormatter BASIC_DATE_ONLY
	        = DateTimeFormatter.ofPattern("yyyyMMdd");

	/**
	 * yyyyMMddHHmm
	 */
	private static final DateTimeFormatter BASIC_YMDHM
	        = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

	/**
	 * yyyyMMddHHmmss
	 */
	private static final DateTimeFormatter BASIC_YMDHMS
	        = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

	/**
	 * yyyyMMddHHmmssSSS
	 */
	private static final DateTimeFormatter BASIC_YMDHMSS
	        = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

	/**
	 * dd.MM.yyyy HH:mm:ss
	 */
	private static final DateTimeFormatter GERMAN_FORMAT_SEC
	        = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

	/**
	 * 通常フォーマット
	 */
	private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	/**
	 * コンストラクタ生成禁止
	 */
	private DateUtil() {
	}

	/**
	 * 日付管理機能
	 */
	public static String getSysDate() {
		return ZonedDateTime.now(JST).format(FMT);
	}

	/**
	 *
	 * @param time 日時
	 * @return タイムスタンプ
	 * @throws Exception 例外
	 */
	public static Timestamp convertTimestamp(String time) throws Exception {
		ArrayList<String> resultList = chkDate(time);
		time = resultList.get(0);
		time = convertFormatHyphen(time);
		String result = resultList.get(1);

		switch (result) {
		case "yMd":
			return convDateYmd(time);
		case "m":
			return convDateYmdhm(time);
		case "s":
			return convDateYmdhms(time);
		case "S":
			return convDateYmdhmsS(time);
		default:
			throw new Exception("time値について該当するメソッドに振り分けできない。(入力値:" + time + ")");
		}
	}

	/**
	 * <p>
	 * 文字列判定
	 * </p>
	 * dateから文字列がどこまで取れるか及びデリミタ有無を判定する。それによってフォーマット変換の際に呼び出すメソッドを決める
	 * @param date 日時
	 * @return 判定文字列用リスト(メソッド判定文字列,デリミタ有無)
	 * @throws Exception
	 */
	private static ArrayList<String> chkDate(String date) throws Exception {
		if (date == null) {
			throw new Exception("dateがnullです。");
		}
		String chkFormat = null;
		String[] split = null;
		StringBuilder sb = new StringBuilder(date);
		ArrayList<String> resultList = new ArrayList<>();

		if (date.contains(".")) {
			chkFormat = "S";
			split = date.split("\\.");
			if (split.length >= 2) {
				if (split[1].length() == 2) {
					sb.append("0");
				} else if (split[1].length() == 1) {
					sb.append("00");
				}
			}
			resultList.add(sb.toString());
		} else {
			split = date.split("\\:");
			switch (split.length) {
			case 1:
				chkFormat = "yMd";
				break;
			case 2:
				chkFormat = "m";
				break;
			case 3:
				chkFormat = "s";
				break;
			default:
				chkFormat = "yMd";
				break;
			}
			resultList.add(date);
		}
		resultList.add(chkFormat);
		return resultList;
	}

	/**
	 * ハイフン変換
	 * @param date 日時
	 * @return
	 */
	private static String convertFormatHyphen(String date) {
		if (date == null) {
			return null;
		}
		if (date.contains("-")) {
			date = date.replace("-", "/");
		}
		return date;
	}

	/**
	 * 日付フォーマット変換(Timestamp)(yyyyMMdd / yyyy/MM/dd)
	 * JST の壁時計時刻として扱う
	 * @param date 日時
	 * @return タイムスタンプ
	 * @throws Exception
	 */
	private static Timestamp convDateYmd(String date) throws Exception {
		final String METHOD_NAME = "convDateYmd";
		if (date == null) {
			throw new Exception("nullエラー(メソッド名:" + METHOD_NAME + ")");
		}
		try {
			LocalDate ld;
			if (YMD_PATTERN.matcher(date).find()) {
				ld = LocalDate.parse(date, BASIC_DATE_ONLY);
			} else if (YMD_DELIMITER_PATTERN.matcher(date).find()) {
				ld = LocalDate.parse(date, JAPANESE_SLASH_DATE_ONLY);
			} else {
				throw new Exception("Formatterエラー(メソッド名:" + METHOD_NAME + ", 日付:" + date + ")");
			}
			return Timestamp.valueOf(ld.atStartOfDay());
		} catch (DateTimeParseException e) {
			throw new Exception("ParseExceptionエラー(メソッド名:" + METHOD_NAME + ", 日付:" + date + ")", e);
		}
	}

	/**
	 * 日付フォーマット変換(Timestamp)(yyyyMMddHHmm / yyyy/MM/dd HH:mm)
	 * JST の壁時計時刻として扱う
	 * @param date 日時
	 * @return タイムスタンプ
	 * @throws Exception
	 */
	private static Timestamp convDateYmdhm(String date) throws Exception {
		final String METHOD_NAME = "convDateYmdhm";
		if (date == null) {
			throw new Exception("nullエラー(メソッド名:" + METHOD_NAME + ")");
		}
		try {
			LocalDateTime ldt;
			if (YMDHM_PATTERN.matcher(date).find()) {
				ldt = LocalDateTime.parse(date, BASIC_YMDHM);
			} else if (YMDHM_DELIMITER_PATTERN.matcher(date).find()) {
				ldt = LocalDateTime.parse(date, JAPANESE_SLASH_NO_SEC);
			} else {
				throw new Exception("Formatterエラー(メソッド名:" + METHOD_NAME + ", 日付:" + date + ")");
			}
			return Timestamp.valueOf(ldt);
		} catch (DateTimeParseException e) {
			throw new Exception("ParseExceptionエラー(メソッド名:" + METHOD_NAME + ", 日付:" + date + ")", e);
		}
	}

	/**
	 * 日付フォーマット変換(Timestamp)(yyyyMMddHHmmss / yyyy/MM/dd HH:mm:ss)
	 * JST の壁時計時刻として扱う
	 * @param date 日時
	 * @return タイムスタンプ
	 * @throws Exception
	 */
	private static Timestamp convDateYmdhms(String date) throws Exception {
		final String METHOD_NAME = "convDateYmdhms";
		if (date == null) {
			throw new Exception("nullエラー(メソッド名:" + METHOD_NAME + ")");
		}
		try {
			LocalDateTime ldt;
			if (YMDHMS_PATTERN.matcher(date).find()) {
				ldt = LocalDateTime.parse(date, BASIC_YMDHMS);
			} else if (YMDHMS_DELIMITER_PATTERN.matcher(date).find()) {
				ldt = LocalDateTime.parse(date, JAPANESE_SLASH_SEC);
			} else {
				throw new Exception("Formatterエラー(メソッド名:" + METHOD_NAME + ", 日付:" + date + ")");
			}
			return Timestamp.valueOf(ldt);
		} catch (DateTimeParseException e) {
			throw new Exception("ParseExceptionエラー(メソッド名:" + METHOD_NAME + ", 日付:" + date + ")", e);
		}
	}

	/**
	 * 日付フォーマット変換(Timestamp)(yyyyMMddHHmmssSSS / yyyy/MM/dd HH:mm:ss.SSS)
	 * JST の壁時計時刻として扱う
	 * @param date 日時
	 * @return タイムスタンプ
	 * @throws Exception
	 */
	private static Timestamp convDateYmdhmsS(String date) throws Exception {
		final String METHOD_NAME = "convDateYmdhmsS";
		if (date == null) {
			throw new Exception("nullエラー(メソッド名:" + METHOD_NAME + ")");
		}
		try {
			LocalDateTime ldt;
			if (YMDHMSS_PATTERN.matcher(date).find()) {
				ldt = LocalDateTime.parse(date, BASIC_YMDHMSS);
			} else if (YMDHMSS_DELIMITER_PATTERN.matcher(date).find()) {
				ldt = LocalDateTime.parse(date, JAPANESE_SLASH_MILLIS);
			} else {
				throw new Exception("Formatterエラー(メソッド名:" + METHOD_NAME + ", 日付:" + date + ")");
			}
			return Timestamp.valueOf(ldt);
		} catch (DateTimeParseException e) {
			throw new Exception("ParseExceptionエラー(メソッド名:" + METHOD_NAME + ", 日付:" + date + ")", e);
		}
	}

	/**
	 * ドイツ形式から日本形式に変換する
	 *
	 * @param germanDateStr ドイツ形式の日時文字列（例: "22.07.2025 19:30"）
	 * @return 日本形式の日時文字列（例: "2025-07-22 19:30:00"）
	 */
	public static String convertGermanToJapaneseFormat(String germanDateStr) {
		try {
			LocalDateTime dateTime = LocalDateTime.parse(germanDateStr, GERMAN_FORMAT);
			return dateTime.format(JAPANESE_FORMAT);
		} catch (DateTimeParseException e) {
			throw new IllegalArgumentException("不正な日時フォーマットです: " + germanDateStr, e);
		}
	}

	/**
	 *
	 * @param input
	 * @return
	 */
	public static String convertOnlyDD_MM_YYYY(String input) {
	    if (input == null) return "";

	    String raw = input;
	    String s = input
	            .replace('\u00A0', ' ')
	            .replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "")
	            .trim();

	    if (s.isEmpty() || "N/A".equalsIgnoreCase(s)) return "";

	    try {
	        LocalDate date = LocalDate.parse(s, PATTERN_DD_MM_YYYY);
	        return date.atStartOfDay().format(JAPANESE_FORMAT);
	    } catch (DateTimeParseException ignore) {}

	    try {
	        LocalDateTime dt = LocalDateTime.parse(s, DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
	        return dt.format(JAPANESE_FORMAT);
	    } catch (DateTimeParseException ignore) {}

	    try {
	        LocalDateTime dt = LocalDateTime.parse(s, DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"));
	        return dt.format(JAPANESE_FORMAT);
	    } catch (DateTimeParseException ignore) {}

	    throw new IllegalArgumentException(
	        "不正な日時フォーマットです: [" + visualize(raw) + "] -> normalized:[" + visualize(s) + "]"
	    );
	}

	/**
	 * 入力がどの形式でも "yyyy-MM-dd HH:mm:ss" に正規化して返す
	 */
	public static String normalizeToJapaneseFormat(String input) {
	    if (input == null || input.isBlank()) {
	        return "";
	    }

	    String s = input
	            .replace('\u00A0', ' ')
	            .replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "")
	            .trim();

	    try {
	        LocalDateTime dt = LocalDateTime.parse(s, GERMAN_FORMAT);
	        return dt.format(JAPANESE_FORMAT);
	    } catch (DateTimeParseException ignore) {}

	    try {
	        LocalDateTime dt = LocalDateTime.parse(s, JAPANESE_FORMAT);
	        return dt.format(JAPANESE_FORMAT);
	    } catch (DateTimeParseException ignore) {}

	    try {
	        LocalDateTime dt = LocalDateTime.parse(s, JAPANESE_FORMAT_NO_SEC);
	        return dt.format(JAPANESE_FORMAT);
	    } catch (DateTimeParseException ignore) {}

	    try {
	        LocalDateTime dt = LocalDateTime.parse(s, JAPANESE_SLASH_SEC);
	        return dt.format(JAPANESE_FORMAT);
	    } catch (DateTimeParseException ignore) {}

	    try {
	        LocalDateTime dt = LocalDateTime.parse(s, JAPANESE_SLASH_NO_SEC);
	        return dt.format(JAPANESE_FORMAT);
	    } catch (DateTimeParseException ignore) {}

	    throw new IllegalArgumentException("不正な日時フォーマットです: " + input);
	}

	/**
     * "2026-03-02 15:40:59" のような文字列を 90分前にして同形式で返す
     */
    public static String minus90Minutes(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isBlank()) {
            throw new IllegalArgumentException("dateTimeStr is null/blank");
        }

        LocalDateTime dt = LocalDateTime.parse(dateTimeStr.trim(), FMT);
        LocalDateTime dtMinus = dt.minusMinutes(90);
        return dtMinus.format(FMT);
    }

    /**
     * 各種日時文字列を LocalDateTime に変換する。
     *
     * @param input 日時文字列
     * @return LocalDateTime
     */
    public static LocalDateTime convertLocalDateTime(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("入力値がnullまたは空です。");
        }

        String s = input
                .replace('\u00A0', ' ')
                .replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "")
                .trim();

        try {
            return LocalDateTime.parse(s, JAPANESE_FORMAT);
        } catch (DateTimeParseException ignore) {}

        try {
            return LocalDateTime.parse(s, JAPANESE_FORMAT_NO_SEC);
        } catch (DateTimeParseException ignore) {}

        try {
            return LocalDateTime.parse(s, JAPANESE_SLASH_SEC);
        } catch (DateTimeParseException ignore) {}

        try {
            return LocalDateTime.parse(s, JAPANESE_SLASH_NO_SEC);
        } catch (DateTimeParseException ignore) {}

        try {
            return LocalDateTime.parse(s, BASIC_YMDHMS);
        } catch (DateTimeParseException ignore) {}

        try {
            return LocalDateTime.parse(s, BASIC_YMDHM);
        } catch (DateTimeParseException ignore) {}

        try {
            return LocalDateTime.parse(s, GERMAN_FORMAT_SEC);
        } catch (DateTimeParseException ignore) {}

        try {
            return LocalDateTime.parse(s, GERMAN_FORMAT);
        } catch (DateTimeParseException ignore) {}

        try {
            return LocalDate.parse(s, JAPANESE_DATE_ONLY).atStartOfDay();
        } catch (DateTimeParseException ignore) {}

        try {
            return LocalDate.parse(s, JAPANESE_SLASH_DATE_ONLY).atStartOfDay();
        } catch (DateTimeParseException ignore) {}

        try {
            return LocalDate.parse(s, BASIC_DATE_ONLY).atStartOfDay();
        } catch (DateTimeParseException ignore) {}

        try {
            return LocalDate.parse(s, PATTERN_DD_MM_YYYY).atStartOfDay();
        } catch (DateTimeParseException ignore) {}

        throw new IllegalArgumentException("不正な日時フォーマットです: " + input);
    }

	/** 目に見えない文字を可視化する */
	private static String visualize(String str) {
	    if (str == null) return "null";
	    StringBuilder sb = new StringBuilder();
	    for (int i = 0; i < str.length(); i++) {
	        char c = str.charAt(i);
	        if (c == ' ') sb.append("␠");
	        else if (c == '\t') sb.append("\\t");
	        else if (c == '\n') sb.append("\\n");
	        else if (c == '\r') sb.append("\\r");
	        else if (c == '\u00A0') sb.append("NBSP");
	        else if (Character.isISOControl(c)) sb.append(String.format("\\u%04x", (int)c));
	        else sb.append(c);
	    }
	    return sb.toString();
	}
}
