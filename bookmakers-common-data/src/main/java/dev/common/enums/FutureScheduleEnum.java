package dev.common.enums;

import java.util.Arrays;

/**
 * 未来データ関係定数
 * @author shiraishitoshio
 *
 */
public enum FutureScheduleEnum {

	/** ライブ中 */
	LIVE("ライブ中", "LIVE"),

	/** 試合終了済み */
	FINISHED("試合終了済み", "FINISHED"),

	/** 試合予定 */
	SCHEDULED("試合予定", "SCHEDULED"),

	/** 延期（本日予定の試合が別日になった） */
	POSTPONED("延期", "POSTPONED"),

	/** 遅延（本日予定の試合開始が遅れている or 試合中に雨等で中断） */
	DELAYED("遅延", "DELAYED"),

	/** 中断 */
	INTERRUPTED("中断", "INTERRUPTED");

	/** 日本語の意味 */
	private final String japaneseMeaning;

	/** 英単語コード */
	private final String code;

	FutureScheduleEnum(String japaneseMeaning, String code) {
		this.japaneseMeaning = japaneseMeaning;
		this.code = code;
	}

	/**
	 * 日本語の意味を取得
	 */
	public String getJapaneseMeaning() {
		return japaneseMeaning;
	}

	/**
	 * 英単語コードを取得
	 */
	public String getCode() {
		return code;
	}

	/**
	 * 文字列がこのステータスと一致するか判定
	 */
	public boolean is(String value) {
		return code.equals(trim(value));
	}

	/**
	 * コード文字列から enum を取得
	 */
	public static FutureScheduleEnum fromCode(String code) {
		String trimmed = trim(code);
		return Arrays.stream(values())
				.filter(v -> v.code.equals(trimmed))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Unknown future schedule code: " + code));
	}

	/**
	 * コード文字列から日本語の意味を取得
	 */
	public static String toJapaneseMeaning(String code) {
		return fromCode(code).getJapaneseMeaning();
	}

	/**
	 * null-safe trim
	 */
	private static String trim(String value) {
		return value == null ? null : value.trim();
	}

	@Override
	public String toString() {
		return code;
	}
}
