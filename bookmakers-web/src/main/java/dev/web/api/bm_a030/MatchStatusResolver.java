package dev.web.api.bm_a030;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 指定日の試合予定一覧の「システムデータ」「リアルタイム情報」の状態を決める
 * （仕様: 試合予定データ画面.xlsx / 【テスト設計書】試合予定データ画面_パターンマスタ.xlsx）。
 *
 * <h2>判定の順番</h2>
 * <ol>
 *   <li><b>延期・中止</b>（開始フラグに関係なく最優先）
 *     <ul>
 *       <li>システム: 途中までのデータあり（中断からの延期など）=「更新なし」、無し=「延期」「中止」</li>
 *       <li>リアルタイム: 「延期」「中止」</li>
 *     </ul></li>
 *   <li><b>開始前（start_flg ≠ 1）</b>
 *     <ul>
 *       <li>システム: 「データなし」（データがあっても変えない）</li>
 *       <li>リアルタイム: 遅延の情報があれば「遅延」、それ以外は「開始前」（中断の情報があっても表示しない）</li>
 *     </ul></li>
 *   <li><b>終了済データあり</b> … システム・リアルタイムとも「終了済」</li>
 *   <li><b>中断</b>（中断の情報が残っている間は中断を優先。通常の試合時間が取れると
 *       バッチが delay_postpone から中断の情報を削除するので、通常の判定に戻る）
 *     <ul>
 *       <li>システム: 開始から4時間以内=「中断」、超えたら「終了済」</li>
 *       <li>リアルタイム: システムデータと同じ</li>
 *     </ul></li>
 *   <li><b>通常</b>（開始後の遅延情報は無視する）
 *     <ul>
 *       <li>システム: データなし=「データなし」、最後の記録から40分以内=「更新あり」、それ以外=「更新なし」</li>
 *       <li>リアルタイム: 開始から2時間10分以内=「ライブ」、超えたら「終了済」（強制）</li>
 *     </ul></li>
 * </ol>
 * <p>
 * 「開始した」は start_flg = 1 だけで判断する（データの有無・試合開始日時は見ない）。
 * データの件数・最後の記録には、試合時間が「中断」「延期」「中止」の行を含めない（FutureMatchDataStatusRepository）。
 * 時刻はすべて日本時間の壁時計（future_time / record_time は timestamp）で比べる。
 * </p>
 * @author shiraishitoshio
 *
 */
public final class MatchStatusResolver {

	/* ---- システムデータ ---- */
	public static final String SYS_UPDATED = "更新あり";
	public static final String SYS_NOT_UPDATED = "更新なし";
	public static final String SYS_FINISHED = "終了済";
	public static final String SYS_NO_DATA = "データなし";

	/* ---- リアルタイム情報 ---- */
	public static final String RT_BEFORE = "開始前";
	public static final String RT_LIVE = "ライブ";
	public static final String RT_FINISHED = "終了済";

	/* ---- S3 の延期・遅延情報（status_type） ---- */
	public static final String POSTPONED = "延期";
	public static final String CANCELLED = "中止";
	public static final String SUSPENDED = "中断";
	public static final String DELAYED = "遅延";

	/** 最後の記録からこれ以内なら更新あり */
	public static final Duration UPDATED_WITHIN = Duration.ofMinutes(40);

	/** リアルタイム: 開始からこれを超えたら終了済 */
	public static final Duration LIVE_UNTIL = Duration.ofMinutes(130);

	/** 中断: 開始からこれを超えたら終了済 */
	public static final Duration SUSPENDED_FINISH_AFTER = Duration.ofHours(4);

	private static final DateTimeFormatter LOCAL_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private MatchStatusResolver() {
	}

	/** 1つの状態と理由 */
	public static final class Status {
		public final String status;
		/** 理由（画面のマウスオーバー用。無ければ null） */
		public final String reason;

		Status(String status, String reason) {
			this.status = status;
			this.reason = reason;
		}
	}

	/** 判定結果 */
	public static final class Result {
		public final Status system;
		public final Status realtime;

		Result(Status system, Status realtime) {
			this.system = system;
			this.realtime = realtime;
		}
	}

	/** 判定に使う値 */
	public static final class Input {
		/** 試合開始日時（"yyyy-MM-dd HH:mm:ss"・日本時間） */
		public String futureTimeLocal;
		/** future_master.start_flg */
		public String startFlg;
		/** times = 終了済 の行数 */
		public int finishedCount;
		/** それ以外の行数（中断・延期・中止の行は含めない） */
		public int liveCount;
		/** 最後の記録日時（ISO・+09:00。中断・延期・中止の行は含めない） */
		public String lastUpdatedAt;
		/** S3 の status_type（延期 / 遅延 / 中断 / 中止。古いファイルの「再開」は通常扱い） */
		public String delayStatusType;
	}

	public static Result resolve(Input in, LocalDateTime now) {
		String delay = trimToNull(in.delayStatusType);
		LocalDateTime start = parseLocal(in.futureTimeLocal);
		LocalDateTime last = parseOffset(in.lastUpdatedAt);
		boolean hasData = in.finishedCount + in.liveCount > 0;
		boolean recentlyUpdated = last != null && !now.isAfter(last.plus(UPDATED_WITHIN));
		boolean started = "1".equals(trimToNull(in.startFlg));

		// 1) 延期・中止（開始フラグに関係なく優先。延期になると start_flg は 0 に戻る）
		if (POSTPONED.equals(delay) || CANCELLED.equals(delay)) {
			Status system = hasData
					? new Status(SYS_NOT_UPDATED, "試合が" + delay + "（途中までのデータあり）")
					: new Status(delay, "試合が" + delay);
			return new Result(system, new Status(delay, "試合が" + delay));
		}

		// 2) 開始前（start_flg = 1 になるまでは、データがあっても変えない）
		if (!started) {
			Status realtime = DELAYED.equals(delay)
					? new Status(DELAYED, "試合開始が遅延")
					: new Status(RT_BEFORE, null);
			return new Result(new Status(SYS_NO_DATA, null), realtime);
		}

		// 3) 終了済データが取れていれば終了済
		if (in.finishedCount > 0) {
			return new Result(new Status(SYS_FINISHED, "終了済データあり"), new Status(RT_FINISHED, "終了済データあり"));
		}

		// 4) 中断（中断の情報が残っている間は中断を優先）
		if (SUSPENDED.equals(delay)) {
			if (start != null && now.isAfter(start.plus(SUSPENDED_FINISH_AFTER))) {
				String reason = "中断のまま開始から4時間を超えたため";
				return new Result(new Status(SYS_FINISHED, reason), new Status(RT_FINISHED, reason));
			}
			return new Result(new Status(SUSPENDED, "試合が中断"), new Status(SUSPENDED, "試合が中断"));
		}

		// 5) 通常（開始後の遅延情報は表示しない）
		Status system;
		if (!hasData) {
			system = new Status(SYS_NO_DATA, "開始済みだがデータ未取得");
		} else if (recentlyUpdated) {
			system = new Status(SYS_UPDATED, null);
		} else {
			system = new Status(SYS_NOT_UPDATED, "最後の記録から40分以上更新が無いため");
		}
		Status realtime;
		if (start != null && now.isAfter(start.plus(LIVE_UNTIL))) {
			realtime = new Status(RT_FINISHED, "試合開始から2時間10分を超えたため");
		} else {
			realtime = new Status(RT_LIVE, null);
		}
		return new Result(system, realtime);
	}

	private static String trimToNull(String s) {
		if (s == null) {
			return null;
		}
		String t = s.trim();
		return t.isEmpty() ? null : t;
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
