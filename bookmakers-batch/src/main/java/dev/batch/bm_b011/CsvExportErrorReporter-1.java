package dev.batch.bm_b011;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.batch.error.AnalyzeErrorRecorder;
import dev.batch.error.AnalyzeErrorWriter;
import dev.common.error.AnalyzeErrorInfo;
import dev.common.error.AnalyzeErrorMatchEntity;
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
 * <h2>自動解決（2026-10-21 見直し）</h2>
 * <p>
 * 実行の最後に {@link #reconcile} で、未対応のエラーを 1 行ずつ「原因が解消したか」突き合わせ、解消した行を
 * 対応済み（resolved_by = AUTO、メモが空なら理由を入れる）にする。CSV を作ったその実行だけでなく、
 * 前の実行で作った CSV・後から直ったシーズンでも解決する。
 * </p>
 * <table border="1">
 *   <tr><th>error_type</th><th>解決とみなす条件</th></tr>
 *   <tr><td>INVALID_CATEGORY / ROUND_NOT_FOUND（CSV のフォルダが決まらない）</td>
 *       <td>その試合（match_id）の CSV がある（仮のフォルダ XXX-YYY の CSV は数えない）</td></tr>
 *   <tr><td>CSV_CREATE_FAILED</td><td>その試合の CSV がある（古い行は detail の csv= のファイルがある）</td></tr>
 *   <tr><td>SEASON_NOT_FOUND / SEASON_RESOLVE_FAILED / INVALID_CATEGORY（csv_detail_manage のシーズン）</td>
 *       <td>その CSV の csv_detail_manage にシーズンが入った（この実行で補えたものを含む）、または CSV が無くなった</td></tr>
 *   <tr><td>CSV_DETAIL_REGISTER_FAILED</td><td>その CSV が csv_detail_manage に登録された、または CSV が無くなった</td></tr>
 *   <tr><td>MISSING_VALUE（チーム名が空）</td><td>自動では解決しない（CSV は作り直さないので、画面で対応済みにする）</td></tr>
 *   <tr><td>試合単位のエラー（INVALID_CATEGORY / ROUND_NOT_FOUND / CSV_CREATE_FAILED / MISSING_VALUE）</td>
 *       <td>その試合（match_id）が今回の B011 の対象に無い＝試合のデータが無くなった（延期で退避・削除など）。
 *       対象試合が 0 件のとき（取得の不具合の可能性）はこの判定をしない</td></tr>
 * </table>
 * <p>
 * 試合の特定は match_id（seq_key の「-連番」より前）。match_id が入っていない古い行は detail の「seqKey=…」から取る。
 * ホーム・アウェーだけで突き合わせると前のシーズンの同じ対戦で解決してしまうため、ホーム・アウェーでは解決しない。
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

	/** 古い行の detail から seq_key を取る（「seqKey=U1nXPu5e-1〜…」） */
	private static final Pattern DETAIL_SEQ_KEY = Pattern.compile("seqKey=([^\\s,〜(]+)");

	/** detail から CSV のキーを取る（「…, csv=国-リーグ-ラウンドN/ホーム-アウェー.csv」。末尾まで） */
	private static final Pattern DETAIL_CSV = Pattern.compile("csv=(.+)$");

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
	 * 実行の最後に呼ぶ: 未対応エラーのうち原因が解消したものを対応済みにする（失敗しても例外は投げない）。
	 *
	 * @param matchIdsWithCsv CSV がある試合の match_id（仮のフォルダの CSV は除いておくこと）
	 * @param currentMatchIds 今回の B011 の対象試合の match_id（ここに無い試合はデータが無くなった試合）
	 * @param existingCsvIds 今ある CSV のキー（正規化済み。この実行で作ったものを含む）
	 * @param registeredCsvIds csv_detail_manage に登録されている CSV のキー（正規化済み）
	 * @param csvIdsWithSeason csv_detail_manage にシーズンが入っている CSV のキー（正規化済み）
	 * @param csvKeyNormalizer detail の csv= を上と同じ形にそろえる関数
	 * @return 対応済みにした件数
	 */
	public int reconcile(Set<String> matchIdsWithCsv, Set<String> currentMatchIds, Set<String> existingCsvIds,
			Set<String> registeredCsvIds, Set<String> csvIdsWithSeason,
			java.util.function.Function<String, String> csvKeyNormalizer) {
		List<AnalyzeErrorMatchEntity> rows = this.analyzeErrorRecorder.findUnresolvedRows(BM_NUMBER);
		if (rows.isEmpty()) {
			return 0;
		}
		// 理由ごとにまとめて更新する
		Map<String, List<String>> seqsByReason = new LinkedHashMap<>();
		for (AnalyzeErrorMatchEntity r : rows) {
			String reason = resolvedReason(r, matchIdsWithCsv, currentMatchIds, existingCsvIds, registeredCsvIds,
					csvIdsWithSeason, csvKeyNormalizer);
			if (reason != null) {
				seqsByReason.computeIfAbsent(reason, k -> new ArrayList<>()).add(r.getSeq());
			}
		}
		int total = 0;
		for (Map.Entry<String, List<String>> e : seqsByReason.entrySet()) {
			total += this.analyzeErrorRecorder.resolveBySeqs(BM_NUMBER, e.getValue(), e.getKey());
		}
		return total;
	}

	/**
	 * 1 行の原因が解消したか（解消していればメモに入れる理由、していなければ null）。
	 */
	static String resolvedReason(AnalyzeErrorMatchEntity r, Set<String> matchIdsWithCsv,
			Set<String> currentMatchIds, Set<String> existingCsvIds,
			Set<String> registeredCsvIds, Set<String> csvIdsWithSeason,
			java.util.function.Function<String, String> csvKeyNormalizer) {
		AnalyzeErrorType type;
		try {
			type = AnalyzeErrorType.valueOf(r.getErrorType());
		} catch (RuntimeException e) {
			return null;
		}
		String detail = r.getDetail() == null ? "" : r.getDetail();
		boolean seasonRow = detail.startsWith("csv_detail_manage の season");
		String csvId = csvIdOf(detail, csvKeyNormalizer);
		String matchId = matchIdOf(r);

		// 試合単位のエラーで、その試合が今回の対象に無い（延期で退避・削除などでデータが無くなった）
		boolean matchLevel = (type == AnalyzeErrorType.INVALID_CATEGORY && !seasonRow)
				|| type == AnalyzeErrorType.ROUND_NOT_FOUND
				|| type == AnalyzeErrorType.CSV_CREATE_FAILED
				|| type == AnalyzeErrorType.MISSING_VALUE;
		if (matchLevel && matchId != null && currentMatchIds != null && !currentMatchIds.isEmpty()
				&& !currentMatchIds.contains(matchId) && !matchIdsWithCsv.contains(matchId)) {
			return "自動解決: 試合のデータが無くなったため（延期で退避・削除など）";
		}

		switch (type) {
		case INVALID_CATEGORY:
			if (seasonRow) {
				return seasonReason(csvId, existingCsvIds, csvIdsWithSeason);
			}
			return matchId != null && matchIdsWithCsv.contains(matchId) ? "自動解決: 試合の CSV を作成済み" : null;
		case ROUND_NOT_FOUND:
			return matchId != null && matchIdsWithCsv.contains(matchId) ? "自動解決: 試合の CSV を作成済み" : null;
		case CSV_CREATE_FAILED:
			if (matchId != null && matchIdsWithCsv.contains(matchId)) {
				return "自動解決: 試合の CSV を作成済み";
			}
			return csvId != null && existingCsvIds.contains(csvId) ? "自動解決: CSV を作成済み" : null;
		case SEASON_NOT_FOUND:
		case SEASON_RESOLVE_FAILED:
			return seasonReason(csvId, existingCsvIds, csvIdsWithSeason);
		case CSV_DETAIL_REGISTER_FAILED:
			if (csvId == null) {
				return null;
			}
			if (registeredCsvIds.contains(csvId)) {
				return "自動解決: csv_detail_manage に登録済み";
			}
			return existingCsvIds.contains(csvId) ? null : "自動解決: CSV が無くなったため";
		default:
			// MISSING_VALUE など: 自動では解決しない
			return null;
		}
	}

	private static String seasonReason(String csvId, Set<String> existingCsvIds, Set<String> csvIdsWithSeason) {
		if (csvId == null) {
			return null;
		}
		if (csvIdsWithSeason.contains(csvId)) {
			return "自動解決: csv_detail_manage にシーズンが入った";
		}
		return existingCsvIds.contains(csvId) ? null : "自動解決: CSV が無くなったため";
	}

	/** 行の match_id（無ければ detail の seqKey= から。取れなければ null） */
	static String matchIdOf(AnalyzeErrorMatchEntity r) {
		if (!isBlank(r.getMatchId())) {
			return r.getMatchId().trim();
		}
		String detail = r.getDetail();
		if (isBlank(detail)) {
			return null;
		}
		Matcher m = DETAIL_SEQ_KEY.matcher(detail);
		return m.find() ? matchIdOfSeqKey(m.group(1)) : null;
	}

	/** detail の csv= の CSV キー（正規化済み。無ければ null） */
	private static String csvIdOf(String detail, java.util.function.Function<String, String> normalizer) {
		if (isBlank(detail)) {
			return null;
		}
		Matcher m = DETAIL_CSV.matcher(detail);
		if (!m.find()) {
			return null;
		}
		String v = normalizer == null ? m.group(1).trim() : normalizer.apply(m.group(1).trim());
		return isBlank(v) ? null : v;
	}

	/** seq_key（「U1nXPu5e-12」）の match_id（「U1nXPu5e」）。「-連番」が無ければそのまま */
	static String matchIdOfSeqKey(String seqKey) {
		if (isBlank(seqKey)) {
			return null;
		}
		String s = seqKey.trim();
		int i = s.lastIndexOf('-');
		if (i > 0 && s.substring(i + 1).chars().allMatch(Character::isDigit) && i + 1 < s.length()) {
			return s.substring(0, i);
		}
		return s;
	}

	/** seq_key の一覧の match_id（先頭の seq_key から。無ければ null） */
	static String matchIdOfSeqs(List<String> seqIds) {
		return seqIds == null || seqIds.isEmpty() ? null : matchIdOfSeqKey(seqIds.get(0));
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
				.matchId(matchIdOfSeqs(seqIds))
				.detail(join("CSV のフォルダを決められないため未作成", seqDetail(seqIds)))
				.field("dataCategory", category);
		record(type, category, i, null, null);
	}

	/**
	 * チーム名が空（CSV は unknown-home / unknown-away の名前で作る）。
	 */
	public void teamMissing(String category, String home, String away, List<String> seqIds) {
		AnalyzeErrorInfo i = info(category, home, away).matchId(matchIdOfSeqs(seqIds)).detail(seqDetail(seqIds));
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
	 * @param seqIds 対象の seqKey（match_id を取る。null 可）
	 * @param cause 例外
	 */
	public void csvFailed(String step, String relativeKey, String category, String home, String away,
			List<String> seqIds, Throwable cause) {
		AnalyzeErrorInfo i = info(category, home, away).matchId(matchIdOfSeqs(seqIds))
				.detail("step=" + step + ", csv=" + relativeKey);
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
	 * @param matchId 試合の match_id（null 可）
	 * @param cause 例外（あれば）
	 */
	public void seasonUnresolved(AnalyzeErrorType type, String category, String country, String league,
			String home, String away, String csvId, String matchId, Throwable cause) {
		AnalyzeErrorInfo i = info(category, home, away).matchId(matchId)
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
			String matchId, Throwable cause) {
		AnalyzeErrorInfo i = info(category, home, away).matchId(matchId).detail("csv=" + csvId);
		record(AnalyzeErrorType.CSV_DETAIL_REGISTER_FAILED, causeMessage(cause), i,
				isBlank(season) ? null : season, cause);
	}

	/**
	 * その試合の CSV が作れて、シーズン付きで登録できた（未対応エラーがあれば対応済みにする）。
	 * @deprecated ホーム・アウェーだけで解決するため前のシーズンの同じ対戦も解決してしまう。{@link #reconcile} を使う
	 *             （2026-10-21 から ExportCsvService は呼ばない）。
	 */
	@Deprecated
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