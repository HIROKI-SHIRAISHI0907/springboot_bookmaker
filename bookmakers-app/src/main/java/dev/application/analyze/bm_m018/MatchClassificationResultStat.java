package dev.application.analyze.bm_m018;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.application.analyze.bm_m018.ClassifyMode.FirstGoalBand;
import dev.application.analyze.bm_m018.ClassifyMode.NextGoal;
import dev.application.analyze.interf.AnalyzeEntityIF;
import dev.common.constant.BookMakersCommonConst;
import dev.common.constant.MessageCdConst;
import dev.common.entity.BookDataEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.util.CountryLeagueParser;
import dev.common.util.ExecuteMainUtil;

/**
 * BM_M019_BM_M020統計分析ロジック
 *
 * <h2>何を導出するクラスか</h2>
 * <p>
 * 試合終了した試合を「最初のゴールがいつ・どちらに入り、次のゴールが前半／後半／なしのどれか」で
 * 15パターン（{@link ClassifyMode}）に分類する。
 * </p>
 * <ul>
 *   <li><b>BM_M019</b>（classify_result_data）: 分類した試合の各時点のスナップショット（BookDataEntity の全項目）＋分類モード。
 *       時点は KICKOFF（試合開始時）/ GOAL（得点した時点、何点目か）/ HT（ハーフタイム）/ FIN（試合終了時）。
 *       分類パターンごとに、得点前後やハーフタイムの試合内容を比べるためのデータ。</li>
 *   <li><b>BM_M020</b>（ビュー classify_result_data_detail）: 国・リーグ・シーズン × 分類モードごとの試合数（0件のモードも出る）。</li>
 * </ul>
 *
 * <h3>分類の決め方</h3>
 * <ol>
 *   <li>通番の数値順に並べ、スコアの変化からゴールを1点ずつ取り出す（取り消しは反映、1回で2点増えたら2点）。</li>
 *   <li>各ゴールの時間は得点を検出した行の試合時間。ハーフタイム行・"45+x" は前半、FIN 行は後半とみなす。</li>
 *   <li>ゴールなし → 15。</li>
 *   <li>最初のゴールが前半 → 20分以内（切り捨てで 20 以下）か 21分〜前半か × 得点側 × 2点目（前半／後半／なし）で 1〜12。</li>
 *   <li>最初のゴールが後半 → 得点側で 13 / 14。</li>
 *   <li>判定に必要なゴールの時間が読めない → -1（条件対象外）。</li>
 * </ol>
 *
 * <h2>修正履歴（旧実装の不具合）</h2>
 * <ul>
 *   <li><b>モード15（0-0）にならなかった</b>: HT 行が 0-0 だと cond 11 の分岐に入り、-1 のまま終わっていた。</li>
 *   <li><b>モード13 が 14 に上書きされていた</b>: 13・14 の条件の両方に cond 8（後半 1-1）が入っていた。</li>
 *   <li><b>前半アディショナルタイムのゴールが後半扱い</b>: "45+2'" が 47分で min &gt; 45 になっていた。</li>
 *   <li><b>1回の取得で2点入ると分類できなかった</b>: 1-0 の行が無いと cond が立たなかった → ゴール単位で判定。</li>
 *   <li><b>取り消されたゴールで分類していた</b>、スコアが空の行を 0-0 扱いしていた。</li>
 *   <li><b>モード13/14 が HT 行必須だった</b> → ゴールの時間で前半/後半を判定するので HT 行が無くても分類できる。</li>
 *   <li><b>同じ試合が流れてくるたびに明細が重複・件数が加算されていた</b> → 試合単位で置き換え、件数はビュー。</li>
 *   <li>getMaxSeqEntities（リスト順で最初の FIN を返す・全行エラーで null）→ 通番の数値順に並べて最後の行で判定。</li>
 *   <li>クラス整理: ClassifyScoreAIConst と番号 Map を {@link ClassifyMode} に、OutputDTO・Count の Entity/Repository は廃止。</li>
 * </ul>
 *
 * <h2>懸念点・エラーが起こりそうな箇所</h2>
 * <ul>
 *   <li><b>ゴールの時間は「検出した行」の時間</b>: 実際の得点時刻より後ろにずれることがある。
 *       20分に入ったゴールを 21分の行で初めて検出すると「20分〜前半」になる。後半開始直後の行で検出した
 *       前半アディショナルタイムのゴールは後半扱いになる。</li>
 *   <li><b>同じ行でホーム・アウェー両方が増えた場合</b>はホーム → アウェーの順とみなす（最初のゴールの側が変わりうる）。</li>
 *   <li><b>PK 戦の行は使わない</b>。PK で終わった試合は、最後の行が FIN でなければ対象外。</li>
 *   <li><b>シーズンは処理日基準</b>・<b>同じ組み合わせの試合がシーズン内に2試合ある場合は上書き</b>（Writer 参照）。</li>
 * </ul>
 *
 * @author shiraishitoshio
 */
@Component
public class MatchClassificationResultStat implements AnalyzeEntityIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = MatchClassificationResultStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = MatchClassificationResultStat.class.getName();

	/** 実行モード */
	private static final String EXEC_MODE = "BM_M019_BM_M020_MATCH_CLASSIFICATION_RESULT";

	/** BM_STAT_NUMBER */
	private static final String BM_NUMBER = "BM_M019_BM_M020";

	/** 「20分以内」の上限（分・切り捨て後） */
	private static final int WITHIN_MINUTES = 20;

	/** 前半の終わり（分） */
	private static final double HALF_MINUTES = 45.0;

	@Autowired
	private BookDataToMatchClassificationResultMapper bookDataToMatchClassificationResultMapper;

	@Autowired
	private MatchClassificationResultWriter matchClassificationResultWriter;

	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * {@inheritDoc}
	 */
	@Override
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public void calcStat(Map<String, Map<String, List<BookDataEntity>>> entities) {
		final String METHOD_NAME = "calcStat";
		this.manageLoggerComponent.init(EXEC_MODE, null);
		this.manageLoggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		int matchCount = 0;
		int savedMatchCount = 0;
		int notFinishedCount = 0;
		int invalidCount = 0;
		int seasonSkipCount = 0;
		int exceptCount = 0;

		// シーズンのキャッシュは Writer 側（スレッド単位）。前回の残りを使わないよう開始時にも破棄する
		this.matchClassificationResultWriter.clearSeasonCache();
		try {
			if (entities == null || entities.isEmpty()) {
				debugLog(METHOD_NAME, BM_NUMBER + " 入力データなし");
				return;
			}

			for (Entry<String, Map<String, List<BookDataEntity>>> outerEntry : entities.entrySet()) {
				Map<String, List<BookDataEntity>> matchMap = outerEntry.getValue();
				if (matchMap == null || matchMap.isEmpty()) {
					continue;
				}
				String[] sp = CountryLeagueParser.parse(outerEntry.getKey());
				String country = (sp == null || sp.length < 2) ? null : trimOrNull(sp[0]);
				String league = (sp == null || sp.length < 2) ? null : trimOrNull(sp[1]);
				Integer roundNo = CountryLeagueParser.parseRoundNo(outerEntry.getKey());
				if (country == null || league == null || roundNo == null) {
					invalidCount += matchMap.size();
					debugLog(METHOD_NAME, BM_NUMBER + " 国,リーグを分割できないためスキップ: " + outerEntry.getKey());
					continue;
				}

				for (Entry<String, List<BookDataEntity>> matchEntry : matchMap.entrySet()) {
					matchCount++;
					String matchKey = matchEntry.getKey();

					List<BookDataEntity> sorted = sortUsableRows(matchEntry.getValue());
					if (sorted.isEmpty()) {
						invalidCount++;
						continue;
					}
					BookDataEntity end = sorted.get(sorted.size() - 1);
					if (!BookMakersCommonConst.FIN.equals(trimOrNull(end.getTime()))) {
						// 試合途中: 終了後のデータが届いたときに処理する
						notFinishedCount++;
						continue;
					}
					String home = trimOrNull(end.getHomeTeamName());
					String away = trimOrNull(end.getAwayTeamName());
					if (home == null || away == null
							|| parseScore(end.getHomeScore()) == null || parseScore(end.getAwayScore()) == null) {
						invalidCount++;
						debugLog(METHOD_NAME, BM_NUMBER + " チーム名・最終スコアが取れないためスキップ: matchKey=" + matchKey);
						continue;
					}

					Timeline timeline = buildTimeline(sorted);
					ClassifyMode mode = classify(timeline.goals);
					if (mode == ClassifyMode.EXCEPT_FOR_CONDITION) {
						exceptCount++;
					}

					List<MatchClassificationResultEntity> rows = buildRows(timeline, end, home, away, mode);
					try {
						this.matchClassificationResultWriter.saveMatch(country, league, roundNo, rows);
						savedMatchCount++;
					} catch (MatchClassificationResultWriter.SeasonNotResolvedException e) {
						// シーズン不明の国,リーグ: 何も保存されていないので、この試合だけスキップ
						seasonSkipCount++;
						debugLog(METHOD_NAME, BM_NUMBER + " シーズン取得不可のためスキップ: matchKey=" + matchKey
								+ " (" + e.getMessage() + ")");
					}
				}
			}
		} finally {
			this.matchClassificationResultWriter.clearSeasonCache();
			debugLog(METHOD_NAME, BM_NUMBER + " matchCount=" + matchCount
					+ ", savedMatchCount=" + savedMatchCount
					+ ", 条件対象外=" + exceptCount
					+ ", notFinishedCount=" + notFinishedCount
					+ ", invalidCount=" + invalidCount
					+ ", seasonSkipCount=" + seasonSkipCount);
			this.manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);
			this.manageLoggerComponent.clear();
		}
	}

	// ===== 分類 =====

	/**
	 * ゴールの並びから分類モードを決める。
	 */
	static ClassifyMode classify(List<Goal> goals) {
		if (goals.isEmpty()) {
			return ClassifyMode.NO_GOAL;
		}
		Goal first = goals.get(0);
		if (first.firstHalf == null) {
			return ClassifyMode.EXCEPT_FOR_CONDITION;
		}
		if (!first.firstHalf) {
			return first.home ? ClassifyMode.NO_GOAL_FIRST_HALF_NEXT_HOME_SCORE
					: ClassifyMode.NO_GOAL_FIRST_HALF_NEXT_AWAY_SCORE;
		}

		// 最初のゴールが前半
		if (first.minute == null) {
			return ClassifyMode.EXCEPT_FOR_CONDITION;
		}
		FirstGoalBand band = ((int) Math.floor(first.minute) <= WITHIN_MINUTES)
				? FirstGoalBand.WITHIN_20
				: FirstGoalBand.BETWEEN_20_AND_45;

		NextGoal next;
		if (goals.size() == 1) {
			next = NextGoal.NONE;
		} else {
			Goal second = goals.get(1);
			if (second.firstHalf == null) {
				return ClassifyMode.EXCEPT_FOR_CONDITION;
			}
			next = second.firstHalf ? NextGoal.BEFORE_HALF : NextGoal.AFTER_HALF;
		}
		return ClassifyMode.ofFirstHalfGoal(first.home, band, next);
	}

	/**
	 * 通番順の行から、ゴールの並びと各時点の行を取り出す。
	 */
	static Timeline buildTimeline(List<BookDataEntity> sorted) {
		Timeline t = new Timeline();
		int homeCount = 0;
		int awayCount = 0;
		for (BookDataEntity row : sorted) {
			Integer h = parseScore(row.getHomeScore());
			Integer a = parseScore(row.getAwayScore());
			if (h == null || a == null) {
				continue;
			}
			if (t.kickoff == null) {
				t.kickoff = row;
				t.kickoffIsScoreless = (h == 0 && a == 0);
			}

			// 取り消し: その側の最後のゴールから消す
			while (homeCount > h) {
				removeLast(t.goals, true);
				homeCount--;
			}
			while (awayCount > a) {
				removeLast(t.goals, false);
				awayCount--;
			}
			t.goalRows.keySet().removeIf(no -> no > h + a);

			if (homeCount < h || awayCount < a) {
				String time = trimOrNull(row.getTime());
				Double minute = toMinutes(time);
				Boolean firstHalf = isFirstHalf(time, minute);
				// 同じ行で両方増えた場合はホーム → アウェーの順とみなす
				while (homeCount < h) {
					t.goals.add(new Goal(true, minute, firstHalf));
					homeCount++;
				}
				while (awayCount < a) {
					t.goals.add(new Goal(false, minute, firstHalf));
					awayCount++;
				}
				t.goalRows.put(h + a, row);
			}

			String time = trimOrNull(row.getTime());
			if (isHalfTime(time)) {
				t.halfTime = row;
				t.halfTimeGoalNo = h + a;
			}
		}
		return t;
	}

	private static void removeLast(List<Goal> goals, boolean home) {
		for (int i = goals.size() - 1; i >= 0; i--) {
			if (goals.get(i).home == home) {
				goals.remove(i);
				return;
			}
		}
	}

	/**
	 * 保存する行（KICKOFF / GOAL / HT / FIN）を作る。
	 */
	private List<MatchClassificationResultEntity> buildRows(Timeline t, BookDataEntity end,
			String home, String away, ClassifyMode mode) {
		List<MatchClassificationResultEntity> rows = new ArrayList<>();
		if (t.kickoff != null && t.kickoffIsScoreless && t.kickoff != end) {
			rows.add(toEntity(t.kickoff, MatchClassificationResultEntity.SNAPSHOT_KICKOFF, 0, home, away, mode));
		}
		for (Map.Entry<Integer, BookDataEntity> e : t.goalRows.entrySet()) {
			rows.add(toEntity(e.getValue(), MatchClassificationResultEntity.SNAPSHOT_GOAL, e.getKey(), home, away, mode));
		}
		if (t.halfTime != null) {
			rows.add(toEntity(t.halfTime, MatchClassificationResultEntity.SNAPSHOT_HT, t.halfTimeGoalNo, home, away, mode));
		}
		rows.add(toEntity(end, MatchClassificationResultEntity.SNAPSHOT_FIN, t.goals.size(), home, away, mode));
		return rows;
	}

	private MatchClassificationResultEntity toEntity(BookDataEntity book, String snapshotType, int goalNo,
			String home, String away, ClassifyMode mode) {
		MatchClassificationResultEntity e = this.bookDataToMatchClassificationResultMapper.mapStruct(book);
		e.setSnapshotType(snapshotType);
		e.setGoalNo(goalNo);
		e.setClassifyMode(mode.getCode());
		// キー項目は FIN 行の値（前後の空白除去）で揃える
		e.setHomeTeamName(home);
		e.setAwayTeamName(away);
		return e;
	}

	// ===== 時間の判定 =====

	private static boolean isHalfTime(String time) {
		return BookMakersCommonConst.HALF_TIME.equals(time) || BookMakersCommonConst.FIRST_HALF_TIME.equals(time);
	}

	/**
	 * 試合時間を分に変換する（読めなければ null）。
	 * ExecuteMainUtil.convertToMinutes は読めない形式（"中断"、"'" の無い "23" など）を 0 分にしてしまうため、
	 * 読める形式（FIN・ハーフタイム・"mm:ss"・"45+2'"・"23'"）だけ渡す。
	 */
	static Double toMinutes(String time) {
		if (time == null) {
			return null;
		}
		boolean readable = BookMakersCommonConst.FIN.equals(time) || isHalfTime(time)
				|| time.contains(":") || time.contains("+") || time.endsWith("'");
		if (!readable) {
			return null;
		}
		try {
			return ExecuteMainUtil.convertToMinutes(time);
		} catch (RuntimeException e) {
			return null;
		}
	}

	/**
	 * 前半のゴールか（判定できなければ null）。
	 * ハーフタイム行・"45+x" は前半、FIN 行は後半。それ以外は 45分以下なら前半。
	 */
	static Boolean isFirstHalf(String time, Double minute) {
		if (time == null) {
			return null;
		}
		if (isHalfTime(time) || time.matches("^45\\s*\\+.*")) {
			return Boolean.TRUE;
		}
		if (BookMakersCommonConst.FIN.equals(time)) {
			return Boolean.FALSE;
		}
		if (minute == null) {
			return null;
		}
		return minute <= HALF_MINUTES;
	}

	// ===== 共通 =====

	/**
	 * 使える行だけを通番の数値順に並べる。
	 * null 行・取得エラー行・ゴール取り消し判定の行・PK 戦の行は除く。
	 */
	private static List<BookDataEntity> sortUsableRows(List<BookDataEntity> rows) {
		List<BookDataEntity> sorted = new ArrayList<>();
		if (rows == null) {
			return sorted;
		}
		for (BookDataEntity e : rows) {
			if (e == null) {
				continue;
			}
			if (BookMakersCommonConst.GET_UNEXPECTED_ERROR.equals(e.getGoalTime())
					|| BookMakersCommonConst.GET_UNEXPECTED_ERROR.equals(e.getGoalTeamMember())) {
				continue;
			}
			if (BookMakersCommonConst.GOAL_DELETE.equals(e.getJudge())) {
				continue;
			}
			String t = e.getTime();
			if (t != null && t.contains(BookMakersCommonConst.PENALTY)) {
				continue;
			}
			sorted.add(e);
		}
		sorted.sort(Comparator.comparingLong(e -> seqToLong(e.getSeq())));
		return sorted;
	}

	/** スコアを整数に変換（空・数値以外・負は null） */
	static Integer parseScore(String value) {
		String s = trimOrNull(value);
		if (s == null) {
			return null;
		}
		s = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFKC);
		if (!s.matches("\\d+")) {
			return null;
		}
		try {
			return Integer.parseInt(s);
		} catch (NumberFormatException e) {
			return null;
		}
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

	private static String trimOrNull(String s) {
		return (s == null || s.isBlank()) ? null : s.trim();
	}

	private void debugLog(String methodName, String message) {
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, methodName, MessageCdConst.MCD00099I_LOG, message);
	}

	/**
	 * 1ゴール（得点側・分・前半か）。
	 */
	static final class Goal {
		final boolean home;
		final Double minute;
		final Boolean firstHalf;

		Goal(boolean home, Double minute, Boolean firstHalf) {
			this.home = home;
			this.minute = minute;
			this.firstHalf = firstHalf;
		}
	}

	/**
	 * 1試合の流れ（ゴールの並び・各時点の行）。
	 */
	static final class Timeline {
		/** ゴール（得点順。取り消し反映済み） */
		final List<Goal> goals = new ArrayList<>();
		/** 合計 n 点目を検出した行（n → 行） */
		final TreeMap<Integer, BookDataEntity> goalRows = new TreeMap<>();
		/** 最初の行 */
		BookDataEntity kickoff;
		/** 最初の行が 0-0 か */
		boolean kickoffIsScoreless;
		/** ハーフタイムの行（複数あれば最後） */
		BookDataEntity halfTime;
		/** ハーフタイム時点の合計得点 */
		int halfTimeGoalNo;
	}
}
