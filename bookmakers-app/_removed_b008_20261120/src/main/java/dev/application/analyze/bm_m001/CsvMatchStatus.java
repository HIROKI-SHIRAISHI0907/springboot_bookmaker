package dev.application.analyze.bm_m001;

import java.text.Normalizer;
import java.util.List;

import dev.common.entity.DataEntity;

/**
 * リアルタイムデータ（CSV）の試合時間（times）から、試合の状態を判定する（BM_M001 共通）。
 *
 * <ul>
 *   <li>「中断」「中断中」 … {@link #SUSPENDED}</li>
 *   <li>「延期」 … {@link #POSTPONED}</li>
 *   <li>「中止」 … {@link #CANCELLED}</li>
 *   <li>それ以外で値がある（23' / 45+2' / ハーフタイム / 終了済 など） … {@link #NORMAL}</li>
 *   <li>空 … null（判定しない）</li>
 * </ul>
 * @author shiraishitoshio
 */
public final class CsvMatchStatus {

	public static final String SUSPENDED = "中断";
	public static final String POSTPONED = "延期";
	public static final String CANCELLED = "中止";
	/** 通常の試合時間（delay_postpone には「再開」として出す） */
	public static final String NORMAL = "通常";
	/** delay_postpone の「再開」 */
	public static final String RESUMED = "再開";

	private CsvMatchStatus() {
	}

	/**
	 * 試合時間（times）から状態を判定する。
	 *
	 * @param times 試合時間
	 * @return 中断 / 延期 / 中止 / 通常。空なら null
	 */
	public static String classify(String times) {
		if (times == null) {
			return null;
		}
		String t = Normalizer.normalize(times, Normalizer.Form.NFKC).trim();
		if (t.isEmpty()) {
			return null;
		}
		// 「中断中」「中断」はどちらも中断
		if (t.contains("中断")) {
			return SUSPENDED;
		}
		if (t.contains("延期")) {
			return POSTPONED;
		}
		if (t.contains("中止")) {
			return CANCELLED;
		}
		return NORMAL;
	}

	/**
	 * CSV（1試合分）の最新の行（記録時刻が一番新しい行。同じならリストの後ろ）。
	 *
	 * @param rows 1試合分の行
	 * @return 最新の行（無ければ null）
	 */
	public static DataEntity latestRow(List<DataEntity> rows) {
		if (rows == null) {
			return null;
		}
		DataEntity best = null;
		String bestTime = null;
		for (DataEntity e : rows) {
			if (e == null) {
				continue;
			}
			String rt = e.getRecordTime() == null ? "" : e.getRecordTime().trim();
			if (best == null || rt.compareTo(bestTime) >= 0) {
				best = e;
				bestTime = rt;
			}
		}
		return best;
	}

	/**
	 * CSV（1試合分）の今の状態（最新の行の試合時間）。
	 *
	 * @param rows 1試合分の行
	 * @return 中断 / 延期 / 中止 / 通常。判定できなければ null
	 */
	public static String statusOf(List<DataEntity> rows) {
		DataEntity latest = latestRow(rows);
		return latest == null ? null : classify(latest.getTimes());
	}
}
