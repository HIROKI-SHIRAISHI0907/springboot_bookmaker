package dev.application.analyze.bm_m021;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
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

/**
 * BM_M021統計分析ロジック（チーム視点の試合最終成績）
 *
 * <h2>何を導出するクラスか</h2>
 * <p>
 * 試合終了した試合ごとに、チーム視点の最終成績を team_match_final_stats に保存する。
 * 1試合につきホームチーム視点（ha=H）とアウェーチーム視点（ha=A）の2行で、各行は
 * 自チームの値・対戦相手の値（opposite*）・得点/失点・スコア表示（"○2-1"）・勝敗（WIN/LOSE/DRAW）・順位を持つ。
 * チームごとの試合結果一覧や、平均などの集計の元データ。
 * </p>
 *
 * <h3>値の取り方</h3>
 * <ul>
 *   <li>すべて試合終了（FIN）行の値（試合全体の累計）。ポゼッションも FIN 行の値。</li>
 *   <li>回数は整数、期待値は小数2桁、ポゼッション・成功率は小数1桁（"%" は付けない）。</li>
 *   <li>パス系（"85% (340/400)"）は成功率・成功数・試行数に分ける。</li>
 *   <li>読めない値は null（0 にはしない）。</li>
 * </ul>
 *
 * <h3>保存方法</h3>
 * <p>
 * 2行を {@link TeamMatchFinalWriter#saveMatch} で1トランザクションで UPSERT する（シーズン・seq は Writer で設定）。
 * 同じ試合を何度処理しても行は増えない。シーズンが取得できない国,リーグの試合はその試合だけスキップする。
 * </p>
 *
 * <h2>修正履歴（旧実装の不具合）</h2>
 * <ul>
 *   <li>同じ試合が流れてくるたびに INSERT されて行が増えていた → UPSERT。</li>
 *   <li>シーズン・国・リーグを持っておらず、別リーグの同名チームを区別できなかった → 追加。</li>
 *   <li>ロングパス（FinalData.longPass 未設定）・枠内ゴール期待値・デュエル数（Mapper 未対応）・気温（綴り違い）が常に null だった。</li>
 *   <li>ポゼッションを「累計値の全スナップショット平均」にしていた（序盤の値に引っ張られる）→ FIN 行の値。値が無いと "0.00%" になっていた → null。</li>
 *   <li>PK 戦で決着した試合がスキップされていた（getMaxSeqEntities が PK 行を返していた）→ 通番順で FIN 行を探す。</li>
 *   <li>スコアが読めないと DRAW 扱いだった → その試合はスキップ。</li>
 *   <li>1試合ごとに十数行の log.info（分割値ごとにも）を出していた → 試合単位の件数ログのみ。</li>
 *   <li>クラス整理: ホーム用/アウェー用でほぼ同じ Mapper・FinalData・RetentionData・TeamMatchFinalOutputDTO・
 *       AverageFeatureOutputDTO を廃止し、{@link #buildRow} 1つで両チームの行を作る（home* と away* を切り替えるだけ）。</li>
 * </ul>
 *
 * <h2>懸念点・エラーが起こりそうな箇所</h2>
 * <ul>
 *   <li><b>PK 戦のスコア</b>: FIN 行のスコアをそのまま使う（PK の得点を含むかは元データ次第）。</li>
 *   <li><b>順位</b>: 数字だけ取り出す（"3位" → 3）。数字が無ければ null。</li>
 *   <li><b>BookDataEntity の getter 名</b>（getHomeOffSide / getTemperature など）に依存する。名前が変わるとコンパイルエラーで分かる。</li>
 *   <li><b>シーズンは処理日基準</b>・<b>同じ組み合わせの試合がシーズン内に2試合ある場合は上書き</b>（Writer 参照）。</li>
 * </ul>
 *
 * @author shiraishitoshio
 */
@Component
public class TeamMatchFinalStat implements AnalyzeEntityIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = TeamMatchFinalStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = TeamMatchFinalStat.class.getName();

	/** 実行モード */
	private static final String EXEC_MODE = "BM_M021_TEAM_MATCH_FINAL";

	/** BM_STAT_NUMBER */
	private static final String BM_NUMBER = "BM_M021";

	/** "X% (成功/試行)" 形式 */
	private static final Pattern TRI_PATTERN =
			Pattern.compile("^\\s*(\\d+(?:\\.\\d+)?)\\s*%\\s*\\(\\s*(\\d+)\\s*/\\s*(\\d+)\\s*\\)\\s*$");

	/** "X%" だけの形式 */
	private static final Pattern PERCENT_ONLY_PATTERN = Pattern.compile("^\\s*(\\d+(?:\\.\\d+)?)\\s*%\\s*$");

	/** 数字部分 */
	private static final Pattern DIGITS = Pattern.compile("(\\d+)");

	@Autowired
	private TeamMatchFinalWriter teamMatchFinalWriter;

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

		// シーズンのキャッシュは Writer 側（スレッド単位）。前回の残りを使わないよう開始時にも破棄する
		this.teamMatchFinalWriter.clearSeasonCache();
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

					BookDataEntity fin = findFinRow(matchEntry.getValue());
					if (fin == null) {
						// 試合途中: 終了後のデータが届いたときに処理する
						notFinishedCount++;
						continue;
					}
					String home = trimOrNull(fin.getHomeTeamName());
					String away = trimOrNull(fin.getAwayTeamName());
					Integer homeScore = parseScore(fin.getHomeScore());
					Integer awayScore = parseScore(fin.getAwayScore());
					if (home == null || away == null || homeScore == null || awayScore == null) {
						invalidCount++;
						debugLog(METHOD_NAME, BM_NUMBER + " チーム名・最終スコアが取れないためスキップ: matchKey=" + matchKey
								+ ", home=" + home + ", away=" + away
								+ ", score=" + fin.getHomeScore() + "-" + fin.getAwayScore());
						continue;
					}

					List<TeamMatchFinalStatsEntity> rows = new ArrayList<>(2);
					rows.add(buildRow(fin, true, home, away, homeScore, awayScore));
					rows.add(buildRow(fin, false, away, home, awayScore, homeScore));
					try {
						this.teamMatchFinalWriter.saveMatch(country, league, roundNo, rows);
						savedMatchCount++;
					} catch (TeamMatchFinalWriter.SeasonNotResolvedException e) {
						// シーズン不明の国,リーグ: 何も保存されていないので、この試合だけスキップ
						seasonSkipCount++;
						debugLog(METHOD_NAME, BM_NUMBER + " シーズン取得不可のためスキップ: matchKey=" + matchKey
								+ " (" + e.getMessage() + ")");
					}
				}
			}
		} finally {
			this.teamMatchFinalWriter.clearSeasonCache();
			debugLog(METHOD_NAME, BM_NUMBER + " matchCount=" + matchCount
					+ ", savedMatchCount=" + savedMatchCount
					+ ", notFinishedCount=" + notFinishedCount
					+ ", invalidCount=" + invalidCount
					+ ", seasonSkipCount=" + seasonSkipCount);
			this.manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);
			this.manageLoggerComponent.clear();
		}
	}

	/**
	 * 1チーム視点の行を作る（シーズン・国・リーグ・seq は Writer で設定）。
	 *
	 * @param end 試合終了（FIN）行
	 * @param isHome true: home* 項目を自チーム、away* 項目を対戦相手として使う
	 */
	static TeamMatchFinalStatsEntity buildRow(BookDataEntity end, boolean isHome,
			String team, String versus, int goalsFor, int goalsAgainst) {
		TeamMatchFinalStatsEntity e = new TeamMatchFinalStatsEntity();
		e.setTeamName(team);
		e.setVersusTeamName(versus);
		e.setHa(isHome ? "H" : "A");
		e.setMatchId(trimOrNull(end.getMatchId()));
		e.setGoalsFor(goalsFor);
		e.setGoalsAgainst(goalsAgainst);
		String result = (goalsFor > goalsAgainst) ? "WIN" : (goalsFor < goalsAgainst) ? "LOSE" : "DRAW";
		e.setResult(result);
		e.setScore(symbol(result) + goalsFor + "-" + goalsAgainst);
		e.setGameFinRank(parseRank(pick(end, isHome, BookDataEntity::getHomeRank, BookDataEntity::getAwayRank)));
		e.setOppositeGameFinRank(parseRank(pick(end, !isHome, BookDataEntity::getHomeRank, BookDataEntity::getAwayRank)));

		e.setExp(toDouble2(pick(end, isHome, BookDataEntity::getHomeExp, BookDataEntity::getAwayExp)));
		e.setOppositeExp(toDouble2(pick(end, !isHome, BookDataEntity::getHomeExp, BookDataEntity::getAwayExp)));
		e.setInGoalExp(toDouble2(pick(end, isHome, BookDataEntity::getHomeInGoalExp, BookDataEntity::getAwayInGoalExp)));
		e.setOppositeInGoalExp(toDouble2(pick(end, !isHome, BookDataEntity::getHomeInGoalExp, BookDataEntity::getAwayInGoalExp)));
		e.setDonation(toDouble1(pick(end, isHome, BookDataEntity::getHomeBallPossesion, BookDataEntity::getAwayBallPossesion)));
		e.setOppositeDonation(toDouble1(pick(end, !isHome, BookDataEntity::getHomeBallPossesion, BookDataEntity::getAwayBallPossesion)));
		e.setShootAll(toInt(pick(end, isHome, BookDataEntity::getHomeShootAll, BookDataEntity::getAwayShootAll)));
		e.setOppositeShootAll(toInt(pick(end, !isHome, BookDataEntity::getHomeShootAll, BookDataEntity::getAwayShootAll)));
		e.setShootIn(toInt(pick(end, isHome, BookDataEntity::getHomeShootIn, BookDataEntity::getAwayShootIn)));
		e.setOppositeShootIn(toInt(pick(end, !isHome, BookDataEntity::getHomeShootIn, BookDataEntity::getAwayShootIn)));
		e.setShootOut(toInt(pick(end, isHome, BookDataEntity::getHomeShootOut, BookDataEntity::getAwayShootOut)));
		e.setOppositeShootOut(toInt(pick(end, !isHome, BookDataEntity::getHomeShootOut, BookDataEntity::getAwayShootOut)));
		e.setBlockShoot(toInt(pick(end, isHome, BookDataEntity::getHomeShootBlocked, BookDataEntity::getAwayShootBlocked)));
		e.setOppositeBlockShoot(toInt(pick(end, !isHome, BookDataEntity::getHomeShootBlocked, BookDataEntity::getAwayShootBlocked)));
		e.setBigChance(toInt(pick(end, isHome, BookDataEntity::getHomeBigChance, BookDataEntity::getAwayBigChance)));
		e.setOppositeBigChance(toInt(pick(end, !isHome, BookDataEntity::getHomeBigChance, BookDataEntity::getAwayBigChance)));
		e.setCorner(toInt(pick(end, isHome, BookDataEntity::getHomeCornerKick, BookDataEntity::getAwayCornerKick)));
		e.setOppositeCorner(toInt(pick(end, !isHome, BookDataEntity::getHomeCornerKick, BookDataEntity::getAwayCornerKick)));
		e.setBoxShootIn(toInt(pick(end, isHome, BookDataEntity::getHomeBoxShootIn, BookDataEntity::getAwayBoxShootIn)));
		e.setOppositeBoxShootIn(toInt(pick(end, !isHome, BookDataEntity::getHomeBoxShootIn, BookDataEntity::getAwayBoxShootIn)));
		e.setBoxShootOut(toInt(pick(end, isHome, BookDataEntity::getHomeBoxShootOut, BookDataEntity::getAwayBoxShootOut)));
		e.setOppositeBoxShootOut(toInt(pick(end, !isHome, BookDataEntity::getHomeBoxShootOut, BookDataEntity::getAwayBoxShootOut)));
		e.setGoalPost(toInt(pick(end, isHome, BookDataEntity::getHomeGoalPost, BookDataEntity::getAwayGoalPost)));
		e.setOppositeGoalPost(toInt(pick(end, !isHome, BookDataEntity::getHomeGoalPost, BookDataEntity::getAwayGoalPost)));
		e.setGoalHead(toInt(pick(end, isHome, BookDataEntity::getHomeGoalHead, BookDataEntity::getAwayGoalHead)));
		e.setOppositeGoalHead(toInt(pick(end, !isHome, BookDataEntity::getHomeGoalHead, BookDataEntity::getAwayGoalHead)));
		e.setKeeperSave(toInt(pick(end, isHome, BookDataEntity::getHomeKeeperSave, BookDataEntity::getAwayKeeperSave)));
		e.setOppositeKeeperSave(toInt(pick(end, !isHome, BookDataEntity::getHomeKeeperSave, BookDataEntity::getAwayKeeperSave)));
		e.setFreeKick(toInt(pick(end, isHome, BookDataEntity::getHomeFreeKick, BookDataEntity::getAwayFreeKick)));
		e.setOppositeFreeKick(toInt(pick(end, !isHome, BookDataEntity::getHomeFreeKick, BookDataEntity::getAwayFreeKick)));
		e.setOffside(toInt(pick(end, isHome, BookDataEntity::getHomeOffSide, BookDataEntity::getAwayOffSide)));
		e.setOppositeOffside(toInt(pick(end, !isHome, BookDataEntity::getHomeOffSide, BookDataEntity::getAwayOffSide)));
		e.setFoul(toInt(pick(end, isHome, BookDataEntity::getHomeFoul, BookDataEntity::getAwayFoul)));
		e.setOppositeFoul(toInt(pick(end, !isHome, BookDataEntity::getHomeFoul, BookDataEntity::getAwayFoul)));
		e.setYellowCard(toInt(pick(end, isHome, BookDataEntity::getHomeYellowCard, BookDataEntity::getAwayYellowCard)));
		e.setOppositeYellowCard(toInt(pick(end, !isHome, BookDataEntity::getHomeYellowCard, BookDataEntity::getAwayYellowCard)));
		e.setRedCard(toInt(pick(end, isHome, BookDataEntity::getHomeRedCard, BookDataEntity::getAwayRedCard)));
		e.setOppositeRedCard(toInt(pick(end, !isHome, BookDataEntity::getHomeRedCard, BookDataEntity::getAwayRedCard)));
		e.setSlowIn(toInt(pick(end, isHome, BookDataEntity::getHomeSlowIn, BookDataEntity::getAwaySlowIn)));
		e.setOppositeSlowIn(toInt(pick(end, !isHome, BookDataEntity::getHomeSlowIn, BookDataEntity::getAwaySlowIn)));
		e.setBoxTouch(toInt(pick(end, isHome, BookDataEntity::getHomeBoxTouch, BookDataEntity::getAwayBoxTouch)));
		e.setOppositeBoxTouch(toInt(pick(end, !isHome, BookDataEntity::getHomeBoxTouch, BookDataEntity::getAwayBoxTouch)));
		Tri ownPassCount = parseTri(pick(end, isHome, BookDataEntity::getHomePassCount, BookDataEntity::getAwayPassCount));
		e.setPassCountSuccessRatio(ownPassCount.ratio);
		e.setPassCountSuccessCount(ownPassCount.success);
		e.setPassCountTryCount(ownPassCount.trys);
		Tri oppPassCount = parseTri(pick(end, !isHome, BookDataEntity::getHomePassCount, BookDataEntity::getAwayPassCount));
		e.setOppositePassCountSuccessRatio(oppPassCount.ratio);
		e.setOppositePassCountSuccessCount(oppPassCount.success);
		e.setOppositePassCountTryCount(oppPassCount.trys);
		Tri ownLongPassCount = parseTri(pick(end, isHome, BookDataEntity::getHomeLongPassCount, BookDataEntity::getAwayLongPassCount));
		e.setLongPassCountSuccessRatio(ownLongPassCount.ratio);
		e.setLongPassCountSuccessCount(ownLongPassCount.success);
		e.setLongPassCountTryCount(ownLongPassCount.trys);
		Tri oppLongPassCount = parseTri(pick(end, !isHome, BookDataEntity::getHomeLongPassCount, BookDataEntity::getAwayLongPassCount));
		e.setOppositeLongPassCountSuccessRatio(oppLongPassCount.ratio);
		e.setOppositeLongPassCountSuccessCount(oppLongPassCount.success);
		e.setOppositeLongPassCountTryCount(oppLongPassCount.trys);
		Tri ownFinalThirdPassCount = parseTri(pick(end, isHome, BookDataEntity::getHomeFinalThirdPassCount, BookDataEntity::getAwayFinalThirdPassCount));
		e.setFinalThirdPassCountSuccessRatio(ownFinalThirdPassCount.ratio);
		e.setFinalThirdPassCountSuccessCount(ownFinalThirdPassCount.success);
		e.setFinalThirdPassCountTryCount(ownFinalThirdPassCount.trys);
		Tri oppFinalThirdPassCount = parseTri(pick(end, !isHome, BookDataEntity::getHomeFinalThirdPassCount, BookDataEntity::getAwayFinalThirdPassCount));
		e.setOppositeFinalThirdPassCountSuccessRatio(oppFinalThirdPassCount.ratio);
		e.setOppositeFinalThirdPassCountSuccessCount(oppFinalThirdPassCount.success);
		e.setOppositeFinalThirdPassCountTryCount(oppFinalThirdPassCount.trys);
		Tri ownCrossCount = parseTri(pick(end, isHome, BookDataEntity::getHomeCrossCount, BookDataEntity::getAwayCrossCount));
		e.setCrossCountSuccessRatio(ownCrossCount.ratio);
		e.setCrossCountSuccessCount(ownCrossCount.success);
		e.setCrossCountTryCount(ownCrossCount.trys);
		Tri oppCrossCount = parseTri(pick(end, !isHome, BookDataEntity::getHomeCrossCount, BookDataEntity::getAwayCrossCount));
		e.setOppositeCrossCountSuccessRatio(oppCrossCount.ratio);
		e.setOppositeCrossCountSuccessCount(oppCrossCount.success);
		e.setOppositeCrossCountTryCount(oppCrossCount.trys);
		Tri ownTackleCount = parseTri(pick(end, isHome, BookDataEntity::getHomeTackleCount, BookDataEntity::getAwayTackleCount));
		e.setTackleCountSuccessRatio(ownTackleCount.ratio);
		e.setTackleCountSuccessCount(ownTackleCount.success);
		e.setTackleCountTryCount(ownTackleCount.trys);
		Tri oppTackleCount = parseTri(pick(end, !isHome, BookDataEntity::getHomeTackleCount, BookDataEntity::getAwayTackleCount));
		e.setOppositeTackleCountSuccessRatio(oppTackleCount.ratio);
		e.setOppositeTackleCountSuccessCount(oppTackleCount.success);
		e.setOppositeTackleCountTryCount(oppTackleCount.trys);
		e.setClearCount(toInt(pick(end, isHome, BookDataEntity::getHomeClearCount, BookDataEntity::getAwayClearCount)));
		e.setOppositeClearCount(toInt(pick(end, !isHome, BookDataEntity::getHomeClearCount, BookDataEntity::getAwayClearCount)));
		e.setDuelCount(toInt(pick(end, isHome, BookDataEntity::getHomeDuelCount, BookDataEntity::getAwayDuelCount)));
		e.setOppositeDuelCount(toInt(pick(end, !isHome, BookDataEntity::getHomeDuelCount, BookDataEntity::getAwayDuelCount)));
		e.setInterceptCount(toInt(pick(end, isHome, BookDataEntity::getHomeInterceptCount, BookDataEntity::getAwayInterceptCount)));
		e.setOppositeInterceptCount(toInt(pick(end, !isHome, BookDataEntity::getHomeInterceptCount, BookDataEntity::getAwayInterceptCount)));

		e.setWeather(trimOrNull(end.getWeather()));
		e.setTemperature(trimOrNull(end.getTemperature()));
		e.setHumid(trimOrNull(end.getHumid()));
		return e;
	}

	// ===== 値の変換 =====

	private static String pick(BookDataEntity row, boolean home,
			Function<BookDataEntity, String> homeGetter, Function<BookDataEntity, String> awayGetter) {
		return home ? homeGetter.apply(row) : awayGetter.apply(row);
	}

	private static String symbol(String result) {
		if ("WIN".equals(result)) {
			return "○";
		}
		if ("LOSE".equals(result)) {
			return "●";
		}
		return "△";
	}

	/** 数値変換（"%" は除去。空・変換不可は null） */
	static Double parseNumber(String value) {
		String s = trimOrNull(value);
		if (s == null) {
			return null;
		}
		s = s.replace("%", "").trim();
		if (s.isEmpty()) {
			return null;
		}
		try {
			double d = Double.parseDouble(s);
			return Double.isFinite(d) ? d : null;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	static Integer toInt(String value) {
		Double d = parseNumber(value);
		return (d == null) ? null : (int) Math.round(d);
	}

	static Double toDouble1(String value) {
		Double d = parseNumber(value);
		return (d == null) ? null : round(d, 1);
	}

	static Double toDouble2(String value) {
		Double d = parseNumber(value);
		return (d == null) ? null : round(d, 2);
	}

	private static double round(double v, int digits) {
		double scale = Math.pow(10, digits);
		return Math.round(v * scale) / scale;
	}

	/** 順位（数字だけ取り出す。無ければ null） */
	static Integer parseRank(String value) {
		String s = trimOrNull(value);
		if (s == null) {
			return null;
		}
		Matcher m = DIGITS.matcher(s);
		if (!m.find()) {
			return null;
		}
		try {
			return Integer.parseInt(m.group(1));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** "X% (成功/試行)" を分ける（"X%" だけなら成功率のみ。読めなければすべて null） */
	static Tri parseTri(String value) {
		String s = trimOrNull(value);
		if (s == null) {
			return Tri.EMPTY;
		}
		Matcher m = TRI_PATTERN.matcher(s);
		if (m.matches()) {
			return new Tri(round(Double.parseDouble(m.group(1)), 1),
					Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
		}
		Matcher p = PERCENT_ONLY_PATTERN.matcher(s);
		if (p.matches()) {
			return new Tri(round(Double.parseDouble(p.group(1)), 1), null, null);
		}
		return Tri.EMPTY;
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

	// ===== 共通 =====

	/**
	 * 通番の数値順で最後の試合終了（FIN）行を返す（無ければ null）。
	 * 取得エラー行は除く。PK 戦の行があっても FIN 行があれば対象にする。
	 */
	private static BookDataEntity findFinRow(List<BookDataEntity> rows) {
		if (rows == null) {
			return null;
		}
		List<BookDataEntity> sorted = new ArrayList<>();
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
		for (int i = sorted.size() - 1; i >= 0; i--) {
			if (BookMakersCommonConst.FIN.equals(trimOrNull(sorted.get(i).getTime()))) {
				return sorted.get(i);
			}
		}
		return null;
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
	 * 成功率・成功数・試行数（旧 RetentionData の代わり）。
	 */
	static final class Tri {
		static final Tri EMPTY = new Tri(null, null, null);
		final Double ratio;
		final Integer success;
		final Integer trys;

		Tri(Double ratio, Integer success, Integer trys) {
			this.ratio = ratio;
			this.success = success;
			this.trys = trys;
		}
	}
}
