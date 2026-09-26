package dev.web.api.bm_a013;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 指定日 [00:00, 翌00:00) の範囲（タイムゾーン考慮）
 */
@Getter
@AllArgsConstructor
public class DateRange {

	private final LocalDate date;
	private final ZoneId zone;
	private final Instant start;
	private final Instant end;

	public static DateRange of(LocalDate date, ZoneId zone) {
		ZonedDateTime s = date.atStartOfDay(zone);
		return new DateRange(date, zone, s.toInstant(), s.plusDays(1).toInstant());
	}

	/** 範囲の終わりが未来なら現在時刻で切る（CloudTrail / CloudWatch 用） */
	public Instant endOrNow() {
		Instant now = Instant.now();
		return end.isAfter(now) ? now : end;
	}

	public int hourOf(Instant t) {
		return t.atZone(zone).getHour();
	}

	public String format(Instant t) {
		return t == null ? null : t.atZone(zone).toOffsetDateTime().toString();
	}
}
