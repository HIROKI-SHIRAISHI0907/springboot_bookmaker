package dev.common.util;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;

/**
 * BookDataEntity.recordTime（文字列）を Timestamp に変換する共通処理。
 *
 * <h2>何をするクラスか</h2>
 * <p>
 * 各 BM の MapStruct Mapper（BM_M005 / BM_M019 など）で同じ変換を使うための共通クラス。
 * Mapper からは default メソッドで {@link #toTimestamp(String)} を呼ぶ。
 * </p>
 * <ul>
 *   <li>10桁の数字: UNIX 時刻（秒）/ 13桁の数字: UNIX 時刻（ミリ秒）</li>
 *   <li>"yyyy-MM-dd HH:mm[:ss][.小数(最大9桁)][オフセット]"（"/" 区切り・"T" 区切り・"Z" も可）</li>
 *   <li>"yyyy-MM-dd"（その日の 0:00）</li>
 * </ul>
 *
 * <h2>懸念点</h2>
 * <ul>
 *   <li><b>オフセットなしの日時は JVM のタイムゾーンで解釈する</b>（サーバの TZ が変わると時刻がずれる）。</li>
 *   <li><b>変換できない値は null</b>（例外にはしない）。</li>
 * </ul>
 */
public final class RecordTimeConverter {

	/** 日付＋時刻（区切りは "-" に、"T" は空白に正規化してから使う）。小数秒・オフセットは任意 */
	private static final DateTimeFormatter DATE_TIME = new DateTimeFormatterBuilder()
			.appendPattern("uuuu-MM-dd HH:mm")
			.optionalStart().appendPattern(":ss").optionalEnd()
			.optionalStart().appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true).optionalEnd()
			.optionalStart().appendOffset("+HH:mm", "Z").optionalEnd()
			.optionalStart().appendOffset("+HHmm", "Z").optionalEnd()
			.toFormatter();

	/** 日付のみ */
	private static final DateTimeFormatter DATE_ONLY = DateTimeFormatter.ofPattern("uuuu-MM-dd");

	private RecordTimeConverter() {
	}

	/**
	 * 記録時間の文字列を Timestamp に変換する（変換できなければ null）。
	 */
	public static Timestamp toTimestamp(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String s = value.trim();

		// 1) UNIX 時刻（10桁=秒、13桁=ミリ秒）
		if (s.matches("^\\d{10}$|^\\d{13}$")) {
			long n = Long.parseLong(s);
			return new Timestamp(s.length() == 10 ? n * 1000L : n);
		}

		// 2) 日付＋時刻（区切りを正規化）
		String normalized = s.replace('/', '-').replace('T', ' ');
		try {
			TemporalAccessor t = DATE_TIME.parseBest(normalized, OffsetDateTime::from, LocalDateTime::from);
			if (t instanceof OffsetDateTime) {
				return Timestamp.from(((OffsetDateTime) t).toInstant());
			}
			return Timestamp.valueOf((LocalDateTime) t);
		} catch (DateTimeParseException ignore) {
			// 次の形式を試す
		}

		// 3) 日付のみ
		try {
			return Timestamp.valueOf(LocalDate.parse(normalized, DATE_ONLY).atStartOfDay());
		} catch (DateTimeParseException ignore) {
			// 変換できない
		}
		return null;
	}
}