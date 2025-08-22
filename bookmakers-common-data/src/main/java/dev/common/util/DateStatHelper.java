package dev.common.util;

import java.time.Clock;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stat用日付変換ヘルパー
 * @author shiraishitoshio
 *
 */
public final class DateStatHelper {

	/** 正規表現 */
    private static final Pattern D_M = Pattern.compile("^\\s*(\\d{1,2})[.／/\\-](\\d{1,2})\\s*$");
    private DateStatHelper() {}

    /**
     * "d.M"（例: 16.8）→ "M/d"（例: 8/16）
     * @param raw
     * @return
     */
    public static String toMonthDay(String raw) {
        if (raw == null) return "";
        String s = normalizeSep(raw);
        Matcher m = D_M.matcher(s);
        if (!m.matches()) return raw; // 想定外は元文字列のまま返す

        int day   = clamp(parseIntSafe(m.group(1)), 1, 31);
        int month = clamp(parseIntSafe(m.group(2)), 1, 12);
        return month + "/" + day; // 例: 8/16
    }

    /**
     * "d.M" → "yyyy-MM-dd"（年は引数で指定）
     * @param raw
     * @return
     */
    public static String toIsoFromDayMonth(String raw, Clock clock) {
        if (raw == null) return "";
        String s = normalizeSep(raw);
        Matcher m = D_M.matcher(s);
        if (!m.matches()) return raw;

        int year  = LocalDate.now(clock).getYear();
        int day   = clamp(parseIntSafe(m.group(1)), 1, 31);
        int month = clamp(parseIntSafe(m.group(2)), 1, 12);
        return String.format("%04d-%02d-%02d", year, month, day);
    }

    // 区切りをドット相当として統一
    private static String normalizeSep(String s) {
        return s.trim()
                .replace('／','/')
                .replace('・','.')
                .replace('/', '.')
                .replace('-', '.');
    }
    private static int parseIntSafe(String s) { try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; } }
    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
}
