package dev.common.util;

import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
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
	private static final Pattern YMDHMS_DELIMITER_PATTERN = Pattern.compile("^\\d{4}/\\d{2}/\\d{2} \\d{2}:\\d{2}:\\d{2}$");
	/**
	 * 年月日時分秒ミリ秒(yyyyMMddHHmmssSSS)の入力チェック文字列
	 */
	private static final Pattern YMDHMSS_PATTERN = Pattern.compile("^\\d{4}\\d{2}\\d{2}\\d{2}\\d{2}\\d{2}\\d{3}$");
	/**
	 * 年月日時分秒ミリ秒(yyyy/MM/dd HH:mm:ss.SSS)のデリミタ入力チェック文字列
	 */
	private static final Pattern YMDHMSS_DELIMITER_PATTERN = Pattern.compile("^\\d{4}/\\d{2}/\\d{2} \\d{2}:\\d{2}:\\d{2}.\\d{3}$");

	/**
	 * コンストラクタ生成禁止
	 */
	private DateUtil() {}

	/**
	 * 日付管理機能
	 */
	public static String getSysDate() {
		Calendar calendar = Calendar.getInstance();
		calendar.get(Calendar.YEAR);
		calendar.get(Calendar.MONTH);
		calendar.get(Calendar.DATE);
		calendar.get(Calendar.HOUR);
		calendar.get(Calendar.MINUTE);
		calendar.get(Calendar.SECOND);
		Timestamp time = new Timestamp(calendar.getTimeInMillis());
		SimpleDateFormat str = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		String date = str.format(time);
		return date;
	}

	/**
	 *
	 * @param time 日時
	 * @return タイムスタンプ
	 * @throws Exception 例外
	 */
	public static Timestamp convertTimestamp(String time) throws Exception {
		// 文字列判定
		ArrayList<String> resultList = chkDate(time);
		// 日にち
		time = resultList.get(0);
		time = convertFormatHyphen(time);
		// 文字判定
		String result = resultList.get(1);
		Timestamp timestamp = null;
		switch (result) {
			case "yMd":
				timestamp = convDateYmd(time);
				break;
			case "m":
				timestamp = convDateYmdhm(time);
				break;
			case "s":
				timestamp = convDateYmdhms(time);
				break;
			case "S":
				timestamp = convDateYmdhmsS(time);
				break;
			default:
				throw new Exception("time値について該当するメソッドに振り分けできない。(入力値:" + time + ")");
		}
		return timestamp;
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
		final String METHOD_NAME = "chkDate";
		String chkFormat = null;
		String[] split = null;
		StringBuilder sb = new StringBuilder(date);
		ArrayList<String> resultList = new ArrayList<String>();
		// SSSがある
		if (date.contains(".")) {
			chkFormat = "S";
			// SSSの形式がない場合SSSに強制変換
			split = date.split("\\.");
			if (split[1].length() == 2) {
				sb.append("0");
			} else if (split[1].length() == 1) {
				sb.append("00");
			}
			resultList.add(sb.toString());
			// SSSがない
		} else {
			split = date.split("\\:");
			switch (split.length) {
				case 1:
					chkFormat = "H";
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
		if (date.contains("-")) {
			date = date.replace("-", "/");
		}
		return date;
	}

	/**
	 * 日付フォーマット変換(Timestamp)(yyyyMMdd)
	 * @param date 日時
	 * @return タイムスタンプ
	 * @throws Exception
	 */
	private static Timestamp convDateYmd(String date) throws Exception {
		final String METHOD_NAME = "convDateYmd";
		Timestamp resDate = null;
		if (date == null) {
			throw new Exception("nullエラー(メソッド名:" + METHOD_NAME + ")");
		}
		try {
			if (YMD_PATTERN.matcher(date).find()) {
				SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd");
				// 日付解析を厳密にする
				format.setLenient(false);
				resDate = new Timestamp(format.parse(date).getTime());
			} else if (YMD_DELIMITER_PATTERN.matcher(date).find()) {
				SimpleDateFormat format = new SimpleDateFormat("yyyy/MM/dd");
				// 日付解析を厳密にする
				format.setLenient(false);
				resDate = new Timestamp(format.parse(date).getTime());
			} else {
				throw new Exception("Formatterエラー(メソッド名:" + METHOD_NAME + ", 日付:" + date + ")");
			}
		} catch (ParseException e) {
			throw new Exception("ParseExceptionエラー(メソッド名:" + METHOD_NAME + ", 日付:" + date + ")");
		}
		return resDate;
	}

	/**
	 * 日付フォーマット変換(Timestamp)(yyyyMMddHHmm)
	 * @param date 日時
	 * @return タイムスタンプ
	 * @throws Exception
	 */
	private static Timestamp convDateYmdhm(String date) throws Exception {
		final String METHOD_NAME = "convDateYmdhm";
		Timestamp resDate = null;
		if (date == null) {
			throw new Exception("nullエラー(メソッド名:" + METHOD_NAME + ")");
		}
		try {
			if (YMDHM_PATTERN.matcher(date).find()) {
				SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmm");
				// 日付解析を厳密にする
				format.setLenient(false);
				resDate = new Timestamp(format.parse(date).getTime());
			} else if (YMDHM_DELIMITER_PATTERN.matcher(date).find()) {
				SimpleDateFormat format = new SimpleDateFormat("yyyy/MM/dd HH:mm");
				// 日付解析を厳密にする
				format.setLenient(false);
				resDate = new Timestamp(format.parse(date).getTime());
			} else {
				throw new Exception("Formatterエラー(メソッド名:" + METHOD_NAME + ", 日付:" + date + ")");
			}
		} catch (ParseException e) {
			throw new Exception("ParseExceptionエラー(メソッド名:" + METHOD_NAME + ", 日付:" + date + ")");
		}
		return resDate;
	}

	/**
	 * 日付フォーマット変換(Timestamp)(yyyyMMddHHmmss)
	 * @param date 日時
	 * @return タイムスタンプ
	 * @throws Exception
	 */
	private static Timestamp convDateYmdhms(String date) throws Exception {
		final String METHOD_NAME = "convDateYmdhms";
		Timestamp resDate = null;
		if (date == null) {
			throw new Exception("nullエラー(メソッド名:" + METHOD_NAME + ")");
		}
		try {
			if (YMDHMS_PATTERN.matcher(date).find()) {
				SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmmss");
				// 日付解析を厳密にする
				format.setLenient(false);
				resDate = new Timestamp(format.parse(date).getTime());
			} else if (YMDHMS_DELIMITER_PATTERN.matcher(date).find()) {
				SimpleDateFormat format = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
				// 日付解析を厳密にする
				format.setLenient(false);
				resDate = new Timestamp(format.parse(date).getTime());
			} else {
				throw new Exception("Formatterエラー(メソッド名:" + METHOD_NAME + ", 日付:" + date + ")");
			}
		} catch (ParseException e) {
			throw new Exception("ParseExceptionエラー(メソッド名:" + METHOD_NAME + ", 日付:" + date + ")");
		}
		return resDate;
	}

	/**
	 * 日付フォーマット変換(Timestamp)(yyyyMMddHHmmssSSS)
	 * @param date 日時
	 * @return タイムスタンプ
	 * @throws Exception
	 */
	private static Timestamp convDateYmdhmsS(String date) throws Exception {
		final String METHOD_NAME = "convDateYmdhmsS";
		Timestamp resDate = null;
		if (date == null) {
			throw new Exception("nullエラー(メソッド名:" + METHOD_NAME + ")");
		}
		try {
			if (YMDHMSS_PATTERN.matcher(date).find()) {
				SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmmssSSS");
				// 日付解析を厳密にする
				format.setLenient(false);
				resDate = new Timestamp(format.parse(date).getTime());
			} else if (YMDHMSS_DELIMITER_PATTERN.matcher(date).find()) {
				SimpleDateFormat format = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS");
				// 日付解析を厳密にする
				format.setLenient(false);
				resDate = new Timestamp(format.parse(date).getTime());
			} else {
				throw new Exception("Formatterエラー(メソッド名:" + METHOD_NAME + ", 日付:" + date + ")");
			}
		} catch (ParseException e) {
			throw new Exception("ParseExceptionエラー(メソッド名:" + METHOD_NAME + ", 日付:" + date + ")");
		}
		return resDate;
	}

}
