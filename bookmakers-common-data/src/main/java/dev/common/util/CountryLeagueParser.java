package dev.common.util;

import java.util.regex.Pattern;


/**
 * calcStat に渡る Map の外側キー（BookDataEntity.gameTeamCategory）から国・リーグを取り出す共通処理。
 *
 * <h2>何をするクラスか</h2>
 * <p>
 * キーは GetStatInfo が CSV の gameTeamCategory をそのまま使っているため、
 * 「国: リーグ - ラウンドN」形式（例: "日本: J1 リーグ - ラウンド 5"）になる。
 * これを {@link ExecuteMainUtil#splitLeagueInfo} で分けて {国, リーグ} を返す。
 * </p>
 * <p>
 * 「国: リーグ - ラウンドN」の形式（コロンで国とリーグが分かれ、末尾が " - ラウンド N"）でないキーは
 * 無視する（null を返す）。旧形式「国,リーグ」やフォルダ名形式「Japan-J1-ラウンド5」、
 * カップ戦（"- 準決勝" など、ラウンド番号の無いもの）も無視する。
 * </p>
 * <p>
 * 【変更】ラウンド番号を {@link #parseRoundNo} で取り出せるようにした。同じ対戦がシーズン中に複数回あるリーグ
 * （スイス・スコットランドなど）で試合を区別するため、各テーブルの一意キーにラウンド番号を入れている。
 * 【変更】末尾が「ラウンド N」でないキーは {@link #parse} も null を返す（ラウンド番号の取れない行を作らない）。
 * </p>
 *
 * <h2>懸念点</h2>
 * <ul>
 *   <li><b>ラウンドはキーに含まれる</b>: 同じリーグでもラウンドごとに別の外側キーになる。
 *       国・リーグ単位で集計する場合は、このクラスで取り出した値でまとめること（キー文字列のままでは分かれる）。</li>
 *   <li><b>splitLeagueInfo はサブリーグ（"- アペルトゥラ" 等）を落とす</b>ため、サブリーグ違いは同じリーグになる。</li>
 *   <li>形式に合わない・取り出せない場合は null（呼び出し側でスキップすること）。</li>
 * </ul>
 */
public final class CountryLeagueParser {

	/** 対象とするキーの形式: 「国: リーグ - … ラウンド N」 */
	static final Pattern FORMAT = Pattern.compile("^[^:]+:\\s*\\S.*\\s-\\s*.*ラウンド\\s*\\d+\\s*$");

	/** 末尾のラウンド番号 */
	static final Pattern ROUND = Pattern.compile("ラウンド\\s*(\\d+)\\s*$");

	private CountryLeagueParser() {
	}

	/**
	 * 国・リーグを取り出す。
	 *
	 * @param category gameTeamCategory（Map の外側キー）
	 * @return {国, リーグ}（前後の空白除去済み）。形式に合わない・取り出せなければ null
	 */
	public static String[] parse(String category) {
		String normalized = normalize(category);
		if (normalized == null || !FORMAT.matcher(normalized).matches()) {
			return null;
		}
		String[] sp = ExecuteMainUtil.splitLeagueInfo(normalized);
		if (sp == null || sp.length < 3) {
			return null;
		}
		String country = trimOrNull(sp[0]);
		String league = trimOrNull(sp[1]);
		String round = trimOrNull(sp[2]);
		if (country == null || league == null || round == null) {
			return null;
		}
		return new String[] { country, league };
	}

	/**
	 * ラウンド番号を取り出す（「… - ラウンド 5」の 5）。
	 *
	 * @param category gameTeamCategory（Map の外側キー）
	 * @return ラウンド番号。形式に合わない・取り出せなければ null
	 */
	public static Integer parseRoundNo(String category) {
		String normalized = normalize(category);
		if (normalized == null || !FORMAT.matcher(normalized).matches()) {
			return null;
		}
		java.util.regex.Matcher m = ROUND.matcher(normalized);
		if (!m.find()) {
			return null;
		}
		try {
			return Integer.valueOf(m.group(1));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** 全角コロン・各種ダッシュを splitLeagueInfo と同じく正規化する（空なら null） */
	private static String normalize(String category) {
		if (category == null || category.isBlank()) {
			return null;
		}
		// NFKC で全角数字・全角コロンも半角にそろえる（「ラウンド５」→「ラウンド5」）
		return java.text.Normalizer.normalize(category.trim(), java.text.Normalizer.Form.NFKC)
				.replace('\uFF1A', ':')
				.replace('\u2010', '-').replace('\u2011', '-').replace('\u2013', '-')
				.replace('\u2014', '-').replace('\u2212', '-');
	}

	private static String trimOrNull(String s) {
		return (s == null || s.isBlank()) ? null : s.trim();
	}
}
