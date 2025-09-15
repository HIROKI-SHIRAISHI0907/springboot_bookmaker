package dev.application.analyze.bm_m031;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.application.analyze.bm_m029.BmM029CountryLeagueBean;
import dev.application.analyze.interf.AnalyzeEntityIF;
import dev.application.domain.repository.SurfaceOverviewRepository;
import dev.common.constant.BookMakersCommonConst;
import dev.common.entity.BookDataEntity;
import dev.common.exception.wrap.RootCauseWrapper;
import dev.common.logger.ManageLoggerComponent;
import dev.common.util.ExecuteMainUtil;

/**
 * BM_M031統計分析ロジック
 * @author shiraishitoshio
 *
 */
@Component
@Transactional
public class SurfaceOverviewStat implements AnalyzeEntityIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = SurfaceOverviewStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = SurfaceOverviewStat.class.getSimpleName();

	/** 実行モード */
	private static final String EXEC_MODE = "BM_M031_SURFACE_OVERVIEW";

	/** クラススコープに以下を追加 */
	private final ConcurrentHashMap<String, Object> lockMap = new ConcurrentHashMap<>();

	/** BmM029CountryLeagueBeanレポジトリクラス */
	@Autowired
	private BmM029CountryLeagueBean bean;

	/** SurfaceOverviewRepositoryレポジトリクラス */
	@Autowired
	private SurfaceOverviewRepository surfaceOverviewRepository;

	/** ログ管理ラッパー*/
	@Autowired
	private RootCauseWrapper rootCauseWrapper;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/** ラウンドマップ */
	private Map<String, Integer> roundMap;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void calcStat(Map<String, Map<String, List<BookDataEntity>>> entities) {
		final String METHOD_NAME = "calcStat";
		this.manageLoggerComponent.init(EXEC_MODE, null);
		this.manageLoggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		roundMap = bean.getCountryLeagueRoundMap();

		// 全リーグ・国を走査
		ConcurrentHashMap<String, SurfaceOverviewEntity> resultMap = new ConcurrentHashMap<>();
		for (Map.Entry<String, Map<String, List<BookDataEntity>>> entry : entities.entrySet()) {
			String[] data_category = ExecuteMainUtil.splitLeagueInfo(entry.getKey());
			String country = data_category[0];
			String league = data_category[1];
			Map<String, List<BookDataEntity>> entrySub = entry.getValue();
			for (Map.Entry<String, List<BookDataEntity>> entitySub : entrySub.entrySet()) {
				String[] teams = entitySub.getKey().split("-");
				String home = teams[0];
				String away = teams[1];
				List<BookDataEntity> entityList = entitySub.getValue();
				// null や空リストはスキップ
				if (entityList == null || entityList.isEmpty())
					continue;

				resultMap = basedMain(entityList, country, league, home, away, resultMap);
				if (resultMap == null) {
					continue;
				}
			}
		}

		for (Map.Entry<String, SurfaceOverviewEntity> data : resultMap.entrySet()) {
			int result = this.surfaceOverviewRepository.insert(data.getValue());
			if (result != 1) {
				String messageCd = "新規登録エラー";
				this.rootCauseWrapper.throwUnexpectedRowCount(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						messageCd,
						1, result,
						String.format("key=%s", data.getKey()));
			}
		}

		this.manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		this.manageLoggerComponent.clear();
	}

	/**
	 * 処理メインロジック
	 * @param entities エンティティ
	 * @param country 国
	 * @param league リーグ
	 * @param home ホーム
	 * @param away アウェー
	 * @param resultMap 保存データマップ
	 * @return
	 */
	private ConcurrentHashMap<String, SurfaceOverviewEntity> basedMain(
			List<BookDataEntity> entities,
			String country, String league, String home, String away,
			ConcurrentHashMap<String, SurfaceOverviewEntity> resultMap) {
		BookDataEntity returnMaxEntity = ExecuteMainUtil.getMaxSeqEntities(entities);
		if (!BookMakersCommonConst.FIN.equals(returnMaxEntity.getTime())) {
			return null;
		}
		BookDataEntity returnMiddleEntity = ExecuteMainUtil.getHalfEntities(entities);
		BookDataEntity returnMinEntity = ExecuteMainUtil.getMinSeqEntities(entities);
		List<String> scoreList = new ArrayList<String>();
		String prev = null;
		for (BookDataEntity entity : entities) {
			String s = entity.getHomeScore() + "-" + entity.getAwayScore();
			if (!s.equals(prev)) {
		        scoreList.add(s);
		        prev = s;
		    }
		}
		// 試合年,月(2025-02-06 07:25:58などの形式から月を算出)
		String gameTime = returnMaxEntity.getRecordTime();
		String[] split = gameTime.split("-");
		String gameYear = split[0];
		String gameMonth = split[1].replaceFirst("^0", "");
		// 非同期
		String homeKey = String.join("|", country, league, home);
		synchronized (getLock(homeKey)) {
			// データ取得
			List<SurfaceOverviewEntity> result = this.surfaceOverviewRepository.select(
					country, league, gameYear, gameMonth, home);
			SurfaceOverviewEntity resultHomeEntity = new SurfaceOverviewEntity();
			if (!result.isEmpty()) {
				resultHomeEntity = result.get(0);
			}
			// 国
			resultHomeEntity.setCountry(country);
			// リーグ
			resultHomeEntity.setLeague(league);
			// 試合年,月
			resultHomeEntity.setGameYear(gameYear);
			resultHomeEntity.setGameMonth(gameMonth);
			// チーム
			resultHomeEntity.setTeam(home);
			resultHomeEntity = setTeamMainData(returnMaxEntity, resultHomeEntity, country, league, home);
			resultHomeEntity = setScoreData(returnMaxEntity, returnMiddleEntity,
					returnMinEntity, resultHomeEntity, home);
			resultHomeEntity = setEachScoreCountData(returnMaxEntity, resultHomeEntity);
			resultHomeEntity = setWinLoseDetailData(returnMaxEntity, scoreList, resultHomeEntity, home);
			resultHomeEntity = firstWinAndConsecutiveLose(resultHomeEntity);
			ensureNotNullCounters(resultHomeEntity);
			resultMap.put(homeKey, resultHomeEntity);
		}
		String awayKey = String.join("|", country, league, away);
		synchronized (getLock(awayKey)) {
			// データ取得
			List<SurfaceOverviewEntity> result = this.surfaceOverviewRepository.select(
					country, league, gameYear, gameMonth, away);
			SurfaceOverviewEntity resultAwayEntity = new SurfaceOverviewEntity();
			if (!result.isEmpty()) {
				resultAwayEntity = result.get(0);
			}
			// 国
			resultAwayEntity.setCountry(country);
			// リーグ
			resultAwayEntity.setLeague(league);
			// 試合年,月
			resultAwayEntity.setGameYear(gameYear);
			resultAwayEntity.setGameMonth(gameMonth);
			// チーム
			resultAwayEntity.setTeam(away);
			resultAwayEntity = setTeamMainData(returnMaxEntity, resultAwayEntity, country, league, away);
			resultAwayEntity = setScoreData(returnMaxEntity, returnMiddleEntity,
					returnMinEntity, resultAwayEntity, away);
			resultAwayEntity = setEachScoreCountData(returnMaxEntity, resultAwayEntity);
			resultAwayEntity = setWinLoseDetailData(returnMaxEntity, scoreList, resultAwayEntity, away);
			resultAwayEntity = firstWinAndConsecutiveLose(resultAwayEntity);
			ensureNotNullCounters(resultAwayEntity);
			resultMap.put(awayKey, resultAwayEntity);
		}

		return resultMap;
	}

	/**
	 * メインデータ(国〜勝ち点,無敗記録)を設定する(teamが同一のものが来る場合はlockする)
	 * @param maxEntity
	 * @param resultEntity
	 * @param country
	 * @param league
	 * @return
	 */
	// 1) games を更新後の値でセット
	private SurfaceOverviewEntity setTeamMainData(BookDataEntity maxEntity,
	        SurfaceOverviewEntity resultEntity, String country, String league, String team) {

	    String homeTeam = maxEntity.getHomeTeamName();
	    String awayTeam = maxEntity.getAwayTeamName();
	    int homeScore = parseOrZero(maxEntity.getHomeScore());
	    int awayScore = parseOrZero(maxEntity.getAwayScore());

	    int winCount  = parseOrZero(resultEntity.getWin());
	    int loseCount = parseOrZero(resultEntity.getLose());
	    int drawCount = parseOrZero(resultEntity.getDraw());

	    // 前回値（フラグ用）
	    int befWinCount  = winCount;
	    int befLoseCount = loseCount;

	    // 勝敗更新
	    if (team.equals(homeTeam)) {
	        if (homeScore > awayScore)       winCount++;
	        else if (homeScore < awayScore)  loseCount++;
	        else                             drawCount++;
	    } else if (team.equals(awayTeam)) {
	        if (awayScore > homeScore)       winCount++;
	        else if (awayScore < homeScore)  loseCount++;
	        else                             drawCount++;
	    }

	    // ここで反映
	    resultEntity.setWin(String.valueOf(winCount));
	    resultEntity.setLose(String.valueOf(loseCount));
	    resultEntity.setDraw(String.valueOf(drawCount));
	    resultEntity.setWinningPoints(String.valueOf(winCount * 3 + drawCount));

	    // ★ 更新後の合計試合数で上書き
	    int games = winCount + loseCount + drawCount;
	    resultEntity.setGames(String.valueOf(games));

	    int unbeaten = parseOrZero(resultEntity.getUnbeatenStreakCount());
	    boolean lostThisGame = (loseCount > befLoseCount);
	    int afUnbeaten = lostThisGame ? 0 : (unbeaten + 1);
	    resultEntity.setUnbeatenStreakCount(String.valueOf(afUnbeaten));
	    resultEntity.setUnbeatenStreakDisp(lostThisGame ? null : SurfaceOverviewConst.CONSECTIVE_UNBEATEN);

	    resultEntity.setWinFlg(winCount  > befWinCount);
	    resultEntity.setLoseFlg(loseCount > befLoseCount);

	    return resultEntity;
	}

	/**
	 * スコアデータ(ホーム前半得点数〜得点継続数)を設定する(teamが同一のものが来る場合はlockする)
	 * @param maxEntity
	 * @param middleEntity
	 * @param resultEntity
	 * @param team
	 * @return
	 */
	private SurfaceOverviewEntity setScoreData(BookDataEntity maxEntity, BookDataEntity middleEntity,
	        BookDataEntity minEntity, SurfaceOverviewEntity resultEntity, String team) {

	    String homeTeam = maxEntity.getHomeTeamName();
	    String awayTeam = maxEntity.getAwayTeamName();

	    int homeMinScore    = parseOrZero(minEntity.getHomeScore());
	    int homeMiddleScore = parseOrZero(middleEntity.getHomeScore());
	    int homeMaxScore    = parseOrZero(maxEntity.getHomeScore());
	    int awayMinScore    = parseOrZero(minEntity.getAwayScore());
	    int awayMiddleScore = parseOrZero(middleEntity.getAwayScore());
	    int awayMaxScore    = parseOrZero(maxEntity.getAwayScore());

	    String home1stHalfScore = resultEntity.getHome1stHalfScore();
	    String home2ndHalfScore = resultEntity.getHome2ndHalfScore();
	    String homeSumScore     = resultEntity.getHomeSumScore();
	    String homeCleanSheet   = resultEntity.getHomeCleanSheet();
	    String away1stHalfScore = resultEntity.getAway1stHalfScore();
	    String away2ndHalfScore = resultEntity.getAway2ndHalfScore();
	    String awaySumScore     = resultEntity.getAwaySumScore();
	    String awayCleanSheet   = resultEntity.getAwayCleanSheet();
	    String failToScore      = resultEntity.getFailToScoreGameCount();

	    int befFailToScore = parseOrZero(failToScore);

	    int home1stScoreDiff = homeMiddleScore - homeMinScore;
	    int home2ndScoreDiff = homeMaxScore    - homeMiddleScore;
	    int away1stScoreDiff = awayMiddleScore - awayMinScore;
	    int away2ndScoreDiff = awayMaxScore    - awayMiddleScore;

	    if (team.equals(homeTeam)) {
	        home1stHalfScore = String.valueOf(parseOrZero(home1stHalfScore) + home1stScoreDiff);
	        home2ndHalfScore = String.valueOf(parseOrZero(home2ndHalfScore) + home2ndScoreDiff);
	        homeSumScore     = String.valueOf(parseOrZero(homeSumScore)     + home1stScoreDiff + home2ndScoreDiff);
	        if (awayMaxScore == 0) homeCleanSheet = String.valueOf(parseOrZero(homeCleanSheet) + 1);
	        if (homeMaxScore == 0) failToScore    = String.valueOf(parseOrZero(failToScore)    + 1);
	    } else if (team.equals(awayTeam)) {
	        away1stHalfScore = String.valueOf(parseOrZero(away1stHalfScore) + away1stScoreDiff);
	        away2ndHalfScore = String.valueOf(parseOrZero(away2ndHalfScore) + away2ndScoreDiff);
	        awaySumScore     = String.valueOf(parseOrZero(awaySumScore)     + away1stScoreDiff + away2ndScoreDiff);
	        if (homeMaxScore == 0) awayCleanSheet = String.valueOf(parseOrZero(awayCleanSheet) + 1);
	        if (awayMaxScore == 0) failToScore    = String.valueOf(parseOrZero(failToScore)    + 1);
	    }

	    // --- ここで未設定を 0 埋め（以降の parse で落ちない）---
	    if (home1stHalfScore == null) home1stHalfScore = "0";
	    if (home2ndHalfScore == null) home2ndHalfScore = "0";
	    if (homeSumScore     == null) homeSumScore     = "0";
	    if (away1stHalfScore == null) away1stHalfScore = "0";
	    if (away2ndHalfScore == null) away2ndHalfScore = "0";
	    if (awaySumScore     == null) awaySumScore     = "0";
	    if (homeCleanSheet   == null) homeCleanSheet   = "0";
	    if (awayCleanSheet   == null) awayCleanSheet   = "0";
	    if (failToScore      == null) failToScore      = "0";

	    // 反映
	    resultEntity.setHome1stHalfScore(home1stHalfScore);
	    resultEntity.setHome2ndHalfScore(home2ndHalfScore);
	    resultEntity.setHomeSumScore(homeSumScore);
	    resultEntity.setHome1stHalfScoreRatio(toPercent(parseOrZero(home1stHalfScore), parseOrZero(homeSumScore)));
	    resultEntity.setHome2ndHalfScoreRatio(toPercent(parseOrZero(home2ndHalfScore), parseOrZero(homeSumScore)));
	    resultEntity.setHomeCleanSheet(homeCleanSheet);

	    resultEntity.setAway1stHalfScore(away1stHalfScore);
	    resultEntity.setAway2ndHalfScore(away2ndHalfScore);
	    resultEntity.setAwaySumScore(awaySumScore);
	    resultEntity.setAway1stHalfScoreRatio(toPercent(parseOrZero(away1stHalfScore), parseOrZero(awaySumScore)));
	    resultEntity.setAway2ndHalfScoreRatio(toPercent(parseOrZero(away2ndHalfScore), parseOrZero(awaySumScore)));
	    resultEntity.setAwayCleanSheet(awayCleanSheet);

	    resultEntity.setFailToScoreGameCount(failToScore);

	    // 直近表示（合計勝敗ではなく「連勝/連敗数」で表現したいなら別カウンタに）
	    resultEntity.setConsecutiveWinDisp(null);
	    if (resultEntity.isWinFlg() && parseOrZero(resultEntity.getWin()) >= 3) {
	        resultEntity.setConsecutiveWinDisp(resultEntity.getWin() + SurfaceOverviewConst.CONSECTIVE_WIN);
	    }
	    resultEntity.setConsecutiveLoseDisp(null);
	    if (resultEntity.isLoseFlg() && parseOrZero(resultEntity.getLose()) >= 3) {
	        resultEntity.setConsecutiveLoseDisp(resultEntity.getLose() + SurfaceOverviewConst.CONSECTIVE_LOSE);
	    }

	    // 得点継続
	    int consec = parseOrZero(resultEntity.getConsecutiveScoreCount());
	    if (parseOrZero(failToScore) == befFailToScore) {
	        consec += 1;
	    } else {
	        consec = 0;
	    }
	    resultEntity.setConsecutiveScoreCount(String.valueOf(consec));
	    resultEntity.setConsecutiveScoreCountDisp(consec >= 3 ? SurfaceOverviewConst.CONSECTIVE_SCORING : null);

	    return resultEntity;
	}

	/**
	 * 序盤,中盤,終盤スコアデータ(序盤勝利数〜終盤好調表示用)を設定する(teamが同一のものが来る場合はlockする)
	 * シーズンの3分の1ずつ消費するごとに序盤,中盤,終盤を区切る
	 * * 基準:
	 *  - シーズン総ラウンド数を3等分し、消費試合数(games)に応じてフェーズ判定
	 *  - 勝敗は本メソッド呼出し前に setTeamMainData で更新済みの winFlg / loseFlg を利用
	 *  - 好調表示はフェーズ内勝率 >= 0.7 で付与（"序盤好調" / "中盤好調" / "終盤好調"）
	 * @param maxEntity
	 * @param resultEntity
	 * @param team
	 * @return
	 */
	private SurfaceOverviewEntity setEachScoreCountData(BookDataEntity maxEntity,
			SurfaceOverviewEntity resultEntity) {
		final String METHOD_NAME = "setEachScoreCountData";
		// 序盤,中盤,終盤の範囲の切り分けは各国,リーグのラウンド数に応じて決定する
		// --- 0埋め（NOT NULL対策） ---
	    if (resultEntity.getFirstWeekGameWinCount() == null)  resultEntity.setFirstWeekGameWinCount("0");
	    if (resultEntity.getFirstWeekGameLostCount() == null) resultEntity.setFirstWeekGameLostCount("0");
	    if (resultEntity.getMidWeekGameWinCount() == null)    resultEntity.setMidWeekGameWinCount("0");
	    if (resultEntity.getMidWeekGameLostCount() == null)   resultEntity.setMidWeekGameLostCount("0");
	    if (resultEntity.getLastWeekGameWinCount() == null)   resultEntity.setLastWeekGameWinCount("0");
	    if (resultEntity.getLastWeekGameLostCount() == null)  resultEntity.setLastWeekGameLostCount("0");

	    // 国とリーグのキーを作成
	    String key = resultEntity.getCountry() + ": " + resultEntity.getLeague();
	    // フェーズ境界
	    int seasonRounds = -99;
	    if (roundMap.containsKey(key)) {
	    	seasonRounds = roundMap.get(key);
	    } else {
	    	String messageCd = "roundMap未存在エラー";
			this.rootCauseWrapper.throwUnexpectedRowCount(
			        PROJECT_NAME, CLASS_NAME, METHOD_NAME,
			        messageCd,
			        -99, seasonRounds,
			        String.format("err=%s", -99)
			    );
	    }
	    final int firstEnd  = (int) Math.ceil(seasonRounds / 3.0);            // 序盤の最終ゲーム番号
	    final int secondEnd = (int) Math.ceil(seasonRounds * 2.0 / 3.0);      // 中盤の最終ゲーム番号

	    // 現在の消費試合数（win+lose+draw）※ setTeamMainData 内で更新済み
	    final int g = parseOrZero(resultEntity.getGames());

	    // 今回試合の結果（勝ち/負け/引分）
	    final boolean won  = resultEntity.isWinFlg();
	    final boolean lost = resultEntity.isLoseFlg();
	    // 引分は won/lost のどちらでもない

	    // 現在フェーズを判定（今回の試合を含む）
	    Phase phase;
	    if (g <= firstEnd)            phase = Phase.FIRST;
	    else if (g <= secondEnd)      phase = Phase.MID;
	    else                          phase = Phase.LAST;

	    // --- フェーズ別 勝/敗 の累計を更新 ---
	    int fW = parseOrZero(resultEntity.getFirstWeekGameWinCount());
	    int fL = parseOrZero(resultEntity.getFirstWeekGameLostCount());
	    int mW = parseOrZero(resultEntity.getMidWeekGameWinCount());
	    int mL = parseOrZero(resultEntity.getMidWeekGameLostCount());
	    int lW = parseOrZero(resultEntity.getLastWeekGameWinCount());
	    int lL = parseOrZero(resultEntity.getLastWeekGameLostCount());

	    switch (phase) {
	        case FIRST:
	            if (won)  fW++;
	            else if (lost) fL++;
	            break;
	        case MID:
	            if (won)  mW++;
	            else if (lost) mL++;
	            break;
	        case LAST:
	            if (won)  lW++;
	            else if (lost) lL++;
	            break;
	    }

	    // 反映（常に "0+"）
	    resultEntity.setFirstWeekGameWinCount(String.valueOf(fW));
	    resultEntity.setFirstWeekGameLostCount(String.valueOf(fL));
	    resultEntity.setMidWeekGameWinCount(String.valueOf(mW));
	    resultEntity.setMidWeekGameLostCount(String.valueOf(mL));
	    resultEntity.setLastWeekGameWinCount(String.valueOf(lW));
	    resultEntity.setLastWeekGameLostCount(String.valueOf(lL));

	    // --- 勝率計算（フェーズ内の試合数 = そのフェーズに属する消費試合数）---
	    int firstGames = clamp(g, 0, firstEnd);
	    int midGames   = clamp(g - firstEnd, 0, Math.max(0, secondEnd - firstEnd));
	    int lastGames  = Math.max(0, g - secondEnd);

	    // 引分は「フェーズ内総試合数 - (勝 + 敗)」で内包される
	    double firstWinRate = (firstGames > 0) ? (fW / (double) firstGames) : 0.0;
	    double midWinRate   = (midGames   > 0) ? (mW / (double) midGames)   : 0.0;
	    double lastWinRate  = (lastGames  > 0) ? (lW / (double) lastGames)  : 0.0;

	    // 表示（勝率7割以上で付与。未達/試合なしは null）
	    resultEntity.setFirstWeekGameWinDisp(firstWinRate >= 0.7 ? "序盤好調" : null);
	    resultEntity.setMidWeekGameWinDisp(  midWinRate   >= 0.7 ? "中盤好調" : null);
	    resultEntity.setLastWeekGameWinDisp( lastWinRate  >= 0.7 ? "終盤好調" : null);

	    return resultEntity;
	}

	/**
	 * 勝利,敗北詳細データ(ホーム勝利数〜アウェー逆境表示用)を設定する(teamが同一のものが来る場合はlockする)
	 * @param maxEntity
	 * @param scoreList
	 * @param resultEntity
	 * @param team
	 * @return
	 */
	private SurfaceOverviewEntity setWinLoseDetailData(BookDataEntity maxEntity, List<String> scoreList,
			SurfaceOverviewEntity resultEntity, String team) {
		String homeTeam = maxEntity.getHomeTeamName();
		String awayTeam = maxEntity.getAwayTeamName();
		int homeScore = Integer.parseInt(maxEntity.getHomeScore());
		int awayScore = Integer.parseInt(maxEntity.getAwayScore());
		String homeWinCount = resultEntity.getHomeWinCount();
		String homeLoseCount = resultEntity.getHomeLoseCount();
		String awayWinCount = resultEntity.getAwayWinCount();
		String awayLoseCount = resultEntity.getAwayLoseCount();
		// teamがホーム側
		if (team.equals(homeTeam)) {
			if (homeScore > awayScore) {
				if (homeWinCount == null || homeWinCount.isBlank()) {
					homeWinCount = "1";
				} else {
					homeWinCount = String.valueOf(Integer.parseInt(homeWinCount) + 1);
				}
			} else if (homeScore < awayScore) {
				if (homeLoseCount == null || homeLoseCount.isBlank()) {
					homeLoseCount = "1";
				} else {
					homeLoseCount = String.valueOf(Integer.parseInt(homeLoseCount) + 1);
				}
			} else {
				if (homeWinCount == null || homeWinCount.isBlank()) {
					homeWinCount = "0";
				}
				if (homeLoseCount == null || homeLoseCount.isBlank()) {
					homeLoseCount = "0";
				}
			}
			// teamがアウェー側
		} else if (team.equals(awayTeam)) {
			if (homeScore < awayScore) {
				if (awayWinCount == null || awayWinCount.isBlank()) {
					awayWinCount = "1";
				} else {
					awayWinCount = String.valueOf(Integer.parseInt(awayWinCount) + 1);
				}
			} else if (homeScore > awayScore) {
				if (awayLoseCount == null || awayLoseCount.isBlank()) {
					awayLoseCount = "1";
				} else {
					awayLoseCount = String.valueOf(Integer.parseInt(awayLoseCount) + 1);
				}
			} else {
				if (awayWinCount == null || awayWinCount.isBlank()) {
					awayWinCount = "0";
				}
				if (awayLoseCount == null || awayLoseCount.isBlank()) {
					awayLoseCount = "0";
				}
			}
		}

		// ホーム勝利数
		resultEntity.setHomeWinCount(homeWinCount);
		// ホーム敗北数
		resultEntity.setHomeLoseCount(homeLoseCount);
		// アウェー勝利数
		resultEntity.setAwayWinCount(awayWinCount);
		// アウェー敗北数
		resultEntity.setAwayLoseCount(awayLoseCount);

		// スコアによる勝利,敗北の設定
		if (team.equals(homeTeam)) {
			resultEntity = updateHomeLeadTrailStats(scoreList, resultEntity);
		} else if (team.equals(awayTeam)) {
			resultEntity = updateAwayLeadTrailStats(scoreList, resultEntity);
		}
		// 逆境表示用
		updateAdversityDisps(resultEntity);

		return resultEntity;
	}

	/**
	 * scoreList（時系列のスコア推移）をもとにホーム側の
	 * - 先制回数
	 * - 逆転勝利/逆転敗北（0-1/0-2/1-0/2-0/その他）
	 * を resultEntity に累積加算する。
	 *
	 * 返却: 更新済み resultEntity
	 */
	private SurfaceOverviewEntity updateHomeLeadTrailStats(
			List<String> scoreList, SurfaceOverviewEntity resultEntity) {

		if (scoreList == null || scoreList.isEmpty())
			return resultEntity;

		boolean has10 = false, has20 = false, has01 = false, has02 = false;
		boolean homeEverLed = false, homeEverTrailed = false;

		// 先制判定（最初にスコアが動いた側）
		String firstScorer = "NONE"; // HOME / AWAY / NONE
		int[] prev = null;

		for (int i = 0; i < scoreList.size(); i++) {
			int[] cur = parseScorePair(scoreList.get(i));
			if (cur == null)
				continue;

			// 特定スコアの存在
			if (cur[0] == 1 && cur[1] == 0)
				has10 = true;
			if (cur[0] == 2 && cur[1] == 0)
				has20 = true;
			if (cur[0] == 0 && cur[1] == 1)
				has01 = true;
			if (cur[0] == 0 && cur[1] == 2)
				has02 = true;

			// リード/ビハインドの存在
			if (cur[0] > cur[1])
				homeEverLed = true;
			if (cur[0] < cur[1])
				homeEverTrailed = true;

			// 先制側
			if (prev != null && "NONE".equals(firstScorer)) {
				int dh = cur[0] - prev[0];
				int da = cur[1] - prev[1];
				if (dh > 0 && da == 0)
					firstScorer = "HOME";
				else if (da > 0 && dh == 0)
					firstScorer = "AWAY";
				// 同フレームで両方増などはスキップ（NONEのまま）
			}
			prev = cur;
		}

		// ループ後、先制側が未確定ならフォールバック（最初の非0-0から推定）
		if ("NONE".equals(firstScorer)) {
		    for (String sc : scoreList) {
		        int[] p = parseScorePair(sc);
		        if (p == null) continue;
		        if (p[0] != p[1]) {          // スコアが動いた最初の状態
		            firstScorer = (p[0] > p[1]) ? "HOME" : "AWAY";
		            break;
		        }
		    }
		}

		// 最終スコア（末尾の有効値）
		int finalH = 0, finalA = 0;
		for (int i = scoreList.size() - 1; i >= 0; i--) {
			int[] last = parseScorePair(scoreList.get(i));
			if (last == null)
				continue;
			finalH = last[0];
			finalA = last[1];
			break;
		}

		// ====== ここから累積更新 ======
		int homeFirst = parseOrZero(resultEntity.getHomeFirstGoalCount());
		int homeWinBehind = parseOrZero(resultEntity.getHomeWinBehindCount());
		int homeLoseBehind = parseOrZero(resultEntity.getHomeLoseBehindCount());
		int homeWinB01 = parseOrZero(resultEntity.getHomeWinBehind0vs1Count());
		int homeLoseB10 = parseOrZero(resultEntity.getHomeLoseBehind1vs0Count());
		int homeWinB02 = parseOrZero(resultEntity.getHomeWinBehind0vs2Count());
		int homeLoseB20 = parseOrZero(resultEntity.getHomeLoseBehind2vs0Count());
		int homeWinBOther = parseOrZero(resultEntity.getHomeWinBehindOtherCount());
		int homeLoseBOther = parseOrZero(resultEntity.getHomeLoseBehindOtherCount());

		// 先制（ホームが最初に得点）
		if ("HOME".equals(firstScorer)) {
			homeFirst += 1;
		}

		// 逆転勝利：最終 勝ち かつ 途中でビハインドあり
		if (finalH > finalA && homeEverTrailed) {
			homeWinBehind += 1;
			if (has02)
				homeWinB02 += 1; // より厳しい 0-2 を優先
			else if (has01)
				homeWinB01 += 1;
			else
				homeWinBOther += 1;
		}

		// 逆転敗北：最終 負け かつ 途中でリードあり
		if (finalH < finalA && homeEverLed) {
			homeLoseBehind += 1;
			if (has20)
				homeLoseB20 += 1; // より大きい 2-0 リードを優先
			else if (has10)
				homeLoseB10 += 1;
			else
				homeLoseBOther += 1;
		}

		// 反映
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
	 * scoreList（時系列のスコア推移）をもとにアウェー側の
	 * - 先制回数
	 * - 逆転勝利/逆転敗北（1-0/2-0/0-1/0-2/その他）
	 * を resultEntity に累積加算する。
	 */
	private SurfaceOverviewEntity updateAwayLeadTrailStats(
			List<String> scoreList, SurfaceOverviewEntity resultEntity) {

		if (scoreList == null || scoreList.isEmpty())
			return resultEntity;

		boolean has10 = false, has20 = false, has01 = false, has02 = false;
		boolean awayEverLed = false, awayEverTrailed = false;

		// 先制判定（最初にスコアが動いた側）
		String firstScorer = "NONE"; // HOME / AWAY / NONE
		int[] prev = null;

		for (int i = 0; i < scoreList.size(); i++) {
			int[] cur = parseScorePair(scoreList.get(i));
			if (cur == null)
				continue;

			// 特定スコアの存在（home-away表記）
			if (cur[0] == 1 && cur[1] == 0)
				has10 = true; // 1-0（ホーム先制/リード）
			if (cur[0] == 2 && cur[1] == 0)
				has20 = true; // 2-0（ホーム2点差リード）
			if (cur[0] == 0 && cur[1] == 1)
				has01 = true; // 0-1（アウェー先制/リード）
			if (cur[0] == 0 && cur[1] == 2)
				has02 = true; // 0-2（アウェー2点差リード）

			// アウェー視点のリード/ビハインド履歴
			if (cur[1] > cur[0])
				awayEverLed = true;
			if (cur[1] < cur[0])
				awayEverTrailed = true;

			// 最初に動いた側（差分で判定）
			if (prev != null && "NONE".equals(firstScorer)) {
				int dh = cur[0] - prev[0];
				int da = cur[1] - prev[1];
				if (da > 0 && dh == 0)
					firstScorer = "AWAY";
				else if (dh > 0 && da == 0)
					firstScorer = "HOME";
				// 両方同時加算などは NONE のまま
			}
			prev = cur;
		}

		// ループ後、先制側が未確定ならフォールバック（最初の非0-0から推定）
		if ("NONE".equals(firstScorer)) {
		    for (String sc : scoreList) {
		        int[] p = parseScorePair(sc);
		        if (p == null) continue;
		        if (p[0] != p[1]) {
		            firstScorer = (p[1] > p[0]) ? "AWAY" : "HOME"; // アウェー視点
		            break;
		        }
		    }
		}

		// 最終スコア（末尾の有効スコア）
		int finalH = 0, finalA = 0;
		for (int i = scoreList.size() - 1; i >= 0; i--) {
			int[] last = parseScorePair(scoreList.get(i));
			if (last == null)
				continue;
			finalH = last[0];
			finalA = last[1];
			break;
		}

		// 既存値（累積）
		int awayFirst = parseOrZero(resultEntity.getAwayFirstGoalCount());
		int awayWinBehind = parseOrZero(resultEntity.getAwayWinBehindCount());
		int awayLoseBehind = parseOrZero(resultEntity.getAwayLoseBehindCount());
		int awayWinB10 = parseOrZero(resultEntity.getAwayWinBehind1vs0Count());
		int awayLoseB01 = parseOrZero(resultEntity.getAwayLoseBehind0vs1Count());
		int awayWinB20 = parseOrZero(resultEntity.getAwayWinBehind2vs0Count());
		int awayLoseB02 = parseOrZero(resultEntity.getAwayLoseBehind0vs2Count());
		int awayWinBOther = parseOrZero(resultEntity.getAwayWinBehindOtherCount());
		int awayLoseBOther = parseOrZero(resultEntity.getAwayLoseBehindOtherCount());

		// アウェー先制
		if ("AWAY".equals(firstScorer)) {
			awayFirst += 1;
		}

		// 逆転勝利：最終 A>H かつ 途中でビハインド（ホームにリードされていた）
		if (finalA > finalH && awayEverTrailed) {
			awayWinBehind += 1;
			if (has20)
				awayWinB20 += 1; // 2-0からの逆転を最優先
			else if (has10)
				awayWinB10 += 1; // 次に1-0
			else
				awayWinBOther += 1;
		}

		// 逆転敗北：最終 A<H かつ 途中でリード（アウェーがリードしていた）
		if (finalA < finalH && awayEverLed) {
			awayLoseBehind += 1;
			if (has02)
				awayLoseB02 += 1; // 0-2からの逆転負けを最優先
			else if (has01)
				awayLoseB01 += 1; // 次に0-1
			else
				awayLoseBOther += 1;
		}

		// 反映
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
	 * 初勝利,負け込みを設定する
	 * @param resultEntity
	 * @return
	 */
	private SurfaceOverviewEntity firstWinAndConsecutiveLose(SurfaceOverviewEntity resultEntity) {
		String win = resultEntity.getWin();
		String games = resultEntity.getGames();
		boolean loseFlg = resultEntity.isLoseFlg();
		resultEntity.setFirstWinDisp(null);
		resultEntity.setLoseStreakDisp(null);
		// 未勝利状態
		if ("0".equals(win)) {
			resultEntity.setFirstWinDisp(SurfaceOverviewConst.FIRST_WIN_MOTIVATION);
		}
		// 負け込み状態
		return resultEntity;
	}

	/**
	 * 割合判定
	 * @param num
	 * @param denom
	 * @param threshold
	 * @return
	 */
	private static boolean isRatioAtLeast(int num, int denom, double threshold) {
		if (denom <= 0)
			return false; // 分母0（勝利0）のときは表示しない
		return (double) num / (double) denom >= threshold;
	}

	/**
	 * 逆境表示（ホーム/アウェー両方）を更新
	 * @param SurfaceOverviewEntity
	 */
	private void updateAdversityDisps(SurfaceOverviewEntity e) {
		final double THRESHOLD = 0.30;

		// ホーム
		int homeWins = parseOrZero(e.getHomeWinCount());
		int homeCFBWins = parseOrZero(e.getHomeWinBehindCount());
		e.setHomeAdversityDisp(
				isRatioAtLeast(homeCFBWins, homeWins, THRESHOLD) ? SurfaceOverviewConst.HOME_ADVERSITY : null);

		// アウェー
		int awayWins = parseOrZero(e.getAwayWinCount());
		int awayCFBWins = parseOrZero(e.getAwayWinBehindCount());
		e.setAwayAdversityDisp(
				isRatioAtLeast(awayCFBWins, awayWins, THRESHOLD) ? SurfaceOverviewConst.AWAY_ADVERSITY : null);
	}

	/**
	 * 2) venue 別カラムなどを 0 で埋める（NOT NULL対策）
	 * @param e
	 */
	private static void ensureNotNullCounters(SurfaceOverviewEntity e) {
	    // home venue
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

	    // away venue
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

	    // 念のためコア数値も 0 埋め（DB制約対策）
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
	}


	/**
	 *  null/空/非数を0に
	 * @param s
	 * @return
	 */
	private static int parseOrZero(String s) {
		if (s == null || s.isBlank())
			return 0;
		try {
			return Integer.parseInt(s.trim());
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	/**
	 * "1-0", "1:0", "1 – 0" 等を許容して [home, away] に変換
	 */
	private static int[] parseScorePair(String s) {
		if (s == null)
			return null;
		String normalized = s.trim()
				.replaceAll("\\s", "")
				.replace('–', '-') // en dash
				.replace('—', '-') // em dash
				.replace(':', '-');
		String[] parts = normalized.split("-");
		if (parts.length != 2)
			return null;
		try {
			int h = Integer.parseInt(parts[0]);
			int a = Integer.parseInt(parts[1]);
			if (h < 0 || a < 0)
				return null;
			return new int[] { h, a };
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/**
	 * 割合を NN% 文字列に整形（四捨五入）。分母0なら "0%"
	 */
	private static String toPercent(int num, int denom) {
	    if (denom <= 0) return "0%";
	    long pct = Math.round((num * 100.0) / denom);
	    return pct + "%";
	    // もし 10% 単位（X0%）に丸めたい場合は以下に差し替え：
	    // long pct = Math.round((num * 100.0) / denom);
	    // pct = Math.round(pct / 10.0) * 10; // 10%単位に丸め
	    // return pct + "%";
	}

	/** 区間クランプ */
	private static int clamp(int v, int min, int max) {
	    if (v < min) return min;
	    if (v > max) return max;
	    return v;
	}

	/**
	 * オブジェクトロック
	 * @param key
	 * @return
	 */
	private Object getLock(String key) {
		return lockMap.computeIfAbsent(key, k -> new Object());
	}
}