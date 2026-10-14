package dev.batch.bm_b011;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.batch.error.AnalyzeErrorRecorder;
import dev.batch.error.AnalyzeErrorWriter;
import dev.common.error.AnalyzeErrorInfo;
import dev.common.error.AnalyzeErrorType;
import dev.common.util.ExecuteMainUtil;

/**
 * B011（CSV 作成）で CSV を作れなかった試合を analyze_error_match に記録する。
 *
 * <h2>記録するもの（bm_number = BM_B011）</h2>
 * <table border="1">
 *   <tr><th>場面</th><th>error_type</th><th>error_field</th></tr>
 *   <tr><td>キーから国・リーグが読めない（新規 CSV を作れない）</td><td>INVALID_CATEGORY</td><td>dataCategory</td></tr>
 *   <tr><td>国・リーグは読めるがラウンド番号が無い（新規 CSV を作れない）</td><td>ROUND_NOT_FOUND</td><td>dataCategory</td></tr>
 *   <tr><td>チーム名が空（CSV は unknown-home 等の名前で作る）</td><td>MISSING_VALUE</td><td>homeTeamName / awayTeamName</td></tr>
 *   <tr><td>CSV 作成中の例外（作れなかった）</td><td>CSV_CREATE_FAILED</td><td>（なし）。detail に step と CSV キー</td></tr>
 *   <tr><td>csv_detail_manage のシーズンが取れない（CSV は作った）</td><td>INVALID_CATEGORY / SEASON_NOT_FOUND / SEASON_RESOLVE_FAILED</td><td>dataCategory / country,league</td></tr>
 *   <tr><td>csv_detail_manage の登録に失敗（バッチは止まる）</td><td>CSV_DETAIL_REGISTER_FAILED</td><td>（なし）</td></tr>
 * </table>
 *
 * <h2>エラー内容（error_message）</h2>
 * <p>
 * 文言はこのクラスで組み立てず、{@link AnalyzeErrorType#message(String)}（「ラベル: 補足」）にそろえる。
 * 補足には対象の値（カテゴリ・国・リーグ・例外のメッセージ）だけを入れる。
 * どの処理で起きたか（csv_detail_manage のシーズンなのか等）は detail に入れる。
 * </p>
 *
 * <h2>自動解決</h2>
 * <p>
 * その試合の CSV が作れて csv_detail_manage にシーズン付きで登録できたら、同じホーム・アウェーの未対応エラーを
 * 対応済み（AUTO）にする。国・リーグは見ない（キーが読めず国・リーグ空で記録したエラーも、カテゴリが直れば解決するため）。
 * 毎回 DB に行かないよう、処理の最初に {@link #begin()} で未対応のホーム・アウェーを読み込んでおく。
 * </p>
 *
 * <h2>懸念点</h2>
 * <ul>
 *   <li>CSV を作れない試合は毎回の実行で再評価されるので、直るまで実行のたびに発生回数が +1 される（行は増えない）。</li>
 *   <li>記録に失敗してもバッチは止めない（ログのみ）。</li>
 *   <li>{@link #begin()} はバッチの実行ごとに呼ぶこと（1つのバッチが同時に2本動かない前提）。</li>
 * </ul>
 */
@Component
public class CsvExportErrorReporter {

	/** BM 番号 */
	static final String BM_NUMBER = "BM_B011";

	@Autowired
	private AnalyzeErrorRecorder analyzeErrorRecorder;

	/** 未対応エラーのホーム・アウェー（{@link AnalyzeErrorWriter#teamKey} 形式） */
	private volatile Set<String> unresolvedTeams = Collections.emptySet();

	/**
	 * 実行の開始時に呼ぶ（未対応エラーのホーム・アウェーを読み込む）。
	 */
	public void begin() {
		this.unresolvedTeams = Collections.synchronizedSet(
				new HashSet<>(this.analyzeErrorRecorder.findUnresolvedTeamKeys(BM_NUMBER)));
	}

	/**
	 * キーから「国: リーグ - ラウンドN」のフォルダが決まらず、新規 CSV を作れなかった。
	 * 国・リーグが読めなければ INVALID_CATEGORY、読めてラウンド番号が無ければ ROUND_NOT_FOUND。
	 *
	 * @param category 判定に使ったカテゴリ（future_master の補完後）
	 * @param home ホーム
	 * @param away アウェー
	 * @param seqIds 対象の seqKey
	 */
	public void categoryUnresolved(String category, String home, String away, List<String> seqIds) {
		AnalyzeErrorType type = countryLeague(category) == null
				? AnalyzeErrorType.INVALID_CATEGORY
				: AnalyzeErrorType.ROUND_NOT_FOUND;
		AnalyzeErrorInfo i = info(category, home, away)
				.detail(join("CSV のフォルダを決められないため未作成", seqDetail(seqIds)))
				.field("dataCategory", category);
		record(type, category, i, null, null);
	}

	/**
	 * チーム名が空（CSV は unknown-home / unknown-away の名前で作る）。
	 */
	public void teamMissing(String category, String home, String away, List<String> seqIds) {
		AnalyzeErrorInfo i = info(category, home, away).detail(seqDetail(seqIds));
		if (isBlank(home)) {
			i.field("homeTeamName", home);
		}
		if (isBlank(away)) {
			i.field("awayTeamName", away);
		}
		// 補足は「項目名=値」（AnalyzeErrorRecorder#recordField と同じ形）
		this.analyzeErrorRecorder.recordField(BM_NUMBER, AnalyzeErrorType.MISSING_VALUE, i);
	}

	/**
	 * CSV 作成中に例外が起きて作れなかった。
	 *
	 * @param step 失敗した処理（fetchAndFilter / writeLocalCsv / putLocalFileToFinal など）
	 * @param relativeKey CSV の相対キー
	 * @param category カテゴリ（分かっていれば）
	 * @param home ホーム（分かっていれば）
	 * @param away アウェー（分かっていれば）
	 * @param cause 例外
	 */
	public void csvFailed(String step, String relativeKey, String category, String home, String away, Throwable cause) {
		AnalyzeErrorInfo i = info(category, home, away).detail("step=" + step + ", csv=" + relativeKey);
		record(AnalyzeErrorType.CSV_CREATE_FAILED, causeMessage(cause), i, null, cause);
	}

	/**
	 * csv_detail_manage に入れるシーズンが取れなかった（CSV は作成済み。season は空で登録される）。
	 *
	 * @param type INVALID_CATEGORY（国・リーグが分からない）/ SEASON_NOT_FOUND / SEASON_RESOLVE_FAILED
	 * @param category カテゴリ
	 * @param country 国（分かっていれば）
	 * @param league リーグ（分かっていれば）
	 * @param home ホーム
	 * @param away アウェー
	 * @param csvId CSV の相対キー
	 * @param cause 例外（あれば）
	 */
	public void seasonUnresolved(AnalyzeErrorType type, String category, String country, String league,
			String home, String away, String csvId, Throwable cause) {
		AnalyzeErrorInfo i = info(category, home, away)
				.detail("csv_detail_manage の season を空で登録, csv=" + csvId);
		if (!isBlank(country) || !isBlank(league)) {
			i.countryLeague(country, league);
		}
		String detail;
		if (type == AnalyzeErrorType.INVALID_CATEGORY) {
			i.field("dataCategory", category);
			detail = category;
		} else {
			i.field("country", country).field("league", league);
			detail = type == AnalyzeErrorType.SEASON_RESOLVE_FAILED
					? causeMessage(cause)
					: "country=" + country + ", league=" + league;
		}
		record(type, detail, i, null, cause);
	}

	/**
	 * csv_detail_manage の登録に失敗した（この後バッチは例外で止まる）。
	 */
	public void detailManageFailed(String csvId, String category, String season, String home, String away,
			Throwable cause) {
		AnalyzeErrorInfo i = info(category, home, away).detail("csv=" + csvId);
		record(AnalyzeErrorType.CSV_DETAIL_REGISTER_FAILED, causeMessage(cause), i,
				isBlank(season) ? null : season, cause);
	}

	/**
	 * その試合の CSV が作れて、シーズン付きで登録できた（未対応エラーがあれば対応済みにする）。
	 */
	public void resolved(String home, String away) {
		if (isBlank(home) || isBlank(away)) {
			return;
		}
		String key = AnalyzeErrorWriter.teamKey(home, away);
		if (!this.unresolvedTeams.contains(key)) {
			return;
		}
		this.analyzeErrorRecorder.resolveByTeams(BM_NUMBER, home, away);
		this.unresolvedTeams.remove(key);
	}

	/** 記録する（エラー内容は AnalyzeErrorType#message でそろえる） */
	private void record(AnalyzeErrorType type, String detail, AnalyzeErrorInfo info, String season, Throwable cause) {
		this.analyzeErrorRecorder.record(BM_NUMBER, type, type.message(detail), info, season, cause);
	}

	/** 試合の情報（国・リーグはカテゴリから読めれば入れる） */
	private static AnalyzeErrorInfo info(String category, String home, String away) {
		AnalyzeErrorInfo i = AnalyzeErrorInfo.match(trimOrNull(category), trimOrNull(home), trimOrNull(away));
		String[] cl = countryLeague(category);
		if (cl != null) {
			i.countryLeague(cl[0], cl[1]);
		}
		return i;
	}

	/** カテゴリから国・リーグ（読めなければ null。仮の値 XXX も国として返す） */
	static String[] countryLeague(String category) {
		if (isBlank(category)) {
			return null;
		}
		try {
			List<String> cl = ExecuteMainUtil.getCountryLeagueByRegex(category.trim());
			if (cl == null || cl.size() < 2 || isBlank(cl.get(0)) || isBlank(cl.get(1))) {
				return null;
			}
			return new String[] { cl.get(0).trim(), cl.get(1).trim() };
		} catch (Exception e) {
			return null;
		}
	}

	private static String causeMessage(Throwable cause) {
		if (cause == null) {
			return null;
		}
		String m = cause.getMessage();
		return isBlank(m) ? cause.getClass().getSimpleName() : m;
	}

	private static String seqDetail(List<String> seqIds) {
		if (seqIds == null || seqIds.isEmpty()) {
			return null;
		}
		return "seqKey=" + seqIds.get(0) + (seqIds.size() > 1 ? "〜" + seqIds.get(seqIds.size() - 1) : "")
				+ " (" + seqIds.size() + "件)";
	}

	private static String join(String a, String b) {
		if (isBlank(b)) {
			return a;
		}
		return a + ", " + b;
	}

	private static boolean isBlank(String s) {
		return s == null || s.trim().isEmpty();
	}

	private static String trimOrNull(String s) {
		return isBlank(s) ? null : s.trim();
	}
}