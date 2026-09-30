package dev.application.analyze.bm_m031;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.application.analyze.interf.AnalyzeEntityIF;
import dev.common.constant.BookMakersCommonConst;
import dev.common.constant.MessageCdConst;
import dev.common.entity.BookDataEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.util.CountryLeagueParser;
import dev.common.util.RecordTimeConverter;

/**
 * BM_M031 統計分析ロジック（チームの表面データ: 成績・今の状態）。
 *
 * <h2>何を導出するクラスか</h2>
 * <p>
 * 試合終了した試合ごとに、チーム × 1試合の明細（{@link SurfaceOverviewMatchEntity}、1試合でホーム視点・アウェー視点の2行）を作り、
 * surface_overview_match に UPSERT する。集計・「今の状態」はすべてビューが明細から計算する。
 * </p>
 * <ul>
 *   <li>surface_overview: 月 × チームの成績（勝敗・勝ち点・前後半の得失点と割合・無失点・無得点・先制・逆転の内訳・
 *       序盤/中盤/終盤の勝敗）と、その月の最後の試合終了時点の状態（シーズン累計・連勝/連敗/無敗/得点継続・表示文言）。</li>
 *   <li>surface_overview_season: シーズン × チームの最新の状態。</li>
 *   <li>surface_overview_match_state: 試合ごとの、その試合終了時点の状態。</li>
 *   <li>surface_overview_standing: ラウンド N 終了時点の順位（旧 BM_M028 の置き換え候補）。</li>
 *   <li>surface_overview_process: 直前ラウンドからの差分（旧 BM_M032）。</li>
 * </ul>
 *
 * <h2>欠けデータを後から入れた場合</h2>
 * <p>
 * 明細は（シーズン, 国, リーグ, チーム, 対戦相手, H/A）で一意なので、欠けていた試合を後から流すと、その試合の2行が増えるだけ。
 * 連続記録（ラウンド番号の順）・シーズン累計・順位はビューが毎回明細から計算するため、その試合より後のラウンドの値も自動で正しくなる。
 * 同じ試合が再送されても上書きされるだけで二重にならない。
 * </p>
 *
 * <h2>1試合の明細の作り方</h2>
 * <ul>
 *   <li>対象: 最後の行が試合終了（FIN）または PK 戦の行の試合。取得エラー行（GET_UNEXPECTED_ERROR）は除き、通番の数値順に並べる。</li>
 *   <li>スコア: PK 戦の行を除いた最後の行（PK 戦の得点は得失点に入れない）。</li>
 *   <li>PK 決着: PK 戦の行があり、PK 戦を除いたスコアが同点の場合、最後の PK 戦の行のスコアで勝敗を決め、
 *       勝ち点は point_setting_master の「PK勝ち」「PK負け」（無ければ通常の勝ち/負け）。PK 戦の行のスコアも同点なら引分。</li>
 *   <li>前半/後半: 最初のハーフタイム行のスコアが前半、試合終了 − ハーフタイムが後半（ハーフタイム行が無ければ null）。
 *       旧実装は「ハーフタイム − 最初の行」で、取得開始時点で入っていた点が前半から抜けていた。</li>
 *   <li>先制: 0-0 から最初に変わったスコアで判定（最初の行が既に 1-0 でも判定できる）。同時に両方が変わった場合は不明（U）。</li>
 *   <li>リード/ビハインド・1-0/2-0/0-1/0-2 になったか: 各行のスコアから（チーム視点）。試合終了の行しか無い試合は flowKnown = false。</li>
 *   <li>ラウンド番号: キーの「ラウンド N」。年月: 試合終了行の記録時間。</li>
 *   <li>サイト表示の順位（teamRank）: 試合終了行の順位（旧 BM_M033 順位履歴の元データ）。
 *       ラウンドごとの順位はビュー surface_overview_standing（site_rank / 計算した rank_no / display_rank）。</li>
 * </ul>
 *
 * <h2>修正履歴（旧実装からの変更）</h2>
 * <ul>
 *   <li>「前回の値 ＋ 今回の1試合」を届いた順に足し込む方式をやめ、明細＋ビューにした（再送で二重加算、
 *       古い試合を後から入れると無敗・得点継続が今の記録に足される、連勝の履歴にシーズン条件が無い、を解消）。</li>
 *   <li>初勝利モチベ・序盤/中盤/終盤・逆境を月の行ではなくシーズン累計で判定（旧は月が変わるとリセット）。</li>
 *   <li>表示のしきい値を旧コメントどおりにした（連勝・連敗は 3 以上。旧コードは 1 以上）。</li>
 *   <li>ハーフタイム行が無い試合で得点・失点を全く足さない（勝敗だけ足す）不整合を解消（合計は必ず入れる）。</li>
 *   <li>年月を record_time の文字列分割ではなく RecordTimeConverter で取得。</li>
 *   <li>チーム名は試合データから取る（旧はキー "home-away" を "-" で分割しており、名前に "-" を含むチームで壊れた）。</li>
 *   <li>「国: リーグ - ラウンドN」形式以外のキーは無視。シーズン・seq（seq_counter 採番）に対応（Writer）。</li>
 *   <li>BM_M028（過去順位）・BM_M032（差分 process）の呼び出しをやめた。順位はビュー surface_overview_standing、
 *       直前ラウンドとの差分はビュー surface_overview_process（旧 BM_M032。1試合の明細がそのまま差分）。</li>
 * </ul>
 *
 * <h2>懸念点・エラーが起こりそうな箇所</h2>
 * <ul>
 *   <li><b>ラウンドが欠けていると連続記録は途切れる</b>（ラウンド番号の順で数えるため）。欠けを埋めればつながる。
 *       延期試合でラウンドの順と実際の試合順が違う場合も、ラウンドの順で数える。</li>
 *   <li><b>ラウンド番号が取れない試合</b>は月別の成績には入るが、連続記録・シーズン累計・順位には入らない。</li>
 *   <li><b>勝ち点は保存時の設定</b>で計算する。point_setting_master を変えた場合は、その試合を流し直すと反映される。</li>
 *   <li><b>試合終了の行しか無い試合</b>は逆転の判定ができない（flowKnown = false。逆転勝ち・逆転負けに数えない）。</li>
 *   <li><b>記録時間の年月は JVM のタイムゾーン</b>で決まる（日付をまたぐ試合は月がずれることがある）。</li>
 *   <li><b>昇格組・降格組</b>は前シーズンの所属リーグが必要なため、今回は出していない（旧実装も未設定）。</li>
 * </ul>
 *
 * @author shiraishitoshio
 */
@Component
public class SurfaceOverviewStat implements AnalyzeEntityIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = SurfaceOverviewStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = SurfaceOverviewStat.class.getName();

	/** 実行モード */
	private static final String EXEC_MODE = "BM_M031_SURFACE_OVERVIEW";

	/** BM_STAT_NUMBER */
	private static final String BM_NUMBER = "BM_M031";

	/** キーのラウンド番号（"… - ラウンド 5" の 5） */
	private static final Pattern ROUND_PATTERN = Pattern.compile("(?:ラウンド|Round)\\s*(\\d+)");

	/** 順位（"1"・"1."・"1位"・"1.0"） */
	private static final Pattern RANK_PATTERN = Pattern.compile("(\\d+)(?:\\.0*)?\\s*(?:位)?\\.?");

	/** 総ラウンド数（序盤/中盤/終盤） */
	@Autowired
	private BmM031SurfaceOverviewBean surfaceOverviewBean;

	/** 勝ち点設定 */
	@Autowired
	private PointSettingBean pointSettingBean;

	/** 登録 */
	@Autowired
	private SurfaceOverviewWriter surfaceOverviewWriter;

	/** ロガー */
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
		int savedCount = 0;
		int notFinishedCount = 0;
		int invalidCount = 0;
		int seasonSkipCount = 0;

		// シーズンのキャッシュは Writer 側（スレッド単位）。前回の残りを使わないよう開始時にも破棄する
		this.surfaceOverviewWriter.clearSeasonCache();
		try {
			if (entities == null || entities.isEmpty()) {
				debugLog(METHOD_NAME, BM_NUMBER + " 入力データなし");
				return;
			}
			this.surfaceOverviewBean.init();
			this.pointSettingBean.reload();

			for (Map.Entry<String, Map<String, List<BookDataEntity>>> outer : entities.entrySet()) {
				Map<String, List<BookDataEntity>> matchMap = outer.getValue();
				if (matchMap == null || matchMap.isEmpty()) {
					continue;
				}
				// 「国: リーグ - ラウンドN」形式でないキーは無視
				String[] cl = CountryLeagueParser.parse(outer.getKey());
				if (cl == null) {
					invalidCount += matchMap.size();
					debugLog(METHOD_NAME, BM_NUMBER + " 対象外のキーのためスキップ: " + outer.getKey());
					continue;
				}
				String country = cl[0];
				String league = cl[1];
				Integer roundNo = parseRound(outer.getKey());

				for (Map.Entry<String, List<BookDataEntity>> match : matchMap.entrySet()) {
					matchCount++;
					String matchKey = match.getKey();
					MatchOutcome o = buildOutcome(match.getValue());
					if (o == null) {
						invalidCount++;
						debugLog(METHOD_NAME, BM_NUMBER + " スコア・チーム名が取れないためスキップ: matchKey=" + matchKey);
						continue;
					}
					if (!o.finished) {
						// 試合途中: 終了後のデータが届いたときに処理する
						notFinishedCount++;
						continue;
					}
					List<SurfaceOverviewMatchEntity> rows = new ArrayList<>(2);
					rows.add(toEntity(o, true, country, league, roundNo, outer.getKey()));
					rows.add(toEntity(o, false, country, league, roundNo, outer.getKey()));
					try {
						this.surfaceOverviewWriter.saveMatch(country, league, rows);
						savedCount++;
					} catch (SurfaceOverviewWriter.SeasonNotResolvedException e) {
						// シーズン不明の国,リーグ: 何も保存されていないので、この試合だけスキップ
						seasonSkipCount++;
						debugLog(METHOD_NAME, BM_NUMBER + " シーズン取得不可のためスキップ: matchKey=" + matchKey
								+ " (" + e.getMessage() + ")");
					}
				}
			}
		} finally {
			this.surfaceOverviewWriter.clearSeasonCache();
			debugLog(METHOD_NAME, BM_NUMBER + " matchCount=" + matchCount + ", savedCount=" + savedCount
					+ ", notFinishedCount=" + notFinishedCount + ", invalidCount=" + invalidCount
					+ ", seasonSkipCount=" + seasonSkipCount);
			this.manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);
			this.manageLoggerComponent.clear();
		}
	}

	// ===== 1試合の結果 =====

	/**
	 * 1試合分のデータから結果を作る（スコア・チーム名が取れなければ null）。
	 */
	static MatchOutcome buildOutcome(List<BookDataEntity> rawRows) {
		List<BookDataEntity> rows = sortUsableRows(rawRows);
		if (rows.isEmpty()) {
			return null;
		}
		BookDataEntity last = rows.get(rows.size() - 1);
		String lastTime = trimOrNull(last.getTime());

		// PK 戦の行と、それ以外（試合本体）に分ける
		List<BookDataEntity> regular = new ArrayList<>();
		List<BookDataEntity> pk = new ArrayList<>();
		for (BookDataEntity e : rows) {
			if (isPenaltyRow(e)) {
				pk.add(e);
			} else {
				regular.add(e);
			}
		}
		if (regular.isEmpty()) {
			return null;
		}
		BookDataEntity fin = regular.get(regular.size() - 1);
		Integer h = parseScore(fin.getHomeScore());
		Integer a = parseScore(fin.getAwayScore());
		String home = trimOrNull(fin.getHomeTeamName());
		String away = trimOrNull(fin.getAwayTeamName());
		if (h == null || a == null || home == null || away == null) {
			return null;
		}

		MatchOutcome o = new MatchOutcome();
		o.finished = BookMakersCommonConst.FIN.equals(lastTime) || isPenaltyRow(last);
		o.home = home;
		o.away = away;
		o.matchId = trimOrNull(fin.getMatchId());
		o.homeRank = parseRank(fin.getHomeRank());
		o.awayRank = parseRank(fin.getAwayRank());
		o.homeScore = h;
		o.awayScore = a;
		o.matchTime = RecordTimeConverter.toTimestamp(fin.getRecordTime());

		// PK 決着（試合本体が同点のときだけ）
		if (!pk.isEmpty() && h.intValue() == a.intValue()) {
			BookDataEntity lastPk = pk.get(pk.size() - 1);
			Integer ph = parseScore(lastPk.getHomeScore());
			Integer pa = parseScore(lastPk.getAwayScore());
			o.pk = true;
			o.pkHome = ph;
			o.pkAway = pa;
			if (ph != null && pa != null && ph.intValue() != pa.intValue()) {
				o.pkHomeWin = ph > pa;
				o.pkDecided = true;
			}
		}

		// 前半（最初のハーフタイム行）
		for (BookDataEntity e : regular) {
			String t = trimOrNull(e.getTime());
			if (BookMakersCommonConst.HALF_TIME.equals(t) || BookMakersCommonConst.FIRST_HALF_TIME.equals(t)) {
				Integer hh = parseScore(e.getHomeScore());
				Integer ha = parseScore(e.getAwayScore());
				if (hh != null && ha != null && hh <= h && ha <= a) {
					o.htHome = hh;
					o.htAway = ha;
				}
				break;
			}
		}

		// スコアの推移（0-0 から）
		List<int[]> states = new ArrayList<>();
		int[] prev = { 0, 0 };
		for (BookDataEntity e : regular) {
			Integer sh = parseScore(e.getHomeScore());
			Integer sa = parseScore(e.getAwayScore());
			if (sh == null || sa == null) {
				continue;
			}
			if (sh != prev[0] || sa != prev[1]) {
				int[] cur = { sh, sa };
				states.add(cur);
				prev = cur;
			}
		}
		o.states = states;
		o.flowKnown = regular.size() >= 2;
		o.snapshotCount = rows.size();
		return o;
	}

	/**
	 * チーム視点の明細を作る（season・seq・phase は Writer で設定）。
	 */
	SurfaceOverviewMatchEntity toEntity(MatchOutcome o, boolean homeSide, String country, String league,
			Integer roundNo, String dataCategory) {
		SurfaceOverviewMatchEntity e = new SurfaceOverviewMatchEntity();
		int gf = homeSide ? o.homeScore : o.awayScore;
		int ga = homeSide ? o.awayScore : o.homeScore;

		e.setTeam(homeSide ? o.home : o.away);
		e.setOpponent(homeSide ? o.away : o.home);
		e.setHa(homeSide ? "H" : "A");
		e.setMatchId(o.matchId);
		e.setTeamRank(homeSide ? o.homeRank : o.awayRank);
		e.setRoundNo(roundNo);
		e.setDataCategory(dataCategory);
		e.setSnapshotCount(o.snapshotCount);
		if (o.pk) {
			e.setPkGoalsFor(homeSide ? o.pkHome : o.pkAway);
			e.setPkGoalsAgainst(homeSide ? o.pkAway : o.pkHome);
		}
		e.setMatchTime(o.matchTime);
		if (o.matchTime != null) {
			LocalDateTime ldt = o.matchTime.toLocalDateTime();
			e.setGameYear(ldt.getYear());
			e.setGameMonth(ldt.getMonthValue());
		}
		e.setGoalsFor(gf);
		e.setGoalsAgainst(ga);

		// 勝敗・勝ち点
		String result;
		String pointType;
		if (gf > ga) {
			result = "W";
			pointType = "勝ち";
		} else if (gf < ga) {
			result = "L";
			pointType = "負け";
		} else if (o.pkDecided) {
			boolean win = homeSide == o.pkHomeWin;
			result = win ? "W" : "L";
			pointType = win ? "PK勝ち" : "PK負け";
		} else {
			result = "D";
			pointType = "引分";
		}
		e.setResult(result);
		e.setPkFlg(o.pk);
		e.setPoints(this.pointSettingBean.getPoint(country, league, pointType));

		// 前半・後半
		if (o.htHome != null) {
			int hf = homeSide ? o.htHome : o.htAway;
			int hag = homeSide ? o.htAway : o.htHome;
			e.setGoalsFor1st(hf);
			e.setGoalsFor2nd(gf - hf);
			e.setGoalsAgainst1st(hag);
			e.setGoalsAgainst2nd(ga - hag);
		}

		// 先制・リード/ビハインド（チーム視点）
		e.setFirstGoal(firstGoal(o.states, homeSide, gf, ga));
		e.setFlowKnown(o.flowKnown);
		boolean led = false;
		boolean trailed = false;
		boolean l10 = false;
		boolean l20 = false;
		boolean t01 = false;
		boolean t02 = false;
		for (int[] s : o.states) {
			int f = homeSide ? s[0] : s[1];
			int g = homeSide ? s[1] : s[0];
			led |= f > g;
			trailed |= f < g;
			l10 |= (f == 1 && g == 0);
			l20 |= (f == 2 && g == 0);
			t01 |= (f == 0 && g == 1);
			t02 |= (f == 0 && g == 2);
		}
		e.setEverLed(led);
		e.setEverTrailed(trailed);
		e.setLed10(l10);
		e.setLed20(l20);
		e.setTrailed01(t01);
		e.setTrailed02(t02);
		return e;
	}

	/**
	 * 先制: T: このチーム / O: 相手 / N: 両者無得点 / U: 不明。
	 */
	static String firstGoal(List<int[]> states, boolean homeSide, int gf, int ga) {
		if (gf == 0 && ga == 0) {
			return "N";
		}
		if (states.isEmpty()) {
			return "U";
		}
		int[] first = states.get(0);
		int f = homeSide ? first[0] : first[1];
		int g = homeSide ? first[1] : first[0];
		if (f > 0 && g == 0) {
			return "T";
		}
		if (g > 0 && f == 0) {
			return "O";
		}
		return "U";
	}

	/**
	 * 使える行だけを通番の数値順に並べる（null 行・取得エラー行は除く）。
	 */
	static List<BookDataEntity> sortUsableRows(List<BookDataEntity> rows) {
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
			sorted.add(e);
		}
		sorted.sort(Comparator.comparingLong(e -> seqToLong(e.getSeq())));
		return sorted;
	}

	/** PK 戦の行 */
	private static boolean isPenaltyRow(BookDataEntity e) {
		String t = e.getTime();
		return t != null && t.contains(BookMakersCommonConst.PENALTY);
	}

	/** キーのラウンド番号（無ければ null） */
	static Integer parseRound(String key) {
		if (key == null) {
			return null;
		}
		String s = java.text.Normalizer.normalize(key, java.text.Normalizer.Form.NFKC);
		Matcher m = ROUND_PATTERN.matcher(s);
		if (!m.find()) {
			return null;
		}
		try {
			int v = Integer.parseInt(m.group(1));
			return v > 0 ? v : null;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** 順位を整数に変換（"1"・"1."・"1位"・"1.0" など。読めない・0 以下は null） */
	static Integer parseRank(String value) {
		String s = trimOrNull(value);
		if (s == null) {
			return null;
		}
		s = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFKC);
		Matcher m = RANK_PATTERN.matcher(s);
		if (!m.matches()) {
			return null;
		}
		try {
			int v = Integer.parseInt(m.group(1));
			return v > 0 ? v : null;
		} catch (NumberFormatException e) {
			return null;
		}
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
	 * 1試合の結果（ホーム・アウェー共通）。
	 */
	static final class MatchOutcome {
		boolean finished;
		String home;
		String away;
		String matchId;
		int homeScore;
		int awayScore;
		Timestamp matchTime;
		boolean pk;
		boolean pkDecided;
		boolean pkHomeWin;
		Integer pkHome;
		Integer pkAway;
		int snapshotCount;
		Integer homeRank;
		Integer awayRank;
		Integer htHome;
		Integer htAway;
		List<int[]> states = new ArrayList<>();
		boolean flowKnown;
	}
}