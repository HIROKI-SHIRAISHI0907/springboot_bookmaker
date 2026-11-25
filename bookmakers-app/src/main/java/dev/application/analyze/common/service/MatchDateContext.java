package dev.application.analyze.common.service;

import java.time.LocalDate;

/**
 * 今処理している試合の日付（record_time の日付）を、統計の各 Writer のシーズン判定に渡す（2026-11-26 追加）。
 * <p>CoreStat.execute の最初に {@link #set} し、finally で {@link #clear} する。
 * 設定されていれば {@link CountryLeagueSeasonResolver} は「今日」ではなくこの日付でシーズンを決める。</p>
 * <p>CoreStat は 1 回の execute で 1 試合（B006 は CSV 1 本、B018 は 1 試合）を処理する前提。
 * 複数の試合が来た場合は一番早い日付を使う。</p>
 */
public final class MatchDateContext {

	private static final ThreadLocal<LocalDate> DATE = new ThreadLocal<>();

	private MatchDateContext() {
	}

	public static void set(LocalDate date) {
		if (date == null) {
			DATE.remove();
		} else {
			DATE.set(date);
		}
	}

	/** 設定されていなければ null */
	public static LocalDate get() {
		return DATE.get();
	}

	public static void clear() {
		DATE.remove();
	}
}
