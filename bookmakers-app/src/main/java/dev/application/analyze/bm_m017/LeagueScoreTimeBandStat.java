package dev.application.analyze.bm_m017;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.application.analyze.bm_m017.LeagueScoreTimeBandWriter.MatchKey;
import dev.application.analyze.common.util.BookMakersCommonConst;
import dev.application.analyze.interf.AnalyzeEntityIF;
import dev.common.constant.MessageCdConst;
import dev.common.entity.BookDataEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.util.ExecuteMainUtil;

/**
 * BM_M017_BM_M018統計分析ロジック（手動データ投入の場合は適用対象外）
 *
 * <h2>何を導出するクラスか</h2>
 * <p>
 * リーグ・シーズンごとに「ゴールがどの時間帯に入りやすいか」を求める。
 * 試合終了（FIN）した試合のゴールを1点ずつ league_score_goal_event に保存し、件数・割合はビューで出す。
 * </p>
 * <ul>
 *   <li><b>BM_M017</b>（ビュー league_score_time_band_stats）: 合計N点目のゴールがどの時間帯に入ったか。
 *       target = その時間帯に入った数、search = 合計N点目のゴール総数、ratio = target ÷ search（%）。</li>
 *   <li><b>BM_M018</b>（ビュー league_score_time_band_stats_split_score）: どちらが（H/A）、得点後何対何になるゴールを、
 *       どの時間帯に取ったか。例: 「アウェーが取って 1-1 になるゴールは 60〜70分が 25%」。
 *       search = 同じ得点側・得点後スコアのゴール総数。</li>
 * </ul>
 *
 * <h3>ゴールの取り出し方</h3>
 * <ol>
 *   <li>通番の数値順に並べる。取得エラー行・ゴール取り消し判定の行・PK 戦の行は使わない。</li>
 *   <li>最後の行が試合終了（FIN）の試合だけを対象にする（試合途中は次回以降に処理）。</li>
 *   <li>前の行よりスコアが増えた行で、増えた点数分のゴールを記録する（0-0 → 2-0 なら 1-0 と 2-0 の2点）。
 *       同じ行でホーム・アウェー両方が増えた場合は、ホーム → アウェーの順とみなす。</li>
 *   <li>スコアが減った場合（ゴール取り消し）は、その側の最後のゴールを取り消す。</li>
 *   <li>時間帯は得点を検出した行の試合時間から {@link ExecuteMainUtil#classifyMatchTime} で決める。
 *       ただしハーフタイム行と "45+x" は前半の得点なので "40〜45" にする（classifyMatchTime は "45〜50" を返すため）。</li>
 * </ol>
 *
 * <h3>保存方法</h3>
 * <p>
 * 1試合分のゴールを {@link LeagueScoreTimeBandWriter#saveMatch} で1トランザクションで保存する（既存行は置き換え）。
 * シーズン・seq は Writer で設定する。同じ試合を何度処理しても件数は増えない（冪等）。
 * シーズンが取得できない国,リーグの試合は、その試合だけスキップする（その他の例外は処理全体を止める）。
 * </p>
 *
 * <h2>修正履歴（旧実装の不具合）</h2>
 * <ul>
 *   <li>時間帯を全ゴール分連結した文字列（"0〜10,0〜10,10〜20,…"）で検索・登録していたため、既存行にほぼ当たらず
 *       target=1 の行が毎回増えていた → 1ゴール1行に変更し、件数はビューで数える。</li>
 *   <li>BM_M018 でホーム側とアウェー側が別々の行（片方が空文字）になっていた。得点時の相手スコアも失われていた
 *       → 得点側・得点後のホーム/アウェースコアを1行に持つ。</li>
 *   <li>試合途中のデータも数え、流れてくるたびに同じゴールを二重に数えていた → FIN の試合だけ、試合単位で置き換え。</li>
 *   <li>通番順に並べずに差分を取っていた → 通番を数値で並べ替え。</li>
 *   <li>1回で2点増えると1点扱いになっていた → 増えた点数分記録。</li>
 *   <li>"国-リーグ-テーブル" を split("-") で戻していたため、"-" を含む国・リーグ名で壊れていた → キーはクラスで持つ。</li>
 *   <li>時間帯が取れないと "null" が保存されていた → 時間帯不明のゴールは保存しない（件数をログに出す）。</li>
 *   <li>target と search が常に同じで ratio が未設定だった → ビューで計算。</li>
 *   <li>トランザクションなし・読み取り→UPDATE の加算漏れ・件数が文字列型・メタデータ未設定 → Writer で解消。</li>
 *   <li>全行の filePath をデバッグログに出していた・parallelStream + synchronized で実質直列だった → 廃止。</li>
 *   <li>クラス整理: LeagueScoreMainData / LeagueScoreRegisterData / LeagueScoreUpdateData / LeagueScoreTimeBandOutputDTO /
 *       2つの Entity・Repository を、Entity・Repository・Writer 各1つにまとめた。</li>
 * </ul>
 *
 * <h2>懸念点・エラーが起こりそうな箇所</h2>
 * <ul>
 *   <li><b>時間帯は「得点を検出した行」の時間</b>: 実際の得点時刻ではない。データ取得間隔が粗いと後ろの時間帯にずれる。
 *       試合終了（FIN）行で初めて検出したゴールは "90〜" になる。</li>
 *   <li><b>同じ行でホーム・アウェー両方が増えた場合の順番</b>はホーム先とみなす（実際と違うことがある）。</li>
 *   <li><b>時間帯の一覧</b>（{@link #TIME_BANDS}）は classifyMatchTime の区切りと合わせてある。
 *       classifyMatchTime を変えたらここも変えること。</li>
 *   <li><b>延長戦</b>: 延長の得点は "90〜" に入る。PK 戦の行は使わない（PK で終わった試合は最後の行が FIN でなければ対象外）。</li>
 *   <li><b>シーズンは処理日基準</b>・<b>同じ組み合わせの試合がシーズン内に2試合ある場合は上書き</b>（Writer 参照）。</li>
 * </ul>
 *
 * @author shiraishitoshio
 */
@Component
public class LeagueScoreTimeBandStat implements AnalyzeEntityIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = LeagueScoreTimeBandStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = LeagueScoreTimeBandStat.class.getName();

	/** 実行モード */
	private static final String EXEC_MODE = "BM_M017_BM_M018_LEAGUE_SCORE_TIME_BAND";

	/** BM_STAT_NUMBER */
	private static final String BM_NUMBER = "BM_M017_BM_M018";

	/** 時間帯（ExecuteMainUtil.classifyMatchTime と同じ区切り・並び順） */
	static final List<String> TIME_BANDS = Arrays.asList(
			"0〜10", "10〜20", "20〜30", "30〜40", "40〜45",
			"45〜50", "50〜60", "60〜70", "70〜80", "80〜90", "90〜");

	/** 前半の最後の時間帯 */
	private static final String FIRST_HALF_LAST_BAND = "40〜45";

	/** 登録処理 */
	@Autowired
	private LeagueScoreTimeBandWriter leagueScoreTimeBandWriter;

	/** ログ管理クラス */
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
		int goalCount = 0;
		int unknownBandGoalCount = 0;

		// シーズンのキャッシュは Writer 側（スレッド単位）。前回の残りを使わないよう開始時にも破棄する
		this.leagueScoreTimeBandWriter.clearSeasonCache();
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
				String[] sp = ExecuteMainUtil.splitLeagueInfo(outerEntry.getKey());
				String country = (sp == null || sp.length < 2) ? null : trimOrNull(sp[0]);
				String league = (sp == null || sp.length < 2) ? null : trimOrNull(sp[1]);
				if (country == null || league == null) {
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
					if (!dev.common.constant.BookMakersCommonConst.FIN.equals(trimOrNull(end.getTime()))) {
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

					List<Goal> goals = extractGoals(sorted);
					List<LeagueScoreGoalEventEntity> rows = new ArrayList<>(goals.size());
					int h = 0;
					int a = 0;
					for (Goal g : goals) {
						if (LeagueScoreGoalEventEntity.SIDE_HOME.equals(g.side)) {
							h++;
						} else {
							a++;
						}
						if (g.band == null) {
							// 時間帯不明: 何点目・スコアの数え方は崩さず、行だけ保存しない
							unknownBandGoalCount++;
							continue;
						}
						LeagueScoreGoalEventEntity e = new LeagueScoreGoalEventEntity();
						e.setMatchId(trimOrNull(end.getMatchId()));
						e.setGoalNo(h + a);
						e.setScoredSide(g.side);
						e.setHomeScoreValue(h);
						e.setAwayScoreValue(a);
						e.setTimeRangeArea(g.band);
						e.setTimeBandOrder(TIME_BANDS.indexOf(g.band));
						e.setGoalTimes(g.times);
						rows.add(e);
					}

					try {
						this.leagueScoreTimeBandWriter.saveMatch(new MatchKey(country, league, home, away), rows);
						savedMatchCount++;
						goalCount += rows.size();
					} catch (LeagueScoreTimeBandWriter.SeasonNotResolvedException e) {
						// シーズン不明の国,リーグ: 何も保存されていないので、この試合だけスキップ
						seasonSkipCount++;
						debugLog(METHOD_NAME, BM_NUMBER + " シーズン取得不可のためスキップ: matchKey=" + matchKey
								+ " (" + e.getMessage() + ")");
					}
				}
			}
		} finally {
			this.leagueScoreTimeBandWriter.clearSeasonCache();
			debugLog(METHOD_NAME, BM_NUMBER + " matchCount=" + matchCount
					+ ", savedMatchCount=" + savedMatchCount
					+ ", goalCount=" + goalCount
					+ ", unknownBandGoalCount=" + unknownBandGoalCount
					+ ", notFinishedCount=" + notFinishedCount
					+ ", invalidCount=" + invalidCount
					+ ", seasonSkipCount=" + seasonSkipCount);
			this.manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);
			this.manageLoggerComponent.clear();
		}
	}

	/**
	 * 通番順の行からゴールを取り出す（取り消しを反映した、得点順のリスト）。
	 */
	static List<Goal> extractGoals(List<BookDataEntity> sorted) {
		List<Goal> goals = new ArrayList<>();
		int homeCount = 0;
		int awayCount = 0;
		for (BookDataEntity row : sorted) {
			Integer h = parseScore(row.getHomeScore());
			Integer a = parseScore(row.getAwayScore());
			if (h == null || a == null) {
				continue;
			}
			// 取り消し: その側の最後のゴールから消す
			while (homeCount > h) {
				removeLast(goals, LeagueScoreGoalEventEntity.SIDE_HOME);
				homeCount--;
			}
			while (awayCount > a) {
				removeLast(goals, LeagueScoreGoalEventEntity.SIDE_AWAY);
				awayCount--;
			}
			if (homeCount == h && awayCount == a) {
				continue;
			}
			String times = trimOrNull(row.getTime());
			String band = resolveBand(times);
			// 同じ行で両方増えた場合はホーム → アウェーの順とみなす
			while (homeCount < h) {
				goals.add(new Goal(LeagueScoreGoalEventEntity.SIDE_HOME, band, times));
				homeCount++;
			}
			while (awayCount < a) {
				goals.add(new Goal(LeagueScoreGoalEventEntity.SIDE_AWAY, band, times));
				awayCount++;
			}
		}
		return goals;
	}

	private static void removeLast(List<Goal> goals, String side) {
		for (int i = goals.size() - 1; i >= 0; i--) {
			if (side.equals(goals.get(i).side)) {
				goals.remove(i);
				return;
			}
		}
	}

	/**
	 * 試合時間から時間帯を決める（決められなければ null）。
	 * ハーフタイム行・"45+x" は前半の得点なので "40〜45"。それ以外は ExecuteMainUtil.classifyMatchTime。
	 */
	static String resolveBand(String time) {
		if (time == null) {
			return null;
		}
		if (BookMakersCommonConst.HALF_TIME.equals(time) || BookMakersCommonConst.FIRST_HALF_TIME.equals(time)
				|| time.matches("^45\\s*\\+.*")) {
			return FIRST_HALF_LAST_BAND;
		}
		// convertToMinutes が分数を読めるのは FIN・"mm:ss"・"45+2'"・"23'" の形だけ。
		// それ以外（"中断"、"'" のない "23" など）は 0 分扱いになり "0〜10" に誤分類されるため使わない
		boolean readable = dev.common.constant.BookMakersCommonConst.FIN.equals(time)
				|| time.contains(":") || time.contains("+") || time.endsWith("'");
		if (!readable) {
			return null;
		}
		try {
			String band = ExecuteMainUtil.classifyMatchTime(time);
			return TIME_BANDS.contains(band) ? band : null;
		} catch (RuntimeException e) {
			// "23" のような想定外の形式で数値変換に失敗した場合
			return null;
		}
	}

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
			if (dev.common.constant.BookMakersCommonConst.GET_UNEXPECTED_ERROR.equals(e.getGoalTime())
					|| dev.common.constant.BookMakersCommonConst.GET_UNEXPECTED_ERROR.equals(e.getGoalTeamMember())) {
				continue;
			}
			if (BookMakersCommonConst.GOAL_DELETE.equals(e.getJudge())) {
				continue;
			}
			String t = e.getTime();
			if (t != null && t.contains(dev.common.constant.BookMakersCommonConst.PENALTY)) {
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
	 * 1ゴール（得点側・時間帯・検出した行の試合時間）。
	 */
	static final class Goal {
		final String side;
		final String band;
		final String times;

		Goal(String side, String band, String times) {
			this.side = side;
			this.band = band;
			this.times = times;
		}
	}
}