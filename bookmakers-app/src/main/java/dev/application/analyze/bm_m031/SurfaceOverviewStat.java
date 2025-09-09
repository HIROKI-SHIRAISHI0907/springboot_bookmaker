package dev.application.analyze.bm_m031;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.application.analyze.interf.AnalyzeEntityIF;
import dev.application.domain.repository.SurfaceOverviewRepository;
import dev.common.entity.BookDataEntity;
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

	/** SurfaceOverviewRepositoryレポジトリクラス */
	@Autowired
	private SurfaceOverviewRepository surfaceOverviewRepository;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void calcStat(Map<String, Map<String, List<BookDataEntity>>> entities) {
		final String METHOD_NAME = "calcStat";
		this.manageLoggerComponent.init(EXEC_MODE, null);
		this.manageLoggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		// 全リーグ・国を走査
		for (Map.Entry<String, Map<String, List<BookDataEntity>>> entry : entities.entrySet()) {
			ConcurrentHashMap<String, SurfaceOverviewEntity> resultMap = new ConcurrentHashMap<>();
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
			}

			if (!resultMap.isEmpty()) {
				//surfaceOverviewRepository.saveAll(resultMap.values());
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
		String gameTime = returnMaxEntity.getTime();
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
	private SurfaceOverviewEntity setTeamMainData(BookDataEntity maxEntity,
			SurfaceOverviewEntity resultEntity, String country, String league, String team) {
		String homeTeam = maxEntity.getHomeTeamName();
		String awayTeam = maxEntity.getAwayTeamName();
		int homeScore = Integer.parseInt(maxEntity.getHomeScore());
		int awayScore = Integer.parseInt(maxEntity.getAwayScore());
		String winCount = resultEntity.getWin();
		String loseCount = resultEntity.getLose();
		String drawCount = resultEntity.getDraw();
		// 前回勝ち負けデータ保存
		String befWinCount = winCount;
		String befLoseCount = loseCount;
		// teamがホーム側
		if (team.equals(homeTeam)) {
			if (homeScore > awayScore) {
				if (winCount == null || winCount.isBlank()) {
					winCount = "1";
				} else {
					winCount = String.valueOf(Integer.parseInt(winCount) + 1);
				}
			} else if (homeScore < awayScore) {
				if (loseCount == null || loseCount.isBlank()) {
					loseCount = "1";
				} else {
					loseCount = String.valueOf(Integer.parseInt(loseCount) + 1);
				}
			} else if (homeScore == awayScore) {
				if (drawCount == null || drawCount.isBlank()) {
					drawCount = "1";
				} else {
					drawCount = String.valueOf(Integer.parseInt(drawCount) + 1);
				}
			}
			// teamがアウェー側
		} else if (team.equals(awayTeam)) {
			if (homeScore < awayScore) {
				if (winCount == null || winCount.isBlank()) {
					winCount = "1";
				} else {
					winCount = String.valueOf(Integer.parseInt(winCount) + 1);
				}
			} else if (homeScore > awayScore) {
				if (loseCount == null || loseCount.isBlank()) {
					loseCount = "1";
				} else {
					loseCount = String.valueOf(Integer.parseInt(loseCount) + 1);
				}
			} else if (homeScore == awayScore) {
				if (drawCount == null || drawCount.isBlank()) {
					drawCount = "1";
				} else {
					drawCount = String.valueOf(Integer.parseInt(drawCount) + 1);
				}
			}
		}
		// 勝利
		resultEntity.setWin(winCount);
		// 敗北
		resultEntity.setLose(loseCount);
		// 引き分け
		resultEntity.setDraw(drawCount);
		// 勝ち点
		resultEntity.setWinningPoints(String.valueOf(Integer.parseInt(winCount) * 3 +
				Integer.parseInt(drawCount) * 1));

		// 無敗記録数,表示(前回保存した勝敗記録から算出。負けが増えなければ継続扱い)
		int befLose = Integer.parseInt(befLoseCount);
		int lose = Integer.parseInt(loseCount);
		int unbeaten = Integer.parseInt(resultEntity.getUnbeatenStreakCount());
		String afUnbeaten = (befLose < lose) ? "0" : String.valueOf(unbeaten + 1);
		String afUnbeatenDisp = (befLose < lose) ? null : SurfaceOverviewConst.CONSECTIVE_UNBEATEN;
		resultEntity.setUnbeatenStreakCount(afUnbeaten);
		resultEntity.setUnbeatenStreakDisp(afUnbeatenDisp);
		// 勝ちが増えれば勝ちフラグ,負けが増えれば負けフラグ設定
		int befWin = Integer.parseInt(befWinCount);
		int win = Integer.parseInt(winCount);
		boolean afWinFlg = (befWin < win) ? true : false;
		resultEntity.setWinFlg(afWinFlg);
		boolean afLoseFlg = (befLose < lose) ? true : false;
		resultEntity.setLoseFlg(afLoseFlg);
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
		String homeMinScore = minEntity.getHomeScore();
		String homeMiddleScore = middleEntity.getHomeScore();
		String homeMaxScore = maxEntity.getHomeScore();
		String awayMinScore = minEntity.getAwayScore();
		String awayMiddleScore = middleEntity.getAwayScore();
		String awayMaxScore = maxEntity.getAwayScore();
		String home1stHalfScore = resultEntity.getHome1stHalfScore();
		String home2ndHalfScore = resultEntity.getHome2ndHalfScore();
		String homeSumScore = resultEntity.getHomeSumScore();
		String homeCleanSheet = resultEntity.getHomeCleanSheet();
		String away1stHalfScore = resultEntity.getAway1stHalfScore();
		String away2ndHalfScore = resultEntity.getAway2ndHalfScore();
		String awaySumScore = resultEntity.getAwaySumScore();
		String awayCleanSheet = resultEntity.getAwayCleanSheet();
		String failToScore = resultEntity.getFailToScoreGameCount();
		// 前回の無得点試合を保存
		int befFailToScore = Integer.parseInt(failToScore);
		// 前後半の得点差分
		int home1stScoreDiff = Integer.parseInt(homeMiddleScore) -
				Integer.parseInt(homeMinScore);
		int home2ndScoreDiff = Integer.parseInt(homeMaxScore) -
				Integer.parseInt(homeMiddleScore);
		int away1stScoreDiff = Integer.parseInt(awayMiddleScore) -
				Integer.parseInt(awayMinScore);
		int away2ndScoreDiff = Integer.parseInt(awayMaxScore) -
				Integer.parseInt(awayMiddleScore);
		// teamがホーム側
		if (team.equals(homeTeam)) {
			home1stHalfScore = (home1stHalfScore == null || home1stHalfScore.isBlank())
					? String.valueOf(home1stScoreDiff)
					: String.valueOf(Integer.parseInt(home1stHalfScore) + home1stScoreDiff);
			home2ndHalfScore = (home2ndHalfScore == null || home2ndHalfScore.isBlank())
					? String.valueOf(home2ndScoreDiff)
					: String.valueOf(Integer.parseInt(home2ndHalfScore) + home2ndScoreDiff);
			homeSumScore = (homeSumScore == null || homeSumScore.isBlank())
					? String.valueOf(home1stScoreDiff + home2ndScoreDiff)
					: String.valueOf(Integer.parseInt(homeSumScore) + home1stScoreDiff + home2ndScoreDiff);
			if ("0".equals(awayMaxScore)) {
				homeCleanSheet = (homeCleanSheet == null || homeCleanSheet.isBlank()) ? "1"
						: String.valueOf(Integer.parseInt(homeCleanSheet) + 1);
			}
			if ("0".equals(homeMaxScore)) {
				failToScore = (failToScore == null || failToScore.isBlank()) ? "1"
						: String.valueOf(Integer.parseInt(failToScore) + 1);
			}
			// teamがアウェー側
		} else if (team.equals(awayTeam)) {
			away1stHalfScore = (away1stHalfScore == null || away1stHalfScore.isBlank())
					? String.valueOf(away1stScoreDiff)
					: String.valueOf(Integer.parseInt(away1stHalfScore) + away1stScoreDiff);
			away2ndHalfScore = (away2ndHalfScore == null || away2ndHalfScore.isBlank())
					? String.valueOf(away2ndScoreDiff)
					: String.valueOf(Integer.parseInt(away2ndHalfScore) + away2ndScoreDiff);
			awaySumScore = (awaySumScore == null || awaySumScore.isBlank())
					? String.valueOf(away1stScoreDiff + away2ndScoreDiff)
					: String.valueOf(Integer.parseInt(awaySumScore) + away1stScoreDiff + away2ndScoreDiff);
			if ("0".equals(homeMaxScore)) {
				awayCleanSheet = (awayCleanSheet == null || awayCleanSheet.isBlank()) ? "1"
						: String.valueOf(Integer.parseInt(awayCleanSheet) + 1);
			}
			if ("0".equals(awayMaxScore)) {
				failToScore = (failToScore == null || failToScore.isBlank()) ? "1"
						: String.valueOf(Integer.parseInt(failToScore) + 1);
			}
		}

		// ホーム前半得点数
		resultEntity.setHome1stHalfScore(home1stHalfScore);
		// ホーム後半得点数
		resultEntity.setHome2ndHalfScore(home2ndHalfScore);
		// ホーム得点数
		resultEntity.setHomeSumScore(homeSumScore);
		// ホーム前半/後半得点割合（%表記）
		int h1 = Integer.parseInt(home1stHalfScore);
		int h2 = Integer.parseInt(home2ndHalfScore);
		int hSum = Integer.parseInt(homeSumScore);
		resultEntity.setHome1stHalfScoreRatio(toPercent(h1, hSum));
		resultEntity.setHome2ndHalfScoreRatio(toPercent(h2, hSum));
		// ホーム無失点数
		resultEntity.setHomeCleanSheet(homeCleanSheet);
		// アウェー前半得点数
		resultEntity.setAway1stHalfScore(away1stHalfScore);
		// アウェー後半得点数
		resultEntity.setAway2ndHalfScore(away2ndHalfScore);
		// アウェー得点数
		resultEntity.setAwaySumScore(awaySumScore);
		// アウェー前半/後半得点割合（%表記）
		int a1 = Integer.parseInt(away1stHalfScore);
		int a2 = Integer.parseInt(away2ndHalfScore);
		int aSum = Integer.parseInt(awaySumScore);
		resultEntity.setAway1stHalfScoreRatio(toPercent(a1, aSum));
		resultEntity.setAway2ndHalfScoreRatio(toPercent(a2, aSum));
		// アウェー無失点数
		resultEntity.setAwayCleanSheet(awayCleanSheet);
		// 無得点試合数
		resultEntity.setFailToScoreGameCount(failToScore);

		// 直近関係表示用
		// winFlgで処理分岐
		boolean winFlg = resultEntity.isWinFlg();
		int win = Integer.parseInt(resultEntity.getWin());
		resultEntity.setConsecutiveWinDisp(null);
		if (winFlg && win >= 3) {
			resultEntity.setConsecutiveWinDisp(win + SurfaceOverviewConst.CONSECTIVE_WIN);
		}
		boolean loseFlg = resultEntity.isLoseFlg();
		int lose = Integer.parseInt(resultEntity.getLose());
		resultEntity.setConsecutiveLoseDisp(null);
		if (loseFlg && lose >= 3) {
			resultEntity.setConsecutiveLoseDisp(lose + SurfaceOverviewConst.CONSECTIVE_LOSE);
		}

		// 得点継続数
		String consecutiveScore = resultEntity.getConsecutiveScoreCount();
		// 無得点試合数が増えなかった
		if (Integer.parseInt(failToScore) == befFailToScore) {
			resultEntity.setConsecutiveScoreCount((consecutiveScore == null || consecutiveScore.isBlank())
					? "1"
					: String.valueOf(Integer.parseInt(consecutiveScore) + 1));
		} else {
			resultEntity.setConsecutiveScoreCount("0");
		}

		// 表示用
		String befConsecutiveScore = resultEntity.getConsecutiveScoreCount();
		resultEntity.setConsecutiveScoreCountDisp(null);
		if (Integer.parseInt(befConsecutiveScore) >= 3) {
			resultEntity.setConsecutiveScoreCountDisp(SurfaceOverviewConst.CONSECTIVE_SCORING);
		}
		return resultEntity;
	}

	/**
	 * 序盤,中盤,終盤スコアデータ(序盤勝利数〜終盤好調表示用)を設定する(teamが同一のものが来る場合はlockする)
	 * @param maxEntity
	 * @param resultEntity
	 * @param team
	 * @return
	 */
	private SurfaceOverviewEntity setEachScoreCountData(BookDataEntity maxEntity,
			SurfaceOverviewEntity resultEntity) {
		// 序盤,中盤,終盤の範囲の切り分けは各国,リーグのラウンド数に応じて決定する
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

	/**
	 * オブジェクトロック
	 * @param key
	 * @return
	 */
	private Object getLock(String key) {
		return lockMap.computeIfAbsent(key, k -> new Object());
	}
}