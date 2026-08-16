package dev.common.util;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 特定の時間に変換するヘルパー
 *
 * タイムゾーンに依存する処理は全てこのクラスに集約する。
 * 対象タイムゾーンを変更する場合は下記の ZONE 定数だけを直せばよい。
 *
 * @author shiraishitoshio
 */
public final class DateOffsetDecisionUtil {

    private DateOffsetDecisionUtil() {}

    /** アプリ全体の基準タイムゾーン。変更する場合はここだけ直す。 */
    private static final ZoneId ZONE = ZoneId.of("Asia/Tokyo");

    /** timestamptz を String にマッピングした値をパースするための正規表現(オフセット有無どちらも許容) */
    private static final Pattern DB_TIMESTAMP_PATTERN = Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2})[ T](\\d{2}:\\d{2}:\\d{2})(?:\\.\\d+)?\\s*([+-]\\d{2}(?::?\\d{2})?|Z)?$");

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
        return odt.atZoneSameInstant(ZONE).toOffsetDateTime().toString();
    }

    // toOffsetDateTimeJst も同様に
    public static OffsetDateTime toOffsetDateTimeJst(OffsetDateTime odt) {
        if (odt == null) return null;
        return odt.atZoneSameInstant(ZONE).toOffsetDateTime();
    }

    /**
     * 日付検索用: 指定日(対象タイムゾーン)の 00:00:00
     */
    public static OffsetDateTime toStartOfDayJstOffsetDateTime(String date) {
        LocalDate targetDate = LocalDate.parse(date.trim());
        return targetDate.atStartOfDay(ZONE).toOffsetDateTime();
    }

    /**
     * 日付検索用: 指定日(対象タイムゾーン)の翌日 00:00:00
     */
    public static OffsetDateTime toNextStartOfDayJstOffsetDateTime(String date) {
        LocalDate targetDate = LocalDate.parse(date.trim()).plusDays(1);
        return targetDate.atStartOfDay(ZONE).toOffsetDateTime();
    }

    /**
     * システム日付(対象タイムゾーン基準の「今日」)の 00:00:00〜23:59:59 を
     * UTCのISO8601文字列(例: 2026-08-15T15:00:00Z)に変換して返す。
     * ただし、現在時刻がまだ当日23:59:59に達していない場合は、
     * 終了時刻を "現在時刻 - 1時間" に留める(未確定データを拾いすぎないようにするため)。
     *
     * @return [0]=当日開始(UTC文字列), [1]=当日終了(UTC文字列)
     */
    public static String[] todayRangeAsUtcIsoStrings() {
        LocalDate today = LocalDate.now(ZONE);

        OffsetDateTime startOfDay = toStartOfDayJstOffsetDateTime(today.toString()); // 00:00:00
        OffsetDateTime endOfDay = startOfDay.plusHours(23).plusMinutes(59).plusSeconds(59); // 23:59:59

        OffsetDateTime now = OffsetDateTime.now(ZONE);
        OffsetDateTime cappedEnd = now.isBefore(endOfDay) ? now.minusHours(1) : endOfDay;

        // 現在時刻-1時間が当日開始より前(=日付が変わった直後など)になる場合は開始時刻に丸める
        if (cappedEnd.isBefore(startOfDay)) {
            cappedEnd = startOfDay;
        }

        String todayStart = startOfDay.withOffsetSameInstant(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String todayEnd = cappedEnd.withOffsetSameInstant(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        return new String[] { todayStart, todayEnd };
    }

    /**
     * timestamptz カラムを String にマッピングした値(UTC想定)を、
     * 対象タイムゾーンの日付(LocalDate)に変換する。
     * オフセット表記が無い場合はUTC登録である前提で扱う。
     * パース不能な場合は null を返す。
     */
    public static LocalDate toTargetZoneDate(String utcTimeText) {
        if (utcTimeText == null || utcTimeText.isBlank()) {
            return null;
        }
        Matcher m = DB_TIMESTAMP_PATTERN.matcher(utcTimeText.trim());
        if (!m.matches()) {
            return null; // 想定外フォーマットは変換不可
        }

        LocalDateTime ldt = LocalDateTime.parse(m.group(1) + "T" + m.group(2));
        String offsetPart = m.group(3);

        ZoneOffset offset;
        if (offsetPart == null || offsetPart.isEmpty() || "Z".equals(offsetPart)) {
            offset = ZoneOffset.UTC; // オフセット表記が無ければUTC前提
        } else {
            String normalized = offsetPart.length() == 3 ? offsetPart + ":00" : offsetPart; // "+00" -> "+00:00"
            offset = ZoneOffset.of(normalized);
        }

        OffsetDateTime odtUtc = ldt.atOffset(offset);
        return toOffsetDateTimeJst(odtUtc).toLocalDate();
    }
}