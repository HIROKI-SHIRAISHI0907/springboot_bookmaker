package dev.common.util;

import java.nio.charset.StandardCharsets;

/**
 * CSV の中身の判定（2026-10 追加）。
 *
 * <p>S3 から取ったバッチ入力の CSV が「ヘッダしかない（データ行が 0 件）」かを、読み込みクラス（ReadXxx）に渡す前に判定する。
 * 読み込みクラスがヘッダのみのファイルをどう返すか（空のリスト／異常コード／null）に左右されずに、
 * 「ヘッダのみ = 登録せず削除する」を決められるようにするため。</p>
 */
public final class CsvContentUtil {

	private CsvContentUtil() {
	}

	/**
	 * ヘッダのみ（または空）か。
	 * BOM を除き、空白だけの行・カンマ／引用符だけの行は数えない。中身のある行が 1 行以下ならヘッダのみとみなす。
	 *
	 * @param bytes CSV のバイト列（UTF-8 想定）
	 * @return ヘッダのみ・空なら true
	 */
	public static boolean isHeaderOnly(byte[] bytes) {
		if (bytes == null || bytes.length == 0) {
			return true;
		}
		return isHeaderOnly(new String(bytes, StandardCharsets.UTF_8));
	}

	/**
	 * ヘッダのみ（または空）か。
	 *
	 * @param text CSV の文字列
	 * @return ヘッダのみ・空なら true
	 */
	public static boolean isHeaderOnly(String text) {
		if (text == null) {
			return true;
		}
		String t = text.startsWith("﻿") ? text.substring(1) : text;
		int contentLines = 0;
		for (String line : t.split("\\r\\n|\\r|\\n")) {
			if (line.replaceAll("[\\s,\"']", "").isEmpty()) {
				continue;
			}
			contentLines++;
			if (contentLines > 1) {
				return false;
			}
		}
		return true;
	}
}