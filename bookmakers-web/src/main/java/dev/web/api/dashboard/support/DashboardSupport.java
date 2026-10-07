package dev.web.api.dashboard.support;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * トップ画面の共通処理（カテゴリの分解・数値の読み取り・試合時間の読み取り）
 * @author shiraishitoshio
 *
 */
public final class DashboardSupport {

	/** 画面の時刻は JST */
	public static final ZoneId JST = ZoneId.of("Asia/Tokyo");

	/** 「ラウンド N」 */
	private static final Pattern ROUND = Pattern.compile("ラウンド\\s*(\\d+)");

	/** "67'" "45+2'" "90+3" */
	private static final Pattern MINUTE = Pattern.compile("^(\\d+)(?:\\+(\\d+))?'?$");

	/** "67:12" */
	private static final Pattern MM_SS = Pattern.compile("^(\\d+):(\\d{1,2})$");

	/** 最初に出てくる数値（"56%" → 56、"1.84" → 1.84） */
	private static final Pattern NUMBER = Pattern.compile("[-+]?\\d+(?:\\.\\d+)?");

	private DashboardSupport() {
	}

	/** NFKC・前後空白除去・連続空白を1つに（チーム名などの突き合わせ用） */
	public static String norm(String s) {
		if (s == null) {
			return "";
		}
		return Normalizer.normalize(s, Normalizer.Form.NFKC).trim().replaceAll("\\s+", " ");
	}

	/**
	 * カテゴリ「国: リーグ - ラウンド N」を {国, リーグ, ラウンド番号(無ければ null)} に分ける。国が無ければ null。
	 */
	public static String[] splitCategory(String dataCategory) {
		String s = norm(dataCategory);
		int colon = s.indexOf(':');
		if (colon <= 0) {
			return null;
		}
		String country = s.substring(0, colon).trim();
		String rest = s.substring(colon + 1).trim();
		int dash = rest.indexOf(" - ");
		String league = (dash >= 0 ? rest.substring(0, dash) : rest).trim();
		Matcher m = ROUND.matcher(rest);
		String round = m.find() ? m.group(1) : null;
		if (country.isEmpty() || league.isEmpty()) {
			return null;
		}
		return new String[] { country, league, round };
	}

	/** 整数（"1" "1.0"）。読めなければ null */
	public static Integer toInt(String s) {
		Double d = toDouble(s);
		return d == null ? null : (int) Math.round(d);
	}

	/** 数値（"1.84" "56%"）。読めなければ null */
	public static Double toDouble(String s) {
		if (s == null) {
			return null;
		}
		Matcher m = NUMBER.matcher(s.replace(",", ""));
		if (!m.find()) {
			return null;
		}
		try {
			return Double.valueOf(m.group());
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** data.record_time（"2026-10-07 21:34:05" など）を JST の日時に。読めなければ null */
	public static LocalDateTime toLocalDateTime(String s) {
		if (s == null || s.isBlank()) {
			return null;
		}
		String t = s.trim().replace('T', ' ');
		int dot = t.indexOf('.');
		if (dot > 0) {
			t = t.substring(0, dot);
		}
		if (t.length() == 16) {
			t = t + ":00";
		}
		try {
			return LocalDateTime.parse(t, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
		} catch (DateTimeParseException e) {
			return null;
		}
	}

	/**
	 * 試合時間の読み取り結果
	 */
	public static final class MatchClock {
		/** LIVE / HT */
		public final String status;
		/** 表示用（"67'" / "HT" / 元の値） */
		public final String label;
		/** 進み具合 0〜100 */
		public final int progress;
		/** 残り時間の割合（0.02〜1） */
		public final double remaining;

		MatchClock(String status, String label, int progress, double remaining) {
			this.status = status;
			this.label = label;
			this.progress = progress;
			this.remaining = remaining;
		}
	}

	/**
	 * 試合時間（"67'" "45+2'" "67:12" "ハーフタイム" "第一ハーフ" など）を読む。
	 */
	public static MatchClock readClock(String times) {
		String t = norm(times);
		if ("ハーフタイム".equals(t)) {
			return new MatchClock("HT", "HT", 50, 0.5);
		}
		Integer minute = null;
		String label = null;
		Matcher m = MINUTE.matcher(t);
		if (m.matches()) {
			label = t.endsWith("'") ? t : t + "'";
			minute = Integer.parseInt(m.group(1)) + (m.group(2) == null ? 0 : Integer.parseInt(m.group(2)));
		} else {
			Matcher mm = MM_SS.matcher(t);
			if (mm.matches()) {
				minute = Integer.parseInt(mm.group(1));
				label = minute + "'";
			}
		}
		if (minute != null) {
			int progress = (int) Math.min(100, Math.round(minute * 100.0 / 90));
			double remaining = clamp((90 - minute) / 90.0, 0.02, 1.0);
			return new MatchClock("LIVE", label, progress, remaining);
		}
		if (t.contains("第一")) {
			return new MatchClock("LIVE", t, 25, 0.75);
		}
		if (t.contains("第二")) {
			return new MatchClock("LIVE", t, 75, 0.25);
		}
		return new MatchClock("LIVE", t.isEmpty() ? "-" : t, 50, 0.5);
	}

	/** ライブではない（終了・延期・中止など） */
	public static boolean isNotLive(String times) {
		String t = norm(times);
		return t.isEmpty() || "終了済".equals(t) || t.contains("延期") || t.contains("中止") || t.contains("中断")
				|| t.contains("キャンセル") || t.contains("ペナルティ");
	}

	/** 終了 */
	public static boolean isFinished(String times) {
		return "終了済".equals(norm(times));
	}

	public static double clamp(double v, double min, double max) {
		return Math.max(min, Math.min(max, v));
	}

	/** 表示用「国 / リーグ」 */
	public static String leagueLabel(String country, String league) {
		return country + " / " + league;
	}
}
