package dev.application.analyze.bm_m023;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Triple;

import dev.common.entity.BookDataEntity;

/**
 * 統計データフォーマット関係リゾルバー
 * @author shiraishitoshio
 *
 */
public abstract class StatFormatResolver {

	/**
	 * スコアパターン補助メソッド
	 * @param entities
	 * @return
	 */
	protected List<String> extractExistingScorePatterns(List<BookDataEntity> entities) {
		return entities.stream()
				.map(e -> e.getHomeScore() + "-" + e.getAwayScore())
				.distinct()
				.collect(Collectors.toList());
	}

	/**
	 * 初期化時の値を、元の文字列形式に基づいて適切に戻す
	 * 例:
	 * - "65%" → "0.0%"
	 * - "65% (13/20)" → "0.0% (0/0)"
	 */
	protected String getInitialValueByFormat(String valueStr) {
		if (valueStr == null || valueStr.isBlank()) {
			return "0.0";
		}
		// % (x/y) が含まれる場合
		if (valueStr.matches(".*%\\s*\\(\\s*\\d+\\s*/\\s*\\d+\\s*\\).*")) {
			return "0.0% (0/0)";
		}
		// % だけの場合
		if (valueStr.contains("%")) {
			return "0.0%";
		}
		// 通常の数値
		return "0.0";
	}

	/**
	 * 値が "X% (X/X)" 形式かを判定
	 */
	protected boolean isPercentAndFractionFormat(String valueStr) {
		return valueStr != null
				&& valueStr.contains("%")
				&& valueStr.contains("(")
				&& valueStr.contains("/")
				&& valueStr.contains(")");
	}

	/**
	 * 同じフォーマットのみ比較
	 * @param a
	 * @param b
	 * @return
	 */
	protected boolean isSameFormat(String a, String b) {
		if (a == null || b == null)
			return false;
		// "0.0%" vs "65%" → OK, "0.0% (0/0)" vs "70% (13/20)" → OK
		if (a.contains("% (") && b.contains("% ("))
			return true;
		if (a.contains("%") && b.contains("%") && !a.contains("(") && !b.contains("("))
			return true;
		if (a.contains("/") && b.contains("/") && !a.contains("%"))
			return true;
		if (!a.contains("%") && !a.contains("/") && !b.contains("%") && !b.contains("/"))
			return true;
		return false;
	}

	/**
	 * データのパース
	 * @param valueStr
	 * @return
	 */
	// StatFormatResolver.java
	protected String parseStatValue(String raw) {
	    if (raw == null) return null;
	    String s = raw.trim();
	    if (s.isEmpty()) return null;

	    // 12.3% (3/8)  → 分子を返す "3"
	    // 小数% も許容 / 空白任意
	    java.util.regex.Matcher m = java.util.regex.Pattern
	        .compile("^(?<pct>\\d+(?:\\.\\d+)?)%\\s*\\(\\s*(?<num>\\d+)\\s*/\\s*(?<den>\\d+)\\s*\\)\\s*$")
	        .matcher(s);
	    if (m.matches()) {
	        return m.group("num");
	    }

	    // (3/8) → "3"
	    m = java.util.regex.Pattern
	        .compile("^\\(\\s*(?<num>\\d+)\\s*/\\s*(?<den>\\d+)\\s*\\)\\s*$")
	        .matcher(s);
	    if (m.matches()) {
	        return m.group("num");
	    }

	    // 12.3% → "12.3"
	    m = java.util.regex.Pattern
	        .compile("^(?<pct>\\d+(?:\\.\\d+)?)%\\s*$")
	        .matcher(s);
	    if (m.matches()) {
	        return m.group("pct");
	    }

	    // 純数値（整数/小数）
	    if (s.matches("^[+-]?\\d+(?:\\.\\d+)?$")) {
	        return s;
	    }

	    // それ以外は不正フォーマットとしてスキップ
	    return null;
	}

	/**
	 * フォーマット変換
	 * @param value
	 * @return
	 */
	protected String formatDecimal(String value) {
		if (value == null || value.isEmpty())
			return "0.00";
		try {
			double d = Double.parseDouble(value.replaceAll("'", ""));
			return String.format("%.2f", d);
		} catch (NumberFormatException e) {
			return value; // 不正な値でも安全に対応
		}
	}

	/**
	 * 3分割データ
	 * @param valueStr
	 * @return
	 */
	protected Triple<String, String, String> splitPercentageWithFraction(String valueStr) {
		if (StringUtils.isBlank(valueStr)) return null;

	    // 12.3% (3/8) を厳密にパース（空白は可）
	    final Pattern P = Pattern.compile(
	        "^\\s*(?<pct>\\d+(?:\\.\\d+)?)%\\s*\\(\\s*(?<num>\\d+)\\s*/\\s*(?<den>\\d+)\\s*\\)\\s*$"
	    );

	    try {
	        Matcher m = P.matcher(valueStr);
	        if (!m.matches()) return null;
	        return Triple.of(m.group("pct"), m.group("num"), m.group("den"));
	    } catch (Exception ignore) {
	        return null;
	    }
	}

	/**
	 * 3分割データ情報の有無
	 * @param fieldName
	 * @return
	 */
	protected boolean isTriSplitFieldName(String fieldName) {
		if (fieldName == null)
			return false;
		String n = fieldName.toLowerCase();
		return n.contains("passcount")
				|| n.contains("longPasscount")
				|| n.contains("finalthirdpasscount")
				|| n.contains("crosscount")
				|| n.contains("tacklecount");
		// 必要に応じて追加
	}

	/**
	 * 3分割データの安全性によるメソッド
	 * @param valueStr
	 * @return
	 */
	protected Triple<String, String, String> split3Safe(String valueStr) {
		if (valueStr == null || valueStr.isBlank())
			return Triple.of("", "", "");
		Triple<String, String, String> t = splitPercentageWithFraction(valueStr);
		if (t != null)
			return t;
		// "93%" のように%だけのケースも3要素で返す
		return Triple.of(valueStr.trim(), "", "");
	}

}
