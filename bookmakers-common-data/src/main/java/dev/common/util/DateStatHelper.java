package dev.common.util;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 日付管理ヘルパー
 * @author shiraishitoshio
 *
 */
public final class DateStatHelper {

    private DateStatHelper() {}

    // 2つの数 (x-y / x.y / x/y / x-y) を拾う
    private static final Pattern TWO_NUMS = Pattern.compile("^\\s*(\\d{1,2})[.／/\\-](\\d{1,2})\\s*$");

    // ISO (yyyy-M-d / yyyy/M/d)
    private static final Pattern ISO_YMD = Pattern.compile("^\\s*(\\d{4})[-/](\\d{1,2})[-/](\\d{1,2})\\s*$");

    // dd.MM.yyyy HH:mm / dd/MM/yyyy HH:mm / dd-MM-yyyy HH:mm
    private static final Pattern DMY_HM = Pattern.compile(
            "^\\s*(\\d{1,2})[.／/\\-](\\d{1,2})[.／/\\-](\\d{4})\\s+(\\d{1,2}):(\\d{2})\\s*$");

    // dd.MM.yyyy HH:mm:ss / dd/MM/yyyy HH:mm:ss / dd-MM-yyyy HH:mm:ss
    private static final Pattern DMY_HMS = Pattern.compile(
            "^\\s*(\\d{1,2})[.／/\\-](\\d{1,2})[.／/\\-](\\d{4})\\s+(\\d{1,2}):(\\d{2}):(\\d{2})\\s*$");

    // yyyy-MM-dd HH:mm / yyyy/MM/dd HH:mm
    private static final Pattern ISO_YMD_HM = Pattern.compile(
            "^\\s*(\\d{4})[-/](\\d{1,2})[-/](\\d{1,2})\\s+(\\d{1,2}):(\\d{2})\\s*$");

    // yyyy-MM-dd HH:mm:ss / yyyy/MM/dd HH:mm:ss
    private static final Pattern ISO_YMD_HMS = Pattern.compile(
            "^\\s*(\\d{4})[-/](\\d{1,2})[-/](\\d{1,2})\\s+(\\d{1,2}):(\\d{2}):(\\d{2})\\s*$");

    private static final DateTimeFormatter DATETIME_TEXT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static final class SeasonIso {
        public final String startIso;
        public final String endIso;
        public SeasonIso(String s, String e) { this.startIso = s; this.endIso = e; }
    }

    /**
     * start/end を同時に変換（年跨ぎは end 年+1）
     * @param startRaw
     * @param endRaw
     * @param clock
     * @return
     */
    public static SeasonIso toSeasonIso(String startRaw, String endRaw, Clock clock) {
        LocalDate now = LocalDate.now(clock);

        LocalDate start = parseToLocalDate(startRaw, now.getYear());
        LocalDate end   = parseToLocalDate(endRaw,   now.getYear());

        // どちらかが年なしの M-d／d-M 由来で両方とも年なし入力だった場合、
        // start の年を基準にそろえる
        if (start != null && end != null) {
            if (isYearless(startRaw)) {
                start = withYearPreservingMonthDay(start, now.getYear());
            }
            if (isYearless(endRaw)) {
                end = withYearPreservingMonthDay(end, now.getYear());
            }
            // 年跨ぎ補正：end が start より前なら end 年+1
            if (end.isBefore(start)) {
                end = end.plusYears(1);
            }
        }

        return new SeasonIso(
            start == null ? null : start.toString(),
            end   == null ? null : end.toString()
        );
    }

    /**
     * 年入り or 月日だけを LocalDate に。月日だけの場合は baseYear を仮で当てる
     * @param raw
     * @param baseYear
     * @return
     */
    private static LocalDate parseToLocalDate(String raw, int baseYear) {
        if (raw == null) return null;
        String t = raw.trim();
        if (t.isEmpty()) return null;

        // yyyy[-/]M[-/]d を優先
        Matcher iso = ISO_YMD.matcher(t);
        if (iso.matches()) {
            int y = clamp(parseIntSafe(iso.group(1)), 1900, 9999);
            int m = clamp(parseIntSafe(iso.group(2)), 1, 12);
            int d = clampDayToMonth(y, m, parseIntSafe(iso.group(3)));
            return LocalDate.of(y, m, d);
        }

        // M-d / d-M（区切りは . ／ / - を許容）
        String s = normalizeSep(t);
        Matcher md = TWO_NUMS.matcher(s);
        if (!md.matches()) return null;

        int a = clamp(parseIntSafe(md.group(1)), 1, 31);
        int b = clamp(parseIntSafe(md.group(2)), 1, 31);

        // ヘuristic:
        //  - どちらかが >12 なら その数を day とみなす
        //  - 両方 <=12（曖昧）は「M-d（先月後日）」とみなす
        int month, day;
        if (a > 12 && b <= 12) { month = b; day = a; }           // d-M
        else if (b > 12 && a <= 12) { month = a; day = b; }      // M-d
        else { month = a; day = b; }                             // 曖昧は M-d とする

        day = clampDayToMonth(baseYear, month, day);
        return LocalDate.of(baseYear, month, day);
    }

    /**
     * 各種日時文字列を LocalDateTime に変換する。
     *
     * 対応例:
     * - 14.07.2026 07:00
     * - 14.07.2026 07:00:59
     * - 2026-07-14 07:00
     * - 2026-07-14 07:00:59
     * - 2026/07/14 07:00
     * - 2026/07/14 07:00:59
     *
     * @param raw
     * @return LocalDateTime（変換できない場合は null）
     */
    public static LocalDateTime toLocalDateTime(String raw) {
        if (raw == null) return null;

        String t = raw.trim();
        if (t.isEmpty()) return null;

        Matcher dmyHm = DMY_HM.matcher(t);
        if (dmyHm.matches()) {
            int d = clamp(parseIntSafe(dmyHm.group(1)), 1, 31);
            int m = clamp(parseIntSafe(dmyHm.group(2)), 1, 12);
            int y = clamp(parseIntSafe(dmyHm.group(3)), 1900, 9999);
            int hh = clamp(parseIntSafe(dmyHm.group(4)), 0, 23);
            int mm = clamp(parseIntSafe(dmyHm.group(5)), 0, 59);
            d = clampDayToMonth(y, m, d);
            return LocalDateTime.of(y, m, d, hh, mm, 0);
        }

        Matcher dmyHms = DMY_HMS.matcher(t);
        if (dmyHms.matches()) {
            int d = clamp(parseIntSafe(dmyHms.group(1)), 1, 31);
            int m = clamp(parseIntSafe(dmyHms.group(2)), 1, 12);
            int y = clamp(parseIntSafe(dmyHms.group(3)), 1900, 9999);
            int hh = clamp(parseIntSafe(dmyHms.group(4)), 0, 23);
            int mm = clamp(parseIntSafe(dmyHms.group(5)), 0, 59);
            int ss = clamp(parseIntSafe(dmyHms.group(6)), 0, 59);
            d = clampDayToMonth(y, m, d);
            return LocalDateTime.of(y, m, d, hh, mm, ss);
        }

        Matcher isoHm = ISO_YMD_HM.matcher(t);
        if (isoHm.matches()) {
            int y = clamp(parseIntSafe(isoHm.group(1)), 1900, 9999);
            int m = clamp(parseIntSafe(isoHm.group(2)), 1, 12);
            int d = clampDayToMonth(y, m, parseIntSafe(isoHm.group(3)));
            int hh = clamp(parseIntSafe(isoHm.group(4)), 0, 23);
            int mm = clamp(parseIntSafe(isoHm.group(5)), 0, 59);
            return LocalDateTime.of(y, m, d, hh, mm, 0);
        }

        Matcher isoHms = ISO_YMD_HMS.matcher(t);
        if (isoHms.matches()) {
            int y = clamp(parseIntSafe(isoHms.group(1)), 1900, 9999);
            int m = clamp(parseIntSafe(isoHms.group(2)), 1, 12);
            int d = clampDayToMonth(y, m, parseIntSafe(isoHms.group(3)));
            int hh = clamp(parseIntSafe(isoHms.group(4)), 0, 23);
            int mm = clamp(parseIntSafe(isoHms.group(5)), 0, 59);
            int ss = clamp(parseIntSafe(isoHms.group(6)), 0, 59);
            return LocalDateTime.of(y, m, d, hh, mm, ss);
        }

        return null;
    }

    /**
     * 各種日時文字列を DB投入向けの yyyy-MM-dd HH:mm:ss に変換する。
     *
     * 例:
     * 14.07.2026 07:00 -> 2026-07-14 07:00:00
     *
     * @param raw
     * @return 変換後文字列（変換できない場合は null）
     */
    public static String toDateTimeText(String raw) {
        LocalDateTime dt = toLocalDateTime(raw);
        if (dt == null) {
            return null;
        }
        return dt.format(DATETIME_TEXT);
    }

    private static boolean isYearless(String raw) {
        if (raw == null) return true;
        return !ISO_YMD.matcher(raw.trim()).matches();
    }

    private static LocalDate withYearPreservingMonthDay(LocalDate d, int year) {
        int m = d.getMonthValue();
        int dd = clampDayToMonth(year, m, d.getDayOfMonth());
        return LocalDate.of(year, m, dd);
    }

    // 区切りをドット相当に統一
    private static String normalizeSep(String s) {
        return s.trim()
                .replace('／','/')
                .replace('・','.')
                .replace('/', '.')
                .replace('-', '.');
    }

    private static int parseIntSafe(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static int clampDayToMonth(int year, int month, int day) {
        int max = YearMonth.of(year, month).lengthOfMonth();
        return clamp(day, 1, max);
    }
}
