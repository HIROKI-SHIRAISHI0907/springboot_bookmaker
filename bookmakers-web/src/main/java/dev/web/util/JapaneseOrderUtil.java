package dev.web.util;

import java.text.Collator;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 国名・リーグ名・チーム名を「あいうえお順」に並べる（2026-11 追加）
 * <p>
 * DB の ORDER BY は文字コード順なので、カタカナ・ひらがな・漢字・英字が混ざると五十音にならない。
 * Java の日本語 Collator で並べる（ひらがな・カタカナ、全角・半角の違いは同じ扱い）。
 * 漢字の国名は読みが分からないので、よく出るものだけ読み（{@link #READING}）に置き換えて比べる。
 * 読みが無い漢字は、かなの後ろに並ぶ。
 * </p>
 * @author shiraishitoshio
 */
public final class JapaneseOrderUtil {

	/** 漢字を含む国名の読み（並べ替え専用） */
	private static final Map<String, String> READING = new LinkedHashMap<>();
	static {
		READING.put("日本", "にほん");
		READING.put("韓国", "かんこく");
		READING.put("中国", "ちゅうごく");
		READING.put("北朝鮮", "きたちょうせん");
		READING.put("台湾", "たいわん");
		READING.put("香港", "ほんこん");
		READING.put("南アフリカ", "みなみあふりか");
		READING.put("北アイルランド", "きたあいるらんど");
		READING.put("北マケドニア", "きたまけどにあ");
		READING.put("中央アフリカ", "ちゅうおうあふりか");
		READING.put("赤道ギニア", "せきどうぎにあ");
		READING.put("米国", "べいこく");
		READING.put("英国", "えいこく");
		READING.put("世界", "せかい");
	}

	private static final Collator COLLATOR;
	static {
		COLLATOR = Collator.getInstance(Locale.JAPANESE);
		// ひらがな・カタカナ、濁点の有無などの違いは、同じ文字のあとで比べる
		COLLATOR.setStrength(Collator.TERTIARY);
	}

	/** あいうえお順の Comparator（null は最後） */
	public static final Comparator<String> ORDER = (a, b) -> {
		if (a == null && b == null) {
			return 0;
		}
		if (a == null) {
			return 1;
		}
		if (b == null) {
			return -1;
		}
		return COLLATOR.compare(key(a), key(b));
	};

	private JapaneseOrderUtil() {
	}

	/** 並べ替え用の文字列（読みがある国名は読みに。前後の空白は除く） */
	private static String key(String s) {
		String v = s.trim();
		String reading = READING.get(v);
		return reading != null ? reading : v;
	}
}
