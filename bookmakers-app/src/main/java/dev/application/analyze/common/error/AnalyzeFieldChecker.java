package dev.application.analyze.common.error;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.common.entity.BookDataEntity;
import dev.common.util.CountryLeagueParser;

/**
 * 試合データの項目を確認し、使えない項目があれば「どの項目が・どんな値で」使えなかったかを analyze_error_match に記録する（全 BM 共通）。
 *
 * <h2>何をするクラスか</h2>
 * <p>
 * 各 Stat は、試合終了行のチーム名・スコアが取れない試合を「スキップしてログに出す」だけだった。
 * ログは流れて消えるので、このクラスで項目ごとに記録し、画面でエラーの原因（項目名と値）を確認できるようにする。
 * </p>
 * <ul>
 *   <li>空の項目 → {@link AnalyzeErrorType#MISSING_VALUE}（error_field に項目名）</li>
 *   <li>読めない値（スコアが数字でない等） → {@link AnalyzeErrorType#INVALID_VALUE}（error_value に「項目名=値」）</li>
 *   <li>両方ある場合は種別ごとに1行ずつ記録する。</li>
 *   <li>同じ BM・試合・項目のエラーは1行にまとめ、発生回数を +1 する（ストリーミングで何度流れても行は増えない）。</li>
 *   <li>記録に失敗しても例外は投げない（{@link AnalyzeErrorRecorder}）。</li>
 * </ul>
 *
 * <h2>使い方（Stat）</h2>
 * <pre>
 * if (home == null || away == null || parseScore(end.getHomeScore()) == null ...) {
 *     invalidCount++;
 *     this.analyzeFieldChecker.checkTeamsAndScore(BM_NUMBER, outerEntry.getKey(), end);
 *     continue;
 * }
 * </pre>
 *
 * <h2>懸念点</h2>
 * <ul>
 *   <li><b>自動解決</b>: 後で同じ試合が正常に登録できたら、シーズンのエラーと同じく対応済み（AUTO）になる。
 *       ただしチーム名が空で記録された行は、試合を特定できないので自動では対応済みにならない（画面で対応）。</li>
 *   <li>スコアの判定（0 以上の整数）は各 Stat の parseScore と同じ基準にしている。基準を変えるときは両方直すこと。</li>
 *   <li>キーが「国: リーグ - ラウンドN」形式でない試合（カップ戦など）は、仕様として対象外なので記録しない（Stat 側でスキップ）。</li>
 * </ul>
 */
@Component
public class AnalyzeFieldChecker {

	@Autowired
	private AnalyzeErrorRecorder analyzeErrorRecorder;

	/**
	 * 試合の最後の行のチーム名・スコア（homeTeamName / awayTeamName / homeScore / awayScore）を確認し、
	 * 使えない項目があれば記録する。
	 *
	 * @param bmNumber BM 番号
	 * @param dataCategory 元のキー（「国: リーグ - ラウンドN」）
	 * @param row 試合の最後の行（試合終了行）
	 * @return すべて使える場合 true
	 */
	public boolean checkTeamsAndScore(String bmNumber, String dataCategory, BookDataEntity row) {
		if (row == null) {
			return false;
		}
		List<String[]> missing = new ArrayList<>();
		List<String[]> invalid = new ArrayList<>();
		checkText("homeTeamName", row.getHomeTeamName(), missing);
		checkText("awayTeamName", row.getAwayTeamName(), missing);
		checkScore("homeScore", row.getHomeScore(), missing, invalid);
		checkScore("awayScore", row.getAwayScore(), missing, invalid);
		if (missing.isEmpty() && invalid.isEmpty()) {
			return true;
		}
		if (!missing.isEmpty()) {
			this.analyzeErrorRecorder.recordField(bmNumber, AnalyzeErrorType.MISSING_VALUE, info(dataCategory, row, missing));
		}
		if (!invalid.isEmpty()) {
			this.analyzeErrorRecorder.recordField(bmNumber, AnalyzeErrorType.INVALID_VALUE, info(dataCategory, row, invalid));
		}
		return false;
	}

	/**
	 * 試合の行の中で通番が最大の行（試合の最後の行）を確認する（どの行を使ったか Stat で分からない場合用）。
	 *
	 * @param bmNumber BM 番号
	 * @param dataCategory 元のキー
	 * @param rows 1試合分の行
	 * @return すべて使える場合 true
	 */
	public boolean checkTeamsAndScore(String bmNumber, String dataCategory, List<BookDataEntity> rows) {
		return checkTeamsAndScore(bmNumber, dataCategory, lastRow(rows));
	}

	/**
	 * 任意の項目が空でないか確認する（空なら MISSING_VALUE として記録）。
	 *
	 * @param bmNumber BM 番号
	 * @param info 試合の情報
	 * @param fieldName 項目名
	 * @param value 値
	 * @return 空でなければ true
	 */
	public boolean requireText(String bmNumber, AnalyzeErrorInfo info, String fieldName, String value) {
		if (value != null && !value.isBlank()) {
			return true;
		}
		AnalyzeErrorInfo i = (info == null ? AnalyzeErrorInfo.empty() : info).field(fieldName, value);
		this.analyzeErrorRecorder.recordField(bmNumber, AnalyzeErrorType.MISSING_VALUE, i);
		return false;
	}

	private static AnalyzeErrorInfo info(String dataCategory, BookDataEntity row, List<String[]> fields) {
		AnalyzeErrorInfo i = AnalyzeErrorInfo.match(dataCategory, trimOrNull(row.getHomeTeamName()),
				trimOrNull(row.getAwayTeamName()))
				.matchId(trimOrNull(row.getMatchId()))
				.detail("seq=" + row.getSeq() + ", time=" + row.getTime());
		String[] cl = CountryLeagueParser.parse(dataCategory);
		if (cl != null && cl.length >= 2) {
			i.countryLeague(cl[0], cl[1]);
		}
		for (String[] f : fields) {
			i.field(f[0], f[1]);
		}
		return i;
	}

	private static void checkText(String name, String value, List<String[]> missing) {
		if (value == null || value.isBlank()) {
			missing.add(new String[] { name, value });
		}
	}

	/** スコア: 空 → missing、0 以上の整数でない → invalid（全角数字は半角として読む） */
	private static void checkScore(String name, String value, List<String[]> missing, List<String[]> invalid) {
		if (value == null || value.isBlank()) {
			missing.add(new String[] { name, value });
			return;
		}
		String s = java.text.Normalizer.normalize(value.trim(), java.text.Normalizer.Form.NFKC);
		if (!s.matches("\\d+")) {
			invalid.add(new String[] { name, value });
		}
	}

	/** 通番（数値）が最大の行。通番が読めない行しか無ければリストの最後 */
	private static BookDataEntity lastRow(List<BookDataEntity> rows) {
		if (rows == null || rows.isEmpty()) {
			return null;
		}
		BookDataEntity best = null;
		long bestSeq = Long.MIN_VALUE;
		for (BookDataEntity e : rows) {
			if (e == null) {
				continue;
			}
			long seq;
			try {
				seq = Long.parseLong(e.getSeq() == null ? "" : e.getSeq().trim());
			} catch (NumberFormatException ex) {
				seq = Long.MIN_VALUE;
			}
			if (best == null || seq >= bestSeq) {
				best = e;
				bestSeq = seq;
			}
		}
		return best;
	}

	private static String trimOrNull(String s) {
		return (s == null || s.isBlank()) ? null : s.trim();
	}
}
