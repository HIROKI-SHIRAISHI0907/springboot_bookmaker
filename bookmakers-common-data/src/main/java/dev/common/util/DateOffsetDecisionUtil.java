package dev.common.util;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * 特定の時間に変換するヘルパー
 * @author shiraishitoshio
 *
 */
public final class DateOffsetDecisionUtil {

    private DateOffsetDecisionUtil() {}

    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");

 // ========================================================
 	// 共通ヘルパー
 	// ========================================================
 	// getLocalDateTime を置き換え
 	public static OffsetDateTime getOffsetDateTime(ResultSet rs, String columnLabel) throws SQLException {
 	    return rs.getObject(columnLabel, OffsetDateTime.class);
 	}

 	// toIsoJstString を置き換え
 	public static String toIsoJstString(OffsetDateTime odt) {
 	    if (odt == null) return null;
 	    return odt.atZoneSameInstant(JST).toOffsetDateTime().toString();
 	}

 	// toOffsetDateTimeJst も同様に
 	public static OffsetDateTime toOffsetDateTimeJst(OffsetDateTime odt) {
 	    if (odt == null) return null;
 	    return odt.atZoneSameInstant(JST).toOffsetDateTime();
 	}

 	/**
	 * 日本時間検索用
	 * @param date
	 * @return
	 */
 	public static OffsetDateTime toStartOfDayJstOffsetDateTime(String date) {
	    LocalDate targetDate = LocalDate.parse(date.trim());
	    return targetDate.atStartOfDay(JST).toOffsetDateTime();
	}

	/**
	 * 日本時間検索用
	 * @param date
	 * @return
	 */
 	public static OffsetDateTime toNextStartOfDayJstOffsetDateTime(String date) {
	    LocalDate targetDate = LocalDate.parse(date.trim()).plusDays(1);
	    return targetDate.atStartOfDay(JST).toOffsetDateTime();
	}

}
