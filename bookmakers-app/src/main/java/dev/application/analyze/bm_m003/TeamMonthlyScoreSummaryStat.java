package dev.application.analyze.bm_m003;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.application.analyze.interf.AnalyzeEntityIF;
import dev.common.constant.BookMakersCommonConst;
import dev.common.constant.MessageCdConst;
import dev.common.entity.BookDataEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.util.ExecuteMainUtil;

/**
 * BM_M003統計分析ロジック
 *
 * <h2>何を導出するクラスか</h2>
 * <p>
 * チームごと・ホーム/アウェー別に、各年各月の「得点数」の累計を集計し、
 * team_monthly_score_summary テーブルに加算保存する。
 * 例: 「チームA のホーム戦での 2026年9月の得点数 = 12」。
 * 月ごとの得点力の推移（季節による調子の波など）を見るためのデータ。
 * </p>
 *
 * <h3>集計方法（修正後）</h3>
 * <ol>
 *   <li>試合ごとにスナップショット行を通番の数値順に並べる。</li>
 *   <li>最終行（最大通番）の試合時間が FIN（試合終了）の試合だけを対象にする。
 *       途中経過の試合は数えない（次回以降、試合終了後のデータが届いた時点で数える）。</li>
 *   <li>最終行のスコアをそのまま得点数とする（ホーム得点→ホームチームの "H"、アウェー得点→アウェーチームの "A"）。
 *       ゴール取り消しは最終スコアに反映済みのため、取り消された得点は数えない。</li>
 *   <li>月は試合の最初の行の記録時間（recordTime の先頭 yyyy-MM）で決める。</li>
 *   <li>「国・リーグ・チーム・H/A・年」単位にまとめ、1回のトランザクションでまとめて加算する。</li>
 * </ol>
 *
 * <h2>修正履歴（データが継続的に流れ込む前提での修正）</h2>
 * <ul>
 *   <li><b>【重要】途中経過の試合で二重計上していた</b>: 以前は「直前の行との差分」で得点を数え、
 *       直前スコアを毎回 0 から始めていた。同じ試合の行が複数回に分かれて届くと、
 *       2回目の実行で前半の得点をもう一度数えていた。試合終了（FIN）の試合を最終スコアで1回だけ数える方式に変更。</li>
 *   <li><b>【重要】ゴール取り消しで得点が残っていた</b>: 取り消し行を飛ばすだけで直前スコアを更新しなかったため、
 *       取り消された得点が残ったり、スコアが戻った後の再得点を二重に数えたりしていた。最終スコア方式で解消。</li>
 *   <li><b>並び順</b>: 行が通番順に並んでいる前提だったのを、数値順にソートするよう変更。</li>
 *   <li><b>チーム名にハイフンを含むと誤判定</b>: マップのキー（ファイル名-ホーム-アウェー）を "-" で分割して
 *       チーム名を取り出していたため、ホームチーム名に "-" があると別チームとして数えていた。
 *       行データのチーム名（homeTeamName / awayTeamName）を使うよう変更。</li>
 *   <li><b>記録時間の形式不正で処理全体が落ちる</b>: 月の数値変換を事前に検証し、不正な試合だけスキップするよう変更。</li>
 *   <li><b>DB アクセス回数と途中失敗</b>: 以前は「チーム×月」ごとに SELECT+UPDATE を別トランザクションで実行しており、
 *       途中で失敗すると一部だけ加算された状態で残り、再実行で二重計上になった。
 *       「チーム×H/A×年」単位に12か月分をまとめ、全件を1トランザクションで保存するよう変更
 *       （DB アクセスは最大 1/12、失敗時は全件ロールバック）。</li>
 *   <li><b>ログ量</b>: 全行でログを出していたのを、試合単位・件数サマリのみに削減。</li>
 * </ul>
 *
 * <h2>懸念点・エラーが起こりそうな箇所</h2>
 * <ul>
 *   <li><b>【重要】同じ試合が再度流れてくると二重計上</b>: FIN の試合を毎回数えるため、
 *       同じ試合（FIN 済み）が次の実行にも含まれると、再度加算される。
 *       処理済み試合の記録（例: logicFlg、処理済みテーブル）が必要。</li>
 *   <li><b>月の決め方</b>: recordTime（データ取得時刻）で月を決める。月末深夜の試合は、
 *       取得時刻のタイムゾーンによって翌月に入る可能性がある。recordTime は "yyyy-MM..." 形式が前提。</li>
 *   <li><b>最終スコアが空・数値でない試合</b>: スキップし、ログを出す。</li>
 *   <li><b>同時実行</b>: Writer 側で行ロック順を固定しているが、DB に一意制約がないと初回 INSERT が重複しうる
 *       （{@link TeamMonthlyScoreSummaryWriter} 参照）。</li>
 * </ul>
 *
 * @author shiraishitoshio
 */
@Component
public class TeamMonthlyScoreSummaryStat implements AnalyzeEntityIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = TeamMonthlyScoreSummaryStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = TeamMonthlyScoreSummaryStat.class.getName();

	/** 実行モード */
	private static final String EXEC_MODE = "BM_M003_TEAM_MONTHLY_SCORE";

	/** ホーム */
	private static final String HOME = "H";

	/** アウェー */
	private static final String AWAY = "A";

	@Autowired
	private TeamMonthlyScoreSummaryWriter teamMonthlyScoreSummaryWriter;

	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * 試合終了済みの試合から、チーム×H/A×年月の得点数を集計して保存する。
	 *
	 * <p>懸念点: FIN 済みの同じ試合が再度入力されると二重計上になる（処理済み管理が必要）。</p>
	 *
	 * @param entities 国,リーグ → 試合キー → スナップショット行 のマップ（null 可）
	 */
	@Override
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public void calcStat(Map<String, Map<String, List<BookDataEntity>>> entities) {
		final String METHOD_NAME = "calcStat";

		this.manageLoggerComponent.init(EXEC_MODE, null);
		this.manageLoggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		int matchCount = 0;
		int finCount = 0;
		int skipCount = 0;

		try {
			if (entities == null || entities.isEmpty()) {
				return;
			}

			// キー順を固定（TreeMap）: Writer でのロック順を一定にしてデッドロックを防ぐ
			Map<TeamYearKey, int[]> aggregate = new TreeMap<>();

			for (Map.Entry<String, Map<String, List<BookDataEntity>>> outerEntry : entities.entrySet()) {
				String countryLeague = outerEntry.getKey();
				Map<String, List<BookDataEntity>> matchMap = outerEntry.getValue();
				if (matchMap == null || matchMap.isEmpty()) {
					continue;
				}

				String[] split = ExecuteMainUtil.splitLeagueInfo(countryLeague);
				if (split == null || split.length < 2 || isBlank(split[0]) || isBlank(split[1])) {
					debugLog(METHOD_NAME, "skip: invalid countryLeague=" + countryLeague);
					continue;
				}
				String country = split[0].trim();
				String league = split[1].trim();

				for (Map.Entry<String, List<BookDataEntity>> matchEntry : matchMap.entrySet()) {
					matchCount++;
					String matchKey = matchEntry.getKey();

					MatchResult result = toMatchResult(matchEntry.getValue());
					if (result == null) {
						// 途中経過・データ不備（理由は toMatchResult 内で判定）
						skipCount++;
						continue;
					}
					if (result.invalidReason != null) {
						skipCount++;
						debugLog(METHOD_NAME, "skip: " + result.invalidReason
								+ ", matchKey=" + matchKey + ", countryLeague=" + countryLeague);
						continue;
					}
					finCount++;

					if (result.homeGoals > 0) {
						add(aggregate, new TeamYearKey(country, league, result.homeTeam, HOME, result.year),
								result.monthIndex, result.homeGoals);
					}
					if (result.awayGoals > 0) {
						add(aggregate, new TeamYearKey(country, league, result.awayTeam, AWAY, result.year),
								result.monthIndex, result.awayGoals);
					}
				}
			}

			debugLog(METHOD_NAME, "aggregate done. matchCount=" + matchCount + ", finCount=" + finCount
					+ ", skipCount=" + skipCount + ", rowCount=" + aggregate.size());

			if (!aggregate.isEmpty()) {
				// 全行を1トランザクションで加算（途中失敗時は全件ロールバック）
				this.teamMonthlyScoreSummaryWriter.addMonthlyGoalsAll(aggregate);
			}

		} finally {
			this.manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);
			this.manageLoggerComponent.clear();
		}
	}

	/**
	 * 1試合分の行から、集計に使う情報（チーム名・最終スコア・年月）を取り出す。
	 *
	 * @param rows 1試合分のスナップショット行
	 * @return 途中経過（FIN でない）・行なしの場合は null、
	 *         データ不備の場合は invalidReason を設定した結果、正常なら集計用の結果
	 */
	private MatchResult toMatchResult(List<BookDataEntity> rows) {
		if (rows == null || rows.isEmpty()) {
			return null;
		}

		// 通番の数値順に並べる（null 行は除外）
		List<BookDataEntity> sorted = new ArrayList<>(rows.size());
		for (BookDataEntity e : rows) {
			if (e != null) {
				sorted.add(e);
			}
		}
		if (sorted.isEmpty()) {
			return null;
		}
		sorted.sort(Comparator.comparingLong(e -> seqToLong(e.getSeq())));

		BookDataEntity last = sorted.get(sorted.size() - 1);
		if (!BookMakersCommonConst.FIN.equals(last.getTime())) {
			// 試合途中: 終了後のデータが届いたときに数える
			return null;
		}

		MatchResult r = new MatchResult();
		r.homeTeam = trimOrNull(last.getHomeTeamName());
		r.awayTeam = trimOrNull(last.getAwayTeamName());
		if (r.homeTeam == null || r.awayTeam == null) {
			r.invalidReason = "blank team name";
			return r;
		}

		Integer home = toIntOrNull(last.getHomeScore());
		Integer away = toIntOrNull(last.getAwayScore());
		if (home == null || away == null || home < 0 || away < 0) {
			r.invalidReason = "invalid final score (" + last.getHomeScore() + "-" + last.getAwayScore() + ")";
			return r;
		}
		r.homeGoals = home;
		r.awayGoals = away;

		// 試合日の年月: 最初に有効な recordTime を持つ行（キックオフに近い行）から取る
		String yearMonth = null;
		for (BookDataEntity e : sorted) {
			yearMonth = toYearMonth(e.getRecordTime());
			if (yearMonth != null) {
				break;
			}
		}
		if (yearMonth == null) {
			r.invalidReason = "invalid recordTime";
			return r;
		}
		r.year = yearMonth.substring(0, 4);
		r.monthIndex = Integer.parseInt(yearMonth.substring(5, 7)) - 1;
		return r;
	}

	/**
	 * 記録時間から "yyyy-MM" を取り出す（形式不正は null）。
	 * "yyyy-MM..." と "yyyy/MM..." を許容する。
	 */
	private static String toYearMonth(String recordTime) {
		if (recordTime == null) {
			return null;
		}
		String s = recordTime.trim();
		if (s.length() < 7) {
			return null;
		}
		String y = s.substring(0, 4);
		char sep = s.charAt(4);
		String m = s.substring(5, 7);
		if ((sep != '-' && sep != '/') || !y.chars().allMatch(Character::isDigit)
				|| !m.chars().allMatch(Character::isDigit)) {
			return null;
		}
		int month = Integer.parseInt(m);
		if (month < 1 || month > 12) {
			return null;
		}
		return y + "-" + m;
	}

	/** 集計マップに月別得点を加算する */
	private static void add(Map<TeamYearKey, int[]> aggregate, TeamYearKey key, int monthIndex, int goals) {
		int[] months = aggregate.computeIfAbsent(key, k -> new int[TeamMonthlyScoreSummaryEntity.MONTH_COUNT]);
		months[monthIndex] += goals;
	}

	private void debugLog(String methodName, String message) {
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, methodName, MessageCdConst.MCD00099I_LOG, message);
	}

	private static long seqToLong(String seq) {
		if (seq == null || seq.isBlank()) {
			return Long.MAX_VALUE;
		}
		try {
			return Long.parseLong(seq.trim());
		} catch (NumberFormatException e) {
			return Long.MAX_VALUE;
		}
	}

	private static Integer toIntOrNull(String s) {
		if (s == null || s.isBlank()) {
			return null;
		}
		try {
			return Integer.parseInt(s.trim());
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static String trimOrNull(String s) {
		return (s == null || s.isBlank()) ? null : s.trim();
	}

	private static boolean isBlank(String value) {
		return value == null || value.trim().isEmpty();
	}

	/** 1試合分の集計用情報 */
	private static final class MatchResult {
		String homeTeam;
		String awayTeam;
		int homeGoals;
		int awayGoals;
		String year;
		int monthIndex;
		/** データ不備の理由（正常時は null） */
		String invalidReason;
	}

	/**
	 * 保存単位のキー（国・リーグ・チーム・H/A・年）。
	 * <p>
	 * Comparable にしてキー順を固定し、Writer での行ロック順を一定にする。
	 * Map のキーとして使うため、equals / hashCode を実装している（値が同じなら同じキー）。
	 * 各項目が null でも比較で落ちないよう、null は先頭扱いにしている。
	 * </p>
	 */
	public static final class TeamYearKey implements Comparable<TeamYearKey> {

		private static final Comparator<String> NULL_SAFE =
				Comparator.nullsFirst(Comparator.naturalOrder());

		private static final Comparator<TeamYearKey> ORDER = Comparator
				.comparing(TeamYearKey::getCountry, NULL_SAFE)
				.thenComparing(TeamYearKey::getLeague, NULL_SAFE)
				.thenComparing(TeamYearKey::getTeam, NULL_SAFE)
				.thenComparing(TeamYearKey::getHa, NULL_SAFE)
				.thenComparing(TeamYearKey::getYear, NULL_SAFE);

		/** 国 */
		private final String country;

		/** リーグ */
		private final String league;

		/** チーム名 */
		private final String team;

		/** ホーム/アウェー（"H" / "A"） */
		private final String ha;

		/** 年（yyyy） */
		private final String year;

		public TeamYearKey(String country, String league, String team, String ha, String year) {
			this.country = country;
			this.league = league;
			this.team = team;
			this.ha = ha;
			this.year = year;
		}

		public String getCountry() {
			return country;
		}

		public String getLeague() {
			return league;
		}

		public String getTeam() {
			return team;
		}

		public String getHa() {
			return ha;
		}

		public String getYear() {
			return year;
		}

		@Override
		public int compareTo(TeamYearKey o) {
			return ORDER.compare(this, o);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (!(obj instanceof TeamYearKey)) {
				return false;
			}
			TeamYearKey other = (TeamYearKey) obj;
			return Objects.equals(country, other.country)
					&& Objects.equals(league, other.league)
					&& Objects.equals(team, other.team)
					&& Objects.equals(ha, other.ha)
					&& Objects.equals(year, other.year);
		}

		@Override
		public int hashCode() {
			return Objects.hash(country, league, team, ha, year);
		}

		@Override
		public String toString() {
			return "TeamYearKey[country=" + country + ", league=" + league + ", team=" + team
					+ ", ha=" + ha + ", year=" + year + "]";
		}
	}
}
