package dev.application.analyze.bm_m031;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.application.analyze.bm_m029.BmM029CountryLeagueBean;
import dev.application.analyze.interf.AnalyzeEntityIF;
import dev.application.domain.repository.SurfaceOverviewRepository;
import dev.common.constant.BookMakersCommonConst;
import dev.common.entity.BookDataEntity;
import dev.common.exception.wrap.RootCauseWrapper;
import dev.common.logger.ManageLoggerComponent;
import dev.common.util.ExecuteMainUtil;

/**
 * # BM_M031 統計分析ロジック（SurfaceOverview 集計）
 *
 * <p>入力された全試合データ（CSV由来）を走査し、月次・チーム単位の
 * 概況トランザクション（SurfaceOverview）を集計します。</p>
 *
 * <h3>主な仕様</h3>
 * <ul>
 *   <li>集計キーは {@code country|league|gameYear|gameMonth|team}。</li>
 *   <li>同一キーが同一バッチ中に複数回現れた場合は、
 *       DBではなく {@code resultMap} にある途中値を再利用し、逐次加算。</li>
 *   <li>勝敗、得点、先制／逆転、連勝／連敗、クリーンシートなどを更新。</li>
 *   <li>ラウンド番号の抽出とフェーズ（序盤／中盤／終盤）カウントに対応。</li>
 * </ul>
 *
 * <p>※本クラスはスレッドセーフな累積を担保するため、キー毎にロックを行います。</p>
 *
 * @author shiraishitoshio
 */
@Component
public class SurfaceOverviewStat implements AnalyzeEntityIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = SurfaceOverviewStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = SurfaceOverviewStat.class.getSimpleName();

	/** 実行モード */
	private static final String EXEC_MODE = "BM_M031_SURFACE_OVERVIEW";

	/** 表示用・しきい値 */
	private static final int REQ_ROUNDS_FOR_LOSE_STREAK = 4; // “負け込み” は4連番4連敗
	private static final int REQ_FOR_CONSEC_LOSE_DISP = 1;  // “X連敗中” を出す最低本数（例: 3）
	private static final int REQ_FOR_CONSEC_WIN_DISP = 1;   // “X連勝中” を出す最低本数（例: 3）

	/** ロック用（キー= country|league|year|month|team） */
	private final ConcurrentHashMap<String, Object> lockMap = new ConcurrentHashMap<>();

	/** 国・リーグ別の総ラウンド数提供 */
	@Autowired
	private BmM029CountryLeagueBean bean;

	/** SurfaceOverview の CRUD */
	@Autowired
	private SurfaceOverviewRepository surfaceOverviewRepository;

	/** 例外ラッパ */
	@Autowired
	private RootCauseWrapper rootCauseWrapper;

	/** ロガー */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/** リーグ別総ラウンド数マップ（例: "Japan: J2" -> 42） */
	private Map<String, Integer> roundMap;

	/**
	 * {@inheritDoc}
	 *
	 * <p>全リーグ×全試合を走査して「同月×チーム」単位で集計します。
	 * 集計は一旦メモリ（resultMap）にため、最後に upsert します。</p>
	 *
	 * @param entities country,league をキー、試合（home-away）毎の BookDataEntity リストを値に持つネストマップ
	 */
	@Override
	public void calcStat(Map<String, Map<String, List<BookDataEntity>>> entities) {
		final String METHOD_NAME = "calcStat";
		manageLoggerComponent.init(EXEC_MODE, null);
		manageLoggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		roundMap = bean.getCountryLeagueRoundMap();

		// 同月×チームの途中結果を保持
		ConcurrentHashMap<String, SurfaceOverviewEntity> resultMap = new ConcurrentHashMap<>();

		// 全リーグ・国を走査
		for (Map.Entry<String, Map<String, List<BookDataEntity>>> entry : entities.entrySet()) {
			String[] data_category = ExecuteMainUtil.splitLeagueInfo(entry.getKey());
			String country = data_category[0];
			String league = data_category[1];

			for (Map.Entry<String, List<BookDataEntity>> sub : entry.getValue().entrySet()) {
				String[] teams = sub.getKey().split("-");
				String home = teams[0].trim();
				String away = teams[1].trim();
				List<BookDataEntity> rows = sub.getValue();

				if (rows == null || rows.isEmpty()) continue;

				// 同一キー（月×チーム）で resultMap の途中値を累積しつつ処理する
				basedMain(rows, country, league, home, away, resultMap);
			}
		}

		// upsert
		for (Map.Entry<String, SurfaceOverviewEntity> e : resultMap.entrySet()) {
			SurfaceOverviewEntity row = e.getValue();
			int result;
			if (row.getId() != null) {
				result = surfaceOverviewRepository.update(row);
				if (result != 1) {
					String messageCd = "更新エラー";
					rootCauseWrapper.throwUnexpectedRowCount(
							PROJECT_NAME, CLASS_NAME, "calcStat", messageCd, 1, result,
							String.format("key=%s, id=%s", e.getKey(), row.getId()));
				}
			} else {
				result = surfaceOverviewRepository.insert(row);
				if (result != 1) {
					String messageCd = "新規登録エラー";
					rootCauseWrapper.throwUnexpectedRowCount(
							PROJECT_NAME, CLASS_NAME, "calcStat", messageCd, 1, result,
							String.format("key=%s", e.getKey()));
				}
			}
		}

		manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		manageLoggerComponent.clear();
	}

	/**
	 * 各試合（home-vs-away）を入力に、該当試合の「同月×チーム」行へ累積加算します。
	 *
	 * <p>同一キーが同一バッチ中に複数回現れた場合、DBからではなく {@code resultMap} の途中値を再利用します。</p>
	 *
	 * @param entities 試合の全スナップショット（時系列）
	 * @param country  国
	 * @param league   リーグ
	 * @param home     ホームチーム名
	 * @param away     アウェイチーム名
	 * @param resultMap 「同月×チーム」途中結果の集積マップ
	 */
	private void basedMain(
			List<BookDataEntity> entities,
			String country, String league, String home, String away,
			ConcurrentHashMap<String, SurfaceOverviewEntity> resultMap) {

		BookDataEntity last = ExecuteMainUtil.getMaxSeqEntities(entities);
		manageLoggerComponent.debugInfoLog(PROJECT_NAME, CLASS_NAME, null, null, last.getFilePath());
		if (!BookMakersCommonConst.FIN.equals(last.getTime())) return;

		BookDataEntity mid  = ExecuteMainUtil.getHalfEntities(entities);
		BookDataEntity first = ExecuteMainUtil.getMinSeqEntities(entities);

		// スコア推移（連続重複を除外）
		List<String> scoreList = new ArrayList<>();
		String prev = null;
		for (BookDataEntity e : entities) {
			String s = e.getHomeScore() + "-" + e.getAwayScore();
			if (!s.equals(prev)) { scoreList.add(s); prev = s; }
		}

		// 試合の年月（record_time から抽出）
		String[] ymd = last.getRecordTime().split("-");
		String gameYear  = ymd[0];
		String gameMonth = ymd[1].replaceFirst("^0", "");

		// ラウンド
		Integer roundNo = tryGetRoundNo(last, roundMap.get(country + ": " + league));

		// ---- Home 側（キーは 年月を含む）----
		final String homeKey = String.join("|", country, league, gameYear, gameMonth, home);
		synchronized (getLock(homeKey)) {
			SurfaceOverviewEntity row =
					resultMap.getOrDefault(homeKey, loadOrNew(country, league, gameYear, gameMonth, home));

			// 必須メタ
			row.setCountry(country);
			row.setLeague(league);
			row.setGameYear(Integer.parseInt(gameYear));
			row.setGameMonth(Integer.parseInt(gameMonth));
			row.setTeam(home);

			// 累積
			row = setTeamMainData(last, row, country, league, home);
			row = setScoreData(last, mid, first, row, home);
			row = setEachScoreCountData(roundNo, row, country, league);
			row = setWinLoseDetailData(last, scoreList, row, home);
			row = firstWinAndConsecutiveLose(row, homeKey, roundNo);
			ensureNotNullCounters(row);

			resultMap.put(homeKey, row);
		}

		// ---- Away 側（キーは 年月を含む）----
		final String awayKey = String.join("|", country, league, gameYear, gameMonth, away);
		Integer roundNoAway = roundNo; // 同一試合のため同値
		synchronized (getLock(awayKey)) {
			SurfaceOverviewEntity row =
					resultMap.getOrDefault(awayKey, loadOrNew(country, league, gameYear, gameMonth, away));

			row.setCountry(country);
			row.setLeague(league);
			row.setGameYear(Integer.parseInt(gameYear));
			row.setGameMonth(Integer.parseInt(gameMonth));
			row.setTeam(away);

			row = setTeamMainData(last, row, country, league, away);
			row = setScoreData(last, mid, first, row, away);
			row = setEachScoreCountData(roundNoAway, row, country, league);
			row = setWinLoseDetailData(last, scoreList, row, away);
			row = firstWinAndConsecutiveLose(row, awayKey, roundNoAway);
			ensureNotNullCounters(row);

			resultMap.put(awayKey, row);
		}
	}

	/**
	 * 当月×チームの既存行を DB から取得し、なければ新規インスタンスを返す。
	 */
	private SurfaceOverviewEntity loadOrNew(String country, String league, String year, String month, String team) {
		List<SurfaceOverviewEntity> rows = surfaceOverviewRepository.select(country, league, year, month, team);
		return rows.isEmpty() ? new SurfaceOverviewEntity() : rows.get(0);
	}

	/**
	 * メイン（勝敗・勝点・無敗継続）を更新。
	 *
	 * <p>games は勝敗反映後の {@code win+lose+draw} で再計算します。</p>
	 */
	private SurfaceOverviewEntity setTeamMainData(BookDataEntity maxEntity,
			SurfaceOverviewEntity resultEntity, String country, String league, String team) {

		String homeTeam = maxEntity.getHomeTeamName();
		String awayTeam = maxEntity.getAwayTeamName();
		int homeScore = parseOrZero(maxEntity.getHomeScore());
		int awayScore = parseOrZero(maxEntity.getAwayScore());

		int winCount  = parseOrZero(resultEntity.getWin());
		int loseCount = parseOrZero(resultEntity.getLose());
		int drawCount = parseOrZero(resultEntity.getDraw());

		int befWin  = winCount;
		int befLose = loseCount;

		// 勝敗更新
		if (team.equals(homeTeam)) {
			if (homeScore > awayScore) winCount++;
			else if (homeScore < awayScore) loseCount++;
			else drawCount++;
		} else if (team.equals(awayTeam)) {
			if (awayScore > homeScore) winCount++;
			else if (awayScore < homeScore) loseCount++;
			else drawCount++;
		}

		// 反映
		resultEntity.setWin(String.valueOf(winCount));
		resultEntity.setLose(String.valueOf(loseCount));
		resultEntity.setDraw(String.valueOf(drawCount));
		resultEntity.setWinningPoints(String.valueOf(winCount * 3 + drawCount));

		int games = winCount + loseCount + drawCount;
		resultEntity.setGames(String.valueOf(games));

		// 無敗継続
		int unbeaten = parseOrZero(resultEntity.getUnbeatenStreakCount());
		boolean lostThisGame = (loseCount > befLose);
		int afUnbeaten = lostThisGame ? 0 : (unbeaten + 1);
		resultEntity.setUnbeatenStreakCount(String.valueOf(afUnbeaten));
		resultEntity.setUnbeatenStreakDisp(lostThisGame ? null : SurfaceOverviewConst.CONSECTIVE_UNBEATEN);

		// この試合の勝敗フラグ（後段で使用）
		resultEntity.setWinFlg(winCount > befWin);
		resultEntity.setLoseFlg(loseCount > befLose);

		return resultEntity;
	}

	/**
	 * スコア系（前半/後半/合計、クリーンシート、得点継続）＋ 失点系（前半/後半/合計、割合）を更新。
	 */
	private SurfaceOverviewEntity setScoreData(BookDataEntity maxEntity, BookDataEntity middleEntity,
	        BookDataEntity minEntity, SurfaceOverviewEntity resultEntity, String team) {

	    String homeTeam = maxEntity.getHomeTeamName();
	    String awayTeam = maxEntity.getAwayTeamName();

	    int homeMin = parseOrZero(minEntity.getHomeScore());
	    int homeMid = parseOrZero(middleEntity.getHomeScore());
	    int homeMax = parseOrZero(maxEntity.getHomeScore());
	    int awayMin = parseOrZero(minEntity.getAwayScore());
	    int awayMid = parseOrZero(middleEntity.getAwayScore());
	    int awayMax = parseOrZero(maxEntity.getAwayScore());

	    // 得点（既存）
	    String h1 = resultEntity.getHome1stHalfScore();
	    String h2 = resultEntity.getHome2ndHalfScore();
	    String hs = resultEntity.getHomeSumScore();
	    String hc = resultEntity.getHomeCleanSheet();

	    String a1 = resultEntity.getAway1stHalfScore();
	    String a2 = resultEntity.getAway2ndHalfScore();
	    String as = resultEntity.getAwaySumScore();
	    String ac = resultEntity.getAwayCleanSheet();

	    String fts = resultEntity.getFailToScoreGameCount();
	    int befFts = parseOrZero(fts);

	    // 追加：失点
	    String hl1 = resultEntity.getHome1stHalfLost();
	    String hl2 = resultEntity.getHome2ndHalfLost();
	    String hls = resultEntity.getHomeSumLost();

	    String al1 = resultEntity.getAway1stHalfLost();
	    String al2 = resultEntity.getAway2ndHalfLost();
	    String als = resultEntity.getAwaySumLost();

	    // 前後半の増分
	    int dh1 = homeMid - homeMin; // ホーム得点（前半増分）
	    int dh2 = homeMax - homeMid; // ホーム得点（後半増分）
	    int da1 = awayMid - awayMin; // アウェー得点（前半増分）
	    int da2 = awayMax - awayMid; // アウェー得点（後半増分）

	    if (team.equals(homeTeam)) {
	        // 得点
	        h1 = String.valueOf(parseOrZero(h1) + dh1);
	        h2 = String.valueOf(parseOrZero(h2) + dh2);
	        hs = String.valueOf(parseOrZero(hs) + dh1 + dh2);
	        if (awayMax == 0) hc = String.valueOf(parseOrZero(hc) + 1);
	        if (homeMax == 0) fts = String.valueOf(parseOrZero(fts) + 1);

	        // 失点（ホームの失点＝アウェー得点の増分）
	        hl1 = String.valueOf(parseOrZero(hl1) + da1);
	        hl2 = String.valueOf(parseOrZero(hl2) + da2);
	        hls = String.valueOf(parseOrZero(hls) + da1 + da2);

	    } else if (team.equals(awayTeam)) {
	        // 得点
	        a1 = String.valueOf(parseOrZero(a1) + da1);
	        a2 = String.valueOf(parseOrZero(a2) + da2);
	        as = String.valueOf(parseOrZero(as) + da1 + da2);
	        if (homeMax == 0) ac = String.valueOf(parseOrZero(ac) + 1);
	        if (awayMax == 0) fts = String.valueOf(parseOrZero(fts) + 1);

	        // 失点（アウェーの失点＝ホーム得点の増分）
	        al1 = String.valueOf(parseOrZero(al1) + dh1);
	        al2 = String.valueOf(parseOrZero(al2) + dh2);
	        als = String.valueOf(parseOrZero(als) + dh1 + dh2);
	    }

	    // 0埋め（nullセーフ）
	    if (h1 == null) h1 = "0";
	    if (h2 == null) h2 = "0";
	    if (hs == null) hs = "0";
	    if (a1 == null) a1 = "0";
	    if (a2 == null) a2 = "0";
	    if (as == null) as = "0";
	    if (hc == null) hc = "0";
	    if (ac == null) ac = "0";
	    if (fts == null) fts = "0";

	    if (hl1 == null) hl1 = "0";
	    if (hl2 == null) hl2 = "0";
	    if (hls == null) hls = "0";
	    if (al1 == null) al1 = "0";
	    if (al2 == null) al2 = "0";
	    if (als == null) als = "0";

	    // 反映：得点
	    resultEntity.setHome1stHalfScore(h1);
	    resultEntity.setHome2ndHalfScore(h2);
	    resultEntity.setHomeSumScore(hs);
	    resultEntity.setHome1stHalfScoreRatio(toPercent(parseOrZero(h1), parseOrZero(hs)));
	    resultEntity.setHome2ndHalfScoreRatio(toPercent(parseOrZero(h2), parseOrZero(hs)));
	    resultEntity.setHomeCleanSheet(hc);

	    resultEntity.setAway1stHalfScore(a1);
	    resultEntity.setAway2ndHalfScore(a2);
	    resultEntity.setAwaySumScore(as);
	    resultEntity.setAway1stHalfScoreRatio(toPercent(parseOrZero(a1), parseOrZero(as)));
	    resultEntity.setAway2ndHalfScoreRatio(toPercent(parseOrZero(a2), parseOrZero(as)));
	    resultEntity.setAwayCleanSheet(ac);

	    resultEntity.setFailToScoreGameCount(fts);

	    // 反映：失点
	    resultEntity.setHome1stHalfLost(hl1);
	    resultEntity.setHome2ndHalfLost(hl2);
	    resultEntity.setHomeSumLost(hls);
	    resultEntity.setHome1stHalfLostRatio(toPercent(parseOrZero(hl1), parseOrZero(hls)));
	    resultEntity.setHome2ndHalfLostRatio(toPercent(parseOrZero(hl2), parseOrZero(hls)));

	    resultEntity.setAway1stHalfLost(al1);
	    resultEntity.setAway2ndHalfLost(al2);
	    resultEntity.setAwaySumLost(als);
	    resultEntity.setAway1stHalfLostRatio(toPercent(parseOrZero(al1), parseOrZero(als)));
	    resultEntity.setAway2ndHalfLostRatio(toPercent(parseOrZero(al2), parseOrZero(als)));

	    // 得点継続（無得点が増えていなければ＋1）
	    int consec = parseOrZero(resultEntity.getConsecutiveScoreCount());
	    consec = (parseOrZero(fts) == befFts) ? (consec + 1) : 0;
	    resultEntity.setConsecutiveScoreCount(String.valueOf(consec));
	    resultEntity.setConsecutiveScoreCountDisp(consec >= 3 ? SurfaceOverviewConst.CONSECTIVE_SCORING : null);

	    return resultEntity;
	}

	/**
	 * フェーズ別（序盤/中盤/終盤）の勝敗カウントを更新。
	 * <p>ラウンドが取れない場合はスキップ。</p>
	 */
	private SurfaceOverviewEntity setEachScoreCountData(
			Integer roundNo,
			SurfaceOverviewEntity resultEntity,
			String country,
			String league) {

		if (roundNo == null) return resultEntity;

		if (resultEntity.getFirstWeekGameWinCount() == null)  resultEntity.setFirstWeekGameWinCount("0");
		if (resultEntity.getFirstWeekGameLostCount() == null) resultEntity.setFirstWeekGameLostCount("0");
		if (resultEntity.getMidWeekGameWinCount() == null)    resultEntity.setMidWeekGameWinCount("0");
		if (resultEntity.getMidWeekGameLostCount() == null)   resultEntity.setMidWeekGameLostCount("0");
		if (resultEntity.getLastWeekGameWinCount() == null)   resultEntity.setLastWeekGameWinCount("0");
		if (resultEntity.getLastWeekGameLostCount() == null)  resultEntity.setLastWeekGameLostCount("0");

		final String key = country + ": " + league;
		Integer seasonRoundsObj = getRound(roundMap, key);
		if (seasonRoundsObj == null) {
			final String METHOD_NAME = "setEachScoreCountData";
			String messageCd = "roundMap未存在のためskip";
			manageLoggerComponent.debugWarnLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, key);
			return resultEntity;
		}
		final int seasonRounds = seasonRoundsObj;
		final int firstEnd  = (int) Math.ceil(seasonRounds / 3.0);
		final int secondEnd = (int) Math.ceil(seasonRounds * 2.0 / 3.0);

		final boolean won  = resultEntity.isWinFlg();
		final boolean lost = resultEntity.isLoseFlg();

		int fW = parseOrZero(resultEntity.getFirstWeekGameWinCount());
		int fL = parseOrZero(resultEntity.getFirstWeekGameLostCount());
		int mW = parseOrZero(resultEntity.getMidWeekGameWinCount());
		int mL = parseOrZero(resultEntity.getMidWeekGameLostCount());
		int lW = parseOrZero(resultEntity.getLastWeekGameWinCount());
		int lL = parseOrZero(resultEntity.getLastWeekGameLostCount());

		if (roundNo <= firstEnd) {
			if (won) fW++; else if (lost) fL++;
		} else if (roundNo <= secondEnd) {
			if (won) mW++; else if (lost) mL++;
		} else {
			if (won) lW++; else if (lost) lL++;
		}

		resultEntity.setFirstWeekGameWinCount(String.valueOf(fW));
		resultEntity.setFirstWeekGameLostCount(String.valueOf(fL));
		resultEntity.setMidWeekGameWinCount(String.valueOf(mW));
		resultEntity.setMidWeekGameLostCount(String.valueOf(mL));
		resultEntity.setLastWeekGameWinCount(String.valueOf(lW));
		resultEntity.setLastWeekGameLostCount(String.valueOf(lL));

		return resultEntity;
	}

	/**
	 * ラウンド番号を gameTeamCategory から抽出（フォールバックあり）。
	 *
	 * @param maxEntity 最終スナップショット
	 * @param roundMax  そのリーグの想定最大ラウンド
	 * @return 抽出したラウンド（不明時 null）
	 */
	private Integer tryGetRoundNo(BookDataEntity maxEntity, Integer roundMax) {
		final String METHOD_NAME = "tryGetRoundNo";
		String cat = maxEntity.getGameTeamCategory();
		if (cat == null) return null;

		Integer n = parseRoundFromGameTeamCategory(cat);
		if (n != null) {
			if (roundMax != null && n > roundMax) {
				String messageCd = "ラウンド番号が異常値";
				manageLoggerComponent.debugWarnLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
						String.format("roundMax=%s, targetRound=%s, gameDataCategory=%s, csv=%s",
								roundMax, n, maxEntity.getGameTeamCategory(), maxEntity.getFilePath()));
			}
			return n;
		}

		// フォールバック（最後のハイフン以降の数字）
		String s = cat.trim().replace('－', '-').replace('–', '-').replace('—', '-').replace('：', ':');
		int idx = s.lastIndexOf('-');
		String tail = (idx >= 0) ? s.substring(idx + 1) : s;
		tail = toHalfWidthDigits(tail);
		String digits = tail.replaceAll("[^0-9]", "");
		if (!digits.isEmpty()) {
			int v = Integer.parseInt(digits);
			if (roundMax != null && v > roundMax) {
				String messageCd = "ラウンド番号が異常値";
				manageLoggerComponent.debugWarnLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
						String.format("roundMax=%s, targetRound=%s, gameDataCategory=%s, csv=%s",
								roundMax, v, maxEntity.getGameTeamCategory(), maxEntity.getFilePath()));
			}
			return v;
		}
		return null;
	}

	/** 全角数字を半角へ */
	private static String toHalfWidthDigits(String in) {
		StringBuilder sb = new StringBuilder(in.length());
		for (char ch : in.toCharArray()) {
			if (ch >= '０' && ch <= '９') sb.append((char) ('0' + (ch - '０')));
			else sb.append(ch);
		}
		return sb.toString();
	}

	/**
	 * 勝利/敗北詳細（先制、逆転勝利/敗北の内訳など：ホーム視点）を更新。
	 */
	private SurfaceOverviewEntity setWinLoseDetailData(BookDataEntity maxEntity, List<String> scoreList,
			SurfaceOverviewEntity resultEntity, String team) {
		String homeTeam = maxEntity.getHomeTeamName();
		String awayTeam = maxEntity.getAwayTeamName();
		int homeScore = Integer.parseInt(maxEntity.getHomeScore());
		int awayScore = Integer.parseInt(maxEntity.getAwayScore());

		String homeWinCount  = resultEntity.getHomeWinCount();
		String homeLoseCount = resultEntity.getHomeLoseCount();
		String awayWinCount  = resultEntity.getAwayWinCount();
		String awayLoseCount = resultEntity.getAwayLoseCount();

		if (team.equals(homeTeam)) {
			if (homeScore > awayScore) {
				homeWinCount = String.valueOf(parseOrZero(homeWinCount) + 1);
			} else if (homeScore < awayScore) {
				homeLoseCount = String.valueOf(parseOrZero(homeLoseCount) + 1);
			} else {
				if (homeWinCount == null || homeWinCount.isBlank()) homeWinCount = "0";
				if (homeLoseCount == null || homeLoseCount.isBlank()) homeLoseCount = "0";
			}
		} else if (team.equals(awayTeam)) {
			if (homeScore < awayScore) {
				awayWinCount = String.valueOf(parseOrZero(awayWinCount) + 1);
			} else if (homeScore > awayScore) {
				awayLoseCount = String.valueOf(parseOrZero(awayLoseCount) + 1);
			} else {
				if (awayWinCount == null || awayWinCount.isBlank()) awayWinCount = "0";
				if (awayLoseCount == null || awayLoseCount.isBlank()) awayLoseCount = "0";
			}
		}

		resultEntity.setHomeWinCount(homeWinCount);
		resultEntity.setHomeLoseCount(homeLoseCount);
		resultEntity.setAwayWinCount(awayWinCount);
		resultEntity.setAwayLoseCount(awayLoseCount);

		if (team.equals(homeTeam)) {
			resultEntity = updateHomeLeadTrailStats(scoreList, resultEntity);
		} else if (team.equals(awayTeam)) {
			resultEntity = updateAwayLeadTrailStats(scoreList, resultEntity);
		}

		updateAdversityDisps(resultEntity);
		return resultEntity;
	}

	/**
	 * スコア推移からホーム視点で先制・逆転勝利/敗北の内訳を累積。
	 */
	private SurfaceOverviewEntity updateHomeLeadTrailStats(
			List<String> scoreList, SurfaceOverviewEntity resultEntity) {

		if (scoreList == null || scoreList.isEmpty()) return resultEntity;

		boolean has10 = false, has20 = false, has01 = false, has02 = false;
		boolean homeEverLed = false, homeEverTrailed = false;

		String firstScorer = "NONE"; // HOME/AWAY/NONE
		int[] prev = null;

		for (String sc : scoreList) {
			int[] cur = parseScorePair(sc);
			if (cur == null) continue;

			if (cur[0] == 1 && cur[1] == 0) has10 = true;
			if (cur[0] == 2 && cur[1] == 0) has20 = true;
			if (cur[0] == 0 && cur[1] == 1) has01 = true;
			if (cur[0] == 0 && cur[1] == 2) has02 = true;

			if (cur[0] > cur[1]) homeEverLed = true;
			if (cur[0] < cur[1]) homeEverTrailed = true;

			if (prev != null && "NONE".equals(firstScorer)) {
				int dh = cur[0] - prev[0];
				int da = cur[1] - prev[1];
				if (dh > 0 && da == 0) firstScorer = "HOME";
				else if (da > 0 && dh == 0) firstScorer = "AWAY";
			}
			prev = cur;
		}

		if ("NONE".equals(firstScorer)) {
			for (String sc : scoreList) {
				int[] p = parseScorePair(sc);
				if (p == null) continue;
				if (p[0] != p[1]) { firstScorer = (p[0] > p[1]) ? "HOME" : "AWAY"; break; }
			}
		}

		int finalH = 0, finalA = 0;
		for (int i = scoreList.size() - 1; i >= 0; i--) {
			int[] last = parseScorePair(scoreList.get(i));
			if (last == null) continue;
			finalH = last[0]; finalA = last[1];
			break;
		}

		int homeFirst     = parseOrZero(resultEntity.getHomeFirstGoalCount());
		int homeWinBehind = parseOrZero(resultEntity.getHomeWinBehindCount());
		int homeLoseBehind = parseOrZero(resultEntity.getHomeLoseBehindCount());
		int homeWinB01 = parseOrZero(resultEntity.getHomeWinBehind0vs1Count());
		int homeLoseB10 = parseOrZero(resultEntity.getHomeLoseBehind1vs0Count());
		int homeWinB02 = parseOrZero(resultEntity.getHomeWinBehind0vs2Count());
		int homeLoseB20 = parseOrZero(resultEntity.getHomeLoseBehind2vs0Count());
		int homeWinBOther = parseOrZero(resultEntity.getHomeWinBehindOtherCount());
		int homeLoseBOther = parseOrZero(resultEntity.getHomeLoseBehindOtherCount());

		if ("HOME".equals(firstScorer)) homeFirst++;

		if (finalH > finalA && homeEverTrailed) {
			homeWinBehind++;
			if (has02) homeWinB02++;
			else if (has01) homeWinB01++;
			else homeWinBOther++;
		}

		if (finalH < finalA && homeEverLed) {
			homeLoseBehind++;
			if (has20) homeLoseB20++;
			else if (has10) homeLoseB10++;
			else homeLoseBOther++;
		}

		resultEntity.setHomeFirstGoalCount(String.valueOf(homeFirst));
		resultEntity.setHomeWinBehindCount(String.valueOf(homeWinBehind));
		resultEntity.setHomeLoseBehindCount(String.valueOf(homeLoseBehind));
		resultEntity.setHomeWinBehind0vs1Count(String.valueOf(homeWinB01));
		resultEntity.setHomeLoseBehind1vs0Count(String.valueOf(homeLoseB10));
		resultEntity.setHomeWinBehind0vs2Count(String.valueOf(homeWinB02));
		resultEntity.setHomeLoseBehind2vs0Count(String.valueOf(homeLoseB20));
		resultEntity.setHomeWinBehindOtherCount(String.valueOf(homeWinBOther));
		resultEntity.setHomeLoseBehindOtherCount(String.valueOf(homeLoseBOther));

		return resultEntity;
	}

	/**
	 * スコア推移からアウェイ視点で先制・逆転勝利/敗北の内訳を累積。
	 */
	private SurfaceOverviewEntity updateAwayLeadTrailStats(
			List<String> scoreList, SurfaceOverviewEntity resultEntity) {

		if (scoreList == null || scoreList.isEmpty()) return resultEntity;

		boolean has10 = false, has20 = false, has01 = false, has02 = false;
		boolean awayEverLed = false, awayEverTrailed = false;

		String firstScorer = "NONE"; // HOME/AWAY/NONE
		int[] prev = null;

		for (String sc : scoreList) {
			int[] cur = parseScorePair(sc);
			if (cur == null) continue;

			if (cur[0] == 1 && cur[1] == 0) has10 = true; // ホーム先制
			if (cur[0] == 2 && cur[1] == 0) has20 = true;
			if (cur[0] == 0 && cur[1] == 1) has01 = true; // アウェー先制
			if (cur[0] == 0 && cur[1] == 2) has02 = true;

			if (cur[1] > cur[0]) awayEverLed = true;
			if (cur[1] < cur[0]) awayEverTrailed = true;

			if (prev != null && "NONE".equals(firstScorer)) {
				int dh = cur[0] - prev[0];
				int da = cur[1] - prev[1];
				if (da > 0 && dh == 0) firstScorer = "AWAY";
				else if (dh > 0 && da == 0) firstScorer = "HOME";
			}
			prev = cur;
		}

		if ("NONE".equals(firstScorer)) {
			for (String sc : scoreList) {
				int[] p = parseScorePair(sc);
				if (p == null) continue;
				if (p[0] != p[1]) { firstScorer = (p[1] > p[0]) ? "AWAY" : "HOME"; break; }
			}
		}

		int finalH = 0, finalA = 0;
		for (int i = scoreList.size() - 1; i >= 0; i--) {
			int[] last = parseScorePair(scoreList.get(i));
			if (last == null) continue;
			finalH = last[0]; finalA = last[1];
			break;
		}

		int awayFirst      = parseOrZero(resultEntity.getAwayFirstGoalCount());
		int awayWinBehind  = parseOrZero(resultEntity.getAwayWinBehindCount());
		int awayLoseBehind = parseOrZero(resultEntity.getAwayLoseBehindCount());
		int awayWinB10     = parseOrZero(resultEntity.getAwayWinBehind1vs0Count());
		int awayLoseB01    = parseOrZero(resultEntity.getAwayLoseBehind0vs1Count());
		int awayWinB20     = parseOrZero(resultEntity.getAwayWinBehind2vs0Count());
		int awayLoseB02    = parseOrZero(resultEntity.getAwayLoseBehind0vs2Count());
		int awayWinBOther  = parseOrZero(resultEntity.getAwayWinBehindOtherCount());
		int awayLoseBOther = parseOrZero(resultEntity.getAwayLoseBehindOtherCount());

		if ("AWAY".equals(firstScorer)) awayFirst++;

		if (finalA > finalH && awayEverTrailed) {
			awayWinBehind++;
			if (has20) awayWinB20++;
			else if (has10) awayWinB10++;
			else awayWinBOther++;
		}

		if (finalA < finalH && awayEverLed) {
			awayLoseBehind++;
			if (has02) awayLoseB02++;
			else if (has01) awayLoseB01++;
			else awayLoseBOther++;
		}

		resultEntity.setAwayFirstGoalCount(String.valueOf(awayFirst));
		resultEntity.setAwayWinBehindCount(String.valueOf(awayWinBehind));
		resultEntity.setAwayLoseBehindCount(String.valueOf(awayLoseBehind));
		resultEntity.setAwayWinBehind1vs0Count(String.valueOf(awayWinB10));
		resultEntity.setAwayLoseBehind0vs1Count(String.valueOf(awayLoseB01));
		resultEntity.setAwayWinBehind2vs0Count(String.valueOf(awayWinB20));
		resultEntity.setAwayLoseBehind0vs2Count(String.valueOf(awayLoseB02));
		resultEntity.setAwayWinBehindOtherCount(String.valueOf(awayWinBOther));
		resultEntity.setAwayLoseBehindOtherCount(String.valueOf(awayLoseBOther));

		return resultEntity;
	}

	/**
	 * 初勝利・連勝/連敗・負け込み表示を更新。
	 * <p>全月の roundConc をマージし、今回ラウンドを反映した上で streak を再計算します。</p>
	 */
	private SurfaceOverviewEntity firstWinAndConsecutiveLose(
			SurfaceOverviewEntity e, String teamKey, Integer roundNo) {

		e.setFirstWinDisp(null);
		if ("0".equals(e.getWin()) && !"0".equals(e.getGames())) {
			e.setFirstWinDisp(SurfaceOverviewConst.FIRST_WIN_MOTIVATION);
		}
		if (roundNo == null) return e;

		final boolean winThis  = e.isWinFlg();
		final boolean loseThis = e.isLoseFlg();

		RoundHistory hist = loadMergedRoundHistory(e.getCountry(), e.getLeague(), e.getTeam());

		// 今回ラウンドで上書き
		hist.all.add(roundNo);
		hist.win.remove(roundNo);
		hist.lose.remove(roundNo);
		if (winThis)      hist.win.add(roundNo);
		else if (loseThis) hist.lose.add(roundNo);

		Integer end = hist.all.isEmpty() ? null : hist.all.last();

		int loseStreak = 0;
		int winStreak  = 0;
		if (end != null) {
			if (hist.lose.contains(end)) loseStreak = countConsecutiveEndingAt(hist.lose, end);
			if (hist.win.contains(end))  winStreak  = countConsecutiveEndingAt(hist.win, end);
		}

		e.setConsecutiveLoseCount(String.valueOf(loseStreak));
		e.setConsecutiveLoseDisp(loseStreak >= REQ_FOR_CONSEC_LOSE_DISP
				? (loseStreak + SurfaceOverviewConst.CONSECTIVE_LOSE) : null);
		e.setLoseStreakDisp(loseStreak >= REQ_ROUNDS_FOR_LOSE_STREAK
				? SurfaceOverviewConst.LOSE_CONSECUTIVE : null);
		e.setConsecutiveWinDisp(winStreak >= REQ_FOR_CONSEC_WIN_DISP
				? (winStreak + SurfaceOverviewConst.CONSECTIVE_WIN) : null);

		// この行の round_conc も更新
		e.setRoundConc(toRoundConc(hist));
		return e;
	}

	/** 勝率に基づく「逆境」表示（ホーム/アウェイ）を更新。 */
	private void updateAdversityDisps(SurfaceOverviewEntity e) {
		final double THRESHOLD = 0.30;

		int homeWins    = parseOrZero(e.getHomeWinCount());
		int homeCFBWins = parseOrZero(e.getHomeWinBehindCount());
		e.setHomeAdversityDisp(
				isRatioAtLeast(homeCFBWins, homeWins, THRESHOLD) ? SurfaceOverviewConst.HOME_ADVERSITY : null);

		int awayWins    = parseOrZero(e.getAwayWinCount());
		int awayCFBWins = parseOrZero(e.getAwayWinBehindCount());
		e.setAwayAdversityDisp(
				isRatioAtLeast(awayCFBWins, awayWins, THRESHOLD) ? SurfaceOverviewConst.AWAY_ADVERSITY : null);
	}

	/** null安全の 0 埋め（NOT NULL 対策、表示安定化）。 */
	private static void ensureNotNullCounters(SurfaceOverviewEntity e) {
		if (e.getHomeWinCount() == null) e.setHomeWinCount("0");
		if (e.getHomeLoseCount() == null) e.setHomeLoseCount("0");
		if (e.getHomeFirstGoalCount() == null) e.setHomeFirstGoalCount("0");
		if (e.getHomeWinBehindCount() == null) e.setHomeWinBehindCount("0");
		if (e.getHomeLoseBehindCount() == null) e.setHomeLoseBehindCount("0");
		if (e.getHomeWinBehind0vs1Count() == null) e.setHomeWinBehind0vs1Count("0");
		if (e.getHomeLoseBehind1vs0Count() == null) e.setHomeLoseBehind1vs0Count("0");
		if (e.getHomeWinBehind0vs2Count() == null) e.setHomeWinBehind0vs2Count("0");
		if (e.getHomeLoseBehind2vs0Count() == null) e.setHomeLoseBehind2vs0Count("0");
		if (e.getHomeWinBehindOtherCount() == null) e.setHomeWinBehindOtherCount("0");
		if (e.getHomeLoseBehindOtherCount() == null) e.setHomeLoseBehindOtherCount("0");

		if (e.getAwayWinCount() == null) e.setAwayWinCount("0");
		if (e.getAwayLoseCount() == null) e.setAwayLoseCount("0");
		if (e.getAwayFirstGoalCount() == null) e.setAwayFirstGoalCount("0");
		if (e.getAwayWinBehindCount() == null) e.setAwayWinBehindCount("0");
		if (e.getAwayLoseBehindCount() == null) e.setAwayLoseBehindCount("0");
		if (e.getAwayWinBehind1vs0Count() == null) e.setAwayWinBehind1vs0Count("0");
		if (e.getAwayLoseBehind0vs1Count() == null) e.setAwayLoseBehind0vs1Count("0");
		if (e.getAwayWinBehind2vs0Count() == null) e.setAwayWinBehind2vs0Count("0");
		if (e.getAwayLoseBehind0vs2Count() == null) e.setAwayLoseBehind0vs2Count("0");
		if (e.getAwayWinBehindOtherCount() == null) e.setAwayWinBehindOtherCount("0");
		if (e.getAwayLoseBehindOtherCount() == null) e.setAwayLoseBehindOtherCount("0");

		if (e.getWin() == null) e.setWin("0");
		if (e.getLose() == null) e.setLose("0");
		if (e.getDraw() == null) e.setDraw("0");
		if (e.getGames() == null) e.setGames("0");
		if (e.getWinningPoints() == null) e.setWinningPoints("0");
		if (e.getFailToScoreGameCount() == null) e.setFailToScoreGameCount("0");
		if (e.getUnbeatenStreakCount() == null) e.setUnbeatenStreakCount("0");
		if (e.getFirstWeekGameWinCount() == null) e.setFirstWeekGameWinCount("0");
		if (e.getFirstWeekGameLostCount() == null) e.setFirstWeekGameLostCount("0");
		if (e.getMidWeekGameWinCount() == null) e.setMidWeekGameWinCount("0");
		if (e.getMidWeekGameLostCount() == null) e.setMidWeekGameLostCount("0");
		if (e.getLastWeekGameWinCount() == null) e.setLastWeekGameWinCount("0");
		if (e.getLastWeekGameLostCount() == null) e.setLastWeekGameLostCount("0");
		if (e.getConsecutiveScoreCount() == null) e.setConsecutiveScoreCount("0");
		if (e.getConsecutiveLoseCount() == null) e.setConsecutiveLoseCount("0");
	}

	/** 同一チーム（country, league, team）の全行から roundConc をマージ。 */
	private RoundHistory loadMergedRoundHistory(String country, String league, String team) {
		RoundHistory merged = new RoundHistory();
		List<SurfaceOverviewEntity> rows = surfaceOverviewRepository.selectAllMonthsByTeam(country, league, team);
		for (SurfaceOverviewEntity row : rows) {
			RoundHistory h = parseRoundConc(row.getRoundConc());
			merged.all.addAll(h.all);
			merged.win.addAll(h.win);
			merged.lose.addAll(h.lose);
		}
		return merged;
	}

	/** null/空/非数は 0 として扱う。 */
	private static int parseOrZero(String s) {
		if (s == null || s.isBlank()) return 0;
		try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return 0; }
	}

	/** "1-0", "1:0", "1 – 0" 等を [home, away] に変換（失敗時 null）。 */
	private static int[] parseScorePair(String s) {
		if (s == null) return null;
		String normalized = s.trim()
				.replaceAll("\\s", "")
				.replace('–', '-') // en dash
				.replace('—', '-') // em dash
				.replace(':', '-');
		String[] parts = normalized.split("-");
		if (parts.length != 2) return null;
		try {
			int h = Integer.parseInt(parts[0]);
			int a = Integer.parseInt(parts[1]);
			if (h < 0 || a < 0) return null;
			return new int[] { h, a };
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** 比率 >= threshold か。分母0は false。 */
	private static boolean isRatioAtLeast(int num, int denom, double threshold) {
		if (denom <= 0) return false;
		return (double) num / (double) denom >= threshold;
	}

	/** 割合を NN% に整形（四捨五入）。分母0なら "0%"。 */
	private static String toPercent(int num, int denom) {
		if (denom <= 0) return "0%";
		long pct = Math.round((num * 100.0) / denom);
		return pct + "%";
	}

	/**
	 * gameTeamCategory から「ラウンド N」を抽出（"ラウンド N" / "Round N" 対応）。
	 * 見つからない場合は null。
	 */
	private static Integer parseRoundFromGameTeamCategory(String s) {
		if (s == null) return null;
		String t = s.trim()
				.replace('\u00A0', ' ')
				.replace('－', '-')
				.replace('–', '-')
				.replace('—', '-')
				.replace('：', ':');
		t = toHalfWidthDigits(t);
		Matcher m = Pattern.compile("(?:ラウンド|Round)\\s*(\\d+)").matcher(t);
		if (m.find()) {
			try { return Integer.valueOf(m.group(1)); } catch (NumberFormatException ignore) {}
		}
		return null;
	}

	// --- RoundHistory と roundConc 変換ヘルパ ---

	private static final class RoundHistory {
		final java.util.TreeSet<Integer> all  = new java.util.TreeSet<>();
		final java.util.TreeSet<Integer> win  = new java.util.TreeSet<>();
		final java.util.TreeSet<Integer> lose = new java.util.TreeSet<>();
	}

	private static RoundHistory parseRoundConc(String s) {
		RoundHistory h = new RoundHistory();
		if (s == null || s.isBlank()) return h;
		String[] parts = s.split("\\|");
		for (String part : parts) {
			String[] kv = part.split("=", 2);
			if (kv.length != 2) continue;
			String k = kv[0].trim();
			String v = kv[1].trim();
			if (!v.isEmpty()) {
				for (String t : v.split(",")) {
					t = t.trim();
					if (t.matches("\\d+")) {
						int n = Integer.parseInt(t);
						switch (k) {
							case "A": h.all.add(n);  break;
							case "W": h.win.add(n);  break;
							case "L": h.lose.add(n); break;
						}
					}
				}
			}
		}
		return h;
	}

	/** RoundHistory を round_conc 文字列（A=..|W=..|L=..）に変換。 */
	private static String toRoundConc(RoundHistory h) {
		String A = h.all.stream().map(String::valueOf).reduce((x, y) -> x + "," + y).orElse("");
		String W = h.win.stream().map(String::valueOf).reduce((x, y) -> x + "," + y).orElse("");
		String L = h.lose.stream().map(String::valueOf).reduce((x, y) -> x + "," + y).orElse("");
		return "A=" + A + "|W=" + W + "|L=" + L;
	}

	/** 末尾が end の連番本数（... end-2, end-1, end）。 */
	private static int countConsecutiveEndingAt(java.util.NavigableSet<Integer> set, int end) {
		if (set.isEmpty()) return 0;
		int cnt = 0;
		for (int r = end; r >= 0 && set.contains(r); r--) cnt++;
		return cnt;
	}

	/** ラウンド総数を取得（見つからない場合は null）。 */
	private static Integer getRound(Map<String, Integer> roundMap, String key) {
		return roundMap.get(key);
	}

	/** ロックオブジェクトをキー毎に用意。 */
	private Object getLock(String key) {
		return lockMap.computeIfAbsent(key, k -> new Object());
	}
}
