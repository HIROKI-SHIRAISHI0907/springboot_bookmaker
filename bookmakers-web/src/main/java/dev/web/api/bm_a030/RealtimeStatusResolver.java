package dev.web.api.bm_a030;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Set;

/**
 * リアルタイムデータの状態を決める（指定日の試合予定一覧）
 *
 * <p>判定の順番</p>
 * <ol>
 *   <li>延期・中断・中止（S3 の延期・遅延情報）… 試合が行われていないので、時間に関係なくその表記のまま</li>
 *   <li>試合開始日時から 2時間30分を超えた … 強制的に「終了済」</li>
 *   <li>最後の記録から 40分以内 … 「更新中」</li>
 *   <li>記録はあるが 40分以上滞っている … 「更新なし」</li>
 *   <li>記録が無く、遅延の情報がある … 「遅延」</li>
 *   <li>それ以外 … 「更新なし」</li>
 * </ol>
 * 時刻はすべて日本時間の壁時計（future_time / record_time は timestamp）で比べる。
 * @author shiraishitoshio
 *
 */
public final class RealtimeStatusResolver {

	public static final String LIVE = "更新中";
	public static final String FINISHED = "終了済";
	public static final String NO_UPDATE = "更新なし";

	/** 試合が行われていない（時間が経っても終了済にしない）状態 */
	public static final Set<String> NOT_PLAYED = Set.of("延期", "中断", "中止");

	/** 試合開始からこれを超えたら強制的に終了済 */
	public static final Duration FORCE_FINISH_AFTER = Duration.ofMinutes(150);

	/** 最後の記録からこれを超えたら更新なし */
	public static final Duration STALE_AFTER = Duration.ofMinutes(40);

	private static final DateTimeFormatter LOCAL_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private RealtimeStatusResolver() {
	}

	/** 判定結果 */
	public static final class Result {
		/** 表示する状態（更新中 / 終了済 / 更新なし / 延期 / 中断 / 中止 / 遅延） */
		public final String status;
		/** なぜその状態になったか（画面のマウスオーバー用） */
		public final String reason;

		Result(String status, String reason) {
			this.status = status;
			this.reason = reason;
		}
	}

	/**
	 * @param futureTimeLocal 試合開始日時（"yyyy-MM-dd HH:mm:ss"・日本時間。null 可）
	 * @param lastUpdatedAt 最後の記録日時（"yyyy-MM-ddTHH:mm:ss+09:00"。null 可）
	 * @param delayStatusType S3 の status_type（延期 / 遅延 / 中断 / 中止。null 可）
	 * @param now 現在日時（日本時間）
	 */
	public static Result resolve(String futureTimeLocal, String lastUpdatedAt, String delayStatusType, LocalDateTime now) {
		String delay = delayStatusType == null ? null : delayStatusType.trim();
		if (delay != null && delay.isEmpty()) {
			delay = null;
		}

		// 1) 延期・中断・中止は試合が行われていないので、そのまま
		if (delay != null && NOT_PLAYED.contains(delay)) {
			return new Result(delay, "試合が" + delay + "のため");
		}

		LocalDateTime start = parseLocal(futureTimeLocal);
		LocalDateTime last = parseOffset(lastUpdatedAt);

		// 2) 開始から 2時間30分を超えたら強制的に終了済
		if (start != null && now.isAfter(start.plus(FORCE_FINISH_AFTER))) {
			return new Result(FINISHED, "試合開始から2時間30分を超えたため");
		}

		if (last != null) {
			// 3) 最後の記録から 40分以内なら更新中
			if (!now.isAfter(last.plus(STALE_AFTER))) {
				return new Result(LIVE, null);
			}
			// 4) 40分以上滞っていれば更新なし
			return new Result(NO_UPDATE, "最後の記録から40分以上更新が無いため");
		}

		// 5) 記録が無く、遅延の情報がある
		if (delay != null) {
			return new Result(delay, "試合が" + delay + "しているため");
		}

		// 6) それ以外
		return new Result(NO_UPDATE, null);
	}

	private static LocalDateTime parseLocal(String s) {
		if (s == null || s.isBlank()) {
			return null;
		}
		try {
			return LocalDateTime.parse(s.trim(), LOCAL_FMT);
		} catch (DateTimeParseException e) {
			return null;
		}
	}

	private static LocalDateTime parseOffset(String s) {
		if (s == null || s.isBlank()) {
			return null;
		}
		try {
			return OffsetDateTime.parse(s.trim()).atZoneSameInstant(FutureMatchAdminService.JST).toLocalDateTime();
		} catch (DateTimeParseException e) {
			return null;
		}
	}
}
