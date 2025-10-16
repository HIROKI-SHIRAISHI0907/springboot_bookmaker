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
 * BM_M031統計分析ロジック
 * @author shiraishitoshio
 *
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

	/**閾値 */
	private static final int REQ_ROUNDS_FOR_LOSE_STREAK = 4; // “負け込み” は4連番4連敗
	private static final int REQ_FOR_CONSEC_LOSE_DISP = 1; // “X連敗中” を出す最低本数（例: 3）
	private static final int REQ_FOR_CONSEC_WIN_DISP = 1; // “X連勝中” を出す最低本数（例: 3）

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
				String home = teams[0].trim();
				String away = teams[1].trim();
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
			SurfaceOverviewEntity entity = data.getValue();
			int result;
			if (entity.getId() != null) {
				result = this.surfaceOverviewRepository.update(entity);
				if (result != 1) {
					String messageCd = "更新エラー";
					this.rootCauseWrapper.throwUnexpectedRowCount(
							PROJECT_NAME, CLASS_NAME, "calcStat",
							messageCd,
							1, result,
							String.format("key=%s, id=%s", data.getKey(), entity.getId()));
				}
			} else {
				result = this.surfaceOverviewRepository.insert(entity);
				if (result != 1) {
					String messageCd = "新規登録エラー";
					this.rootCauseWrapper.throwUnexpectedRowCount(
							PROJECT_NAME, CLASS_NAME, "calcStat",
							messageCd,
							1, result,
							String.format("key=%s", data.getKey()));
				}
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
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, null, null, returnMaxEntity.getFilePath());
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
		Integer roundNo = tryGetRoundNo(returnMaxEntity, roundMap.get(country + ": " + league));
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
			resultHomeEntity = setEachScoreCountData(roundNo, resultHomeEntity, country, league);
			resultHomeEntity = setWinLoseDetailData(returnMaxEntity, scoreList, resultHomeEntity, home);
			resultHomeEntity = firstWinAndConsecutiveLose(resultHomeEntity, homeKey, roundNo);
			ensureNotNullCounters(resultHomeEntity);
			resultMap.put(homeKey, resultHomeEntity);
		}
		String awayKey = String.join("|", country, league, away);
		Integer roundNoAway = tryGetRoundNo(returnMaxEntity, roundMap.get(country + ": " + league));
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
			resultAwayEntity = setEachScoreCountData(roundNoAway, resultAwayEntity, country, league);
			resultAwayEntity = setWinLoseDetailData(returnMaxEntity, scoreList, resultAwayEntity, away);
			resultAwayEntity = firstWinAndConsecutiveLose(resultAwayEntity, awayKey, roundNoAway);
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

		int winCount = parseOrZero(resultEntity.getWin());
		int loseCount = parseOrZero(resultEntity.getLose());
		int drawCount = parseOrZero(resultEntity.getDraw());

		// 前回値（フラグ用）
		int befWinCount = winCount;
		int befLoseCount = loseCount;

		// 勝敗更新
		if (team.equals(homeTeam)) {
			if (homeScore > awayScore)
				winCount++;
			else if (homeScore < awayScore)
				loseCount++;
			else
				drawCount++;
		} else if (team.equals(awayTeam)) {
			if (awayScore > homeScore)
				winCount++;
			else if (awayScore < homeScore)
				loseCount++;
			else
				drawCount++;
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

		resultEntity.setWinFlg(winCount > befWinCount);
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

		int homeMinScore = parseOrZero(minEntity.getHomeScore());
		int homeMiddleScore = parseOrZero(middleEntity.getHomeScore());
		int homeMaxScore = parseOrZero(maxEntity.getHomeScore());
		int awayMinScore = parseOrZero(minEntity.getAwayScore());
		int awayMiddleScore = parseOrZero(middleEntity.getAwayScore());
		int awayMaxScore = parseOrZero(maxEntity.getAwayScore());

		String home1stHalfScore = resultEntity.getHome1stHalfScore();
		String home2ndHalfScore = resultEntity.getHome2ndHalfScore();
		String homeSumScore = resultEntity.getHomeSumScore();
		String homeCleanSheet = resultEntity.getHomeCleanSheet();
		String away1stHalfScore = resultEntity.getAway1stHalfScore();
		String away2ndHalfScore = resultEntity.getAway2ndHalfScore();
		String awaySumScore = resultEntity.getAwaySumScore();
		String awayCleanSheet = resultEntity.getAwayCleanSheet();
		String failToScore = resultEntity.getFailToScoreGameCount();

		int befFailToScore = parseOrZero(failToScore);

		int home1stScoreDiff = homeMiddleScore - homeMinScore;
		int home2ndScoreDiff = homeMaxScore - homeMiddleScore;
		int away1stScoreDiff = awayMiddleScore - awayMinScore;
		int away2ndScoreDiff = awayMaxScore - awayMiddleScore;

		if (team.equals(homeTeam)) {
			home1stHalfScore = String.valueOf(parseOrZero(home1stHalfScore) + home1stScoreDiff);
			home2ndHalfScore = String.valueOf(parseOrZero(home2ndHalfScore) + home2ndScoreDiff);
			homeSumScore = String.valueOf(parseOrZero(homeSumScore) + home1stScoreDiff + home2ndScoreDiff);
			if (awayMaxScore == 0)
				homeCleanSheet = String.valueOf(parseOrZero(homeCleanSheet) + 1);
			if (homeMaxScore == 0)
				failToScore = String.valueOf(parseOrZero(failToScore) + 1);
		} else if (team.equals(awayTeam)) {
			away1stHalfScore = String.valueOf(parseOrZero(away1stHalfScore) + away1stScoreDiff);
			away2ndHalfScore = String.valueOf(parseOrZero(away2ndHalfScore) + away2ndScoreDiff);
			awaySumScore = String.valueOf(parseOrZero(awaySumScore) + away1stScoreDiff + away2ndScoreDiff);
			if (homeMaxScore == 0)
				awayCleanSheet = String.valueOf(parseOrZero(awayCleanSheet) + 1);
			if (awayMaxScore == 0)
				failToScore = String.valueOf(parseOrZero(failToScore) + 1);
		}

		// --- ここで未設定を 0 埋め ---
		if (home1stHalfScore == null)
			home1stHalfScore = "0";
		if (home2ndHalfScore == null)
			home2ndHalfScore = "0";
		if (homeSumScore == null)
			homeSumScore = "0";
		if (away1stHalfScore == null)
			away1stHalfScore = "0";
		if (away2ndHalfScore == null)
			away2ndHalfScore = "0";
		if (awaySumScore == null)
			awaySumScore = "0";
		if (homeCleanSheet == null)
			homeCleanSheet = "0";
		if (awayCleanSheet == null)
			awayCleanSheet = "0";
		if (failToScore == null)
			failToScore = "0";

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

		// ★ ここでは “連勝/連敗の表示” はしない（厳密判定は別メソッドで）
		// 得点継続のみ維持
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
	 * シーズンの3分の1ずつ消費するごとに序盤,中盤,終盤を区切る。データカテゴリの語尾のラウンド数を見て序盤,中盤,終盤を判断
	 * * 基準:
	 *  - シーズン総ラウンド数を3等分し、消費試合数(games)に応じてフェーズ判定
	 *  - 勝敗は本メソッド呼出し前に setTeamMainData で更新済みの winFlg / loseFlg を利用
	 *  - 好調表示はフェーズ内勝率 >= 0.7 で付与（"序盤好調" / "中盤好調" / "終盤好調"）
	 * @param maxEntity
	 * @param resultEntity
	 * @param team
	 * @return
	 */
	/**
	 * 序盤/中盤/終盤の勝敗カウントを更新する。
	 * ラウンド番号 roundNo が取得できた場合のみ更新する（null のときは何もしない）。
	 * 勝敗判定は setTeamMainData 後の winFlg / loseFlg を利用。
	 */
	private SurfaceOverviewEntity setEachScoreCountData(
			Integer roundNo,
			SurfaceOverviewEntity resultEntity,
			String country,
			String league) {

		// ラウンド不明ならフェーズ更新はスキップ（誤加算防止）
		if (roundNo == null) {
			return resultEntity;
		}

		// 0埋め
		if (resultEntity.getFirstWeekGameWinCount() == null)
			resultEntity.setFirstWeekGameWinCount("0");
		if (resultEntity.getFirstWeekGameLostCount() == null)
			resultEntity.setFirstWeekGameLostCount("0");
		if (resultEntity.getMidWeekGameWinCount() == null)
			resultEntity.setMidWeekGameWinCount("0");
		if (resultEntity.getMidWeekGameLostCount() == null)
			resultEntity.setMidWeekGameLostCount("0");
		if (resultEntity.getLastWeekGameWinCount() == null)
			resultEntity.setLastWeekGameWinCount("0");
		if (resultEntity.getLastWeekGameLostCount() == null)
			resultEntity.setLastWeekGameLostCount("0");

		// シーズン総ラウンド数を取得
		final String key = country + ": " + league;
		Integer seasonRoundsObj = getRound(roundMap, key);
		if (seasonRoundsObj == null) {
			// 既存の想定に合わせ、ここは異常として扱う
			final String METHOD_NAME = "setEachScoreCountData";
			String messageCd = "roundMap未存在エラー";
			this.rootCauseWrapper.throwUnexpectedRowCount(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					messageCd, -99, -99,
					String.format("country=%s, league=%s", country, league));
		}
		final int seasonRounds = seasonRoundsObj;

		// フェーズ境界（厳密 roundNo 判定のみ）
		final int firstEnd = (int) Math.ceil(seasonRounds / 3.0); // ~ firstEnd
		final int secondEnd = (int) Math.ceil(seasonRounds * 2.0 / 3.0); // firstEnd+1 ~ secondEnd
		// final: secondEnd+1 ~

		// 勝敗フラグ
		final boolean won = resultEntity.isWinFlg();
		final boolean lost = resultEntity.isLoseFlg();

		int fW = parseOrZero(resultEntity.getFirstWeekGameWinCount());
		int fL = parseOrZero(resultEntity.getFirstWeekGameLostCount());
		int mW = parseOrZero(resultEntity.getMidWeekGameWinCount());
		int mL = parseOrZero(resultEntity.getMidWeekGameLostCount());
		int lW = parseOrZero(resultEntity.getLastWeekGameWinCount());
		int lL = parseOrZero(resultEntity.getLastWeekGameLostCount());

		if (roundNo <= firstEnd) {
			if (won)
				fW++;
			else if (lost)
				fL++;
		} else if (roundNo <= secondEnd) {
			if (won)
				mW++;
			else if (lost)
				mL++;
		} else {
			if (won)
				lW++;
			else if (lost)
				lL++;
		}

		// 反映（カウントのみ）
		resultEntity.setFirstWeekGameWinCount(String.valueOf(fW));
		resultEntity.setFirstWeekGameLostCount(String.valueOf(fL));
		resultEntity.setMidWeekGameWinCount(String.valueOf(mW));
		resultEntity.setMidWeekGameLostCount(String.valueOf(mL));
		resultEntity.setLastWeekGameWinCount(String.valueOf(lW));
		resultEntity.setLastWeekGameLostCount(String.valueOf(lL));

		// 好調表示は“累積カウント / そのフェーズで消化した試合数”で出す必要がありますが、
		// ここでは表示の更新は行いません（誤判定防止のため）。
		// 表示を更新する場合は、別途「そのフェーズで実際に何試合消化したか」を roundNo ベースで管理してください。
		// （例：各フェーズの消化試合数を別カラムで持つか、勝敗カウントの合計を分母にする）

		return resultEntity;
	}

	/**
	 * ラウンド番号を取得
	 * @param maxEntity
	 * @param roundMax
	 * @return
	 */
	private Integer tryGetRoundNo(BookDataEntity maxEntity, Integer roundMax) {
		final String METHOD_NAME = "tryGetRoundNo";
		String cat = maxEntity.getGameTeamCategory();
		if (cat == null)
			return null;

		// まずはカテゴリ文字列から直接「ラウンド N」を取る
		Integer n = parseRoundFromGameTeamCategory(cat);
		if (n != null) {
			if (roundMax != null && n > roundMax) {
				String messageCd = "ラウンド番号が異常値";
				this.rootCauseWrapper.throwUnexpectedRowCount(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						messageCd, -99, -99,
						String.format("roundMax=%s, targetRound=%s, gameDataCategory=%s, csv=%s",
								roundMax, n, maxEntity.getGameTeamCategory(), maxEntity.getFilePath()));
			}
			return n;
		}

		// フォールバック（最後のハイフン以降に数字があればそれを採用）
		String s = cat.trim()
				.replace('－', '-').replace('–', '-').replace('—', '-')
				.replace('：', ':');
		int idx = s.lastIndexOf('-');
		String tail = (idx >= 0) ? s.substring(idx + 1) : s;
		tail = toHalfWidthDigits(tail);
		String digits = tail.replaceAll("[^0-9]", "");
		if (!digits.isEmpty()) {
			int v = Integer.parseInt(digits);
			if (v > roundMax) {
				String messageCd = "ラウンド番号が異常値";
				this.rootCauseWrapper.throwUnexpectedRowCount(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						messageCd, -99, -99,
						String.format("roundMax=%s, targetRound=%s, gameDataCategory=%s, csv=%s",
								roundMax, v, maxEntity.getGameTeamCategory(), maxEntity.getFilePath()));
			}
			return v;
		}
		return null;
	}

	/**
	 *  全角数字を半角へ
	 * @param in
	 * @return
	 */
	private static String toHalfWidthDigits(String in) {
		StringBuilder sb = new StringBuilder(in.length());
		for (char ch : in.toCharArray()) {
			if (ch >= '０' && ch <= '９') {
				sb.append((char) ('0' + (ch - '０')));
			} else {
				sb.append(ch);
			}
		}
		return sb.toString();
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
				if (p == null)
					continue;
				if (p[0] != p[1]) { // スコアが動いた最初の状態
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
				if (p == null)
					continue;
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
	 * 初勝利・負け込み（連敗）を設定する。
	 * 連敗は「直近ラウンド + 1 で負け」が続いたときのみ増加。
	 * ラウンドが飛んだ/勝ち/引き分けが間にある場合はリセット。
	 *
	 * @param resultEntity 今回の集計先
	 * @param teamKey      同期・状態保持用キー（country|league|team など）
	 * @param roundNo      今回試合のラウンド番号（取れない場合は null）
	 */
	/**
	 * 初勝利・連勝/連敗・負け込み（行＝年月単位で判定）
	 * その行の roundConc を更新 → その行だけで streak を再計算します
	 */
	private SurfaceOverviewEntity firstWinAndConsecutiveLose(
			SurfaceOverviewEntity e, String teamKey, Integer roundNo) {

		e.setFirstWinDisp(null);
		if ("0".equals(e.getWin()) && !"0".equals(e.getGames())) {
			e.setFirstWinDisp(SurfaceOverviewConst.FIRST_WIN_MOTIVATION);
		}
		if (roundNo == null)
			return e;

		final boolean winThis = e.isWinFlg();
		final boolean loseThis = e.isLoseFlg();

		// ★ ここを差し替え：全行マージ済みヒストリをロード
		RoundHistory hist = loadMergedRoundHistory(e.getCountry(), e.getLeague(), e.getTeam());

		// 今回ラウンドで上書き
		hist.all.add(roundNo);
		hist.win.remove(roundNo);
		hist.lose.remove(roundNo);
		if (winThis)
			hist.win.add(roundNo);
		else if (loseThis)
			hist.lose.add(roundNo);

		Integer end = hist.all.isEmpty() ? null : hist.all.last();

		int loseStreak = 0;
		int winStreak = 0;
		if (end != null) {
			if (hist.lose.contains(end))
				loseStreak = countConsecutiveEndingAt(hist.lose, end);
			if (hist.win.contains(end))
				winStreak = countConsecutiveEndingAt(hist.win, end);
		}

		e.setConsecutiveLoseCount(String.valueOf(loseStreak));
		e.setConsecutiveLoseDisp(loseStreak >= REQ_FOR_CONSEC_LOSE_DISP
				? (loseStreak + SurfaceOverviewConst.CONSECTIVE_LOSE)
				: null);
		e.setLoseStreakDisp(loseStreak >= REQ_ROUNDS_FOR_LOSE_STREAK
				? SurfaceOverviewConst.LOSE_CONSECUTIVE
				: null);

		e.setConsecutiveWinDisp(winStreak >= REQ_FOR_CONSEC_WIN_DISP
				? (winStreak + SurfaceOverviewConst.CONSECTIVE_WIN)
				: null);

		// ★ マージ＋今回反映した結果を、**この行の roundConc にも保存**
		e.setRoundConc(toRoundConc(hist));
		return e;
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
		if (e.getHomeWinCount() == null)
			e.setHomeWinCount("0");
		if (e.getHomeLoseCount() == null)
			e.setHomeLoseCount("0");
		if (e.getHomeFirstGoalCount() == null)
			e.setHomeFirstGoalCount("0");
		if (e.getHomeWinBehindCount() == null)
			e.setHomeWinBehindCount("0");
		if (e.getHomeLoseBehindCount() == null)
			e.setHomeLoseBehindCount("0");
		if (e.getHomeWinBehind0vs1Count() == null)
			e.setHomeWinBehind0vs1Count("0");
		if (e.getHomeLoseBehind1vs0Count() == null)
			e.setHomeLoseBehind1vs0Count("0");
		if (e.getHomeWinBehind0vs2Count() == null)
			e.setHomeWinBehind0vs2Count("0");
		if (e.getHomeLoseBehind2vs0Count() == null)
			e.setHomeLoseBehind2vs0Count("0");
		if (e.getHomeWinBehindOtherCount() == null)
			e.setHomeWinBehindOtherCount("0");
		if (e.getHomeLoseBehindOtherCount() == null)
			e.setHomeLoseBehindOtherCount("0");

		// away venue
		if (e.getAwayWinCount() == null)
			e.setAwayWinCount("0");
		if (e.getAwayLoseCount() == null)
			e.setAwayLoseCount("0");
		if (e.getAwayFirstGoalCount() == null)
			e.setAwayFirstGoalCount("0");
		if (e.getAwayWinBehindCount() == null)
			e.setAwayWinBehindCount("0");
		if (e.getAwayLoseBehindCount() == null)
			e.setAwayLoseBehindCount("0");
		if (e.getAwayWinBehind1vs0Count() == null)
			e.setAwayWinBehind1vs0Count("0");
		if (e.getAwayLoseBehind0vs1Count() == null)
			e.setAwayLoseBehind0vs1Count("0");
		if (e.getAwayWinBehind2vs0Count() == null)
			e.setAwayWinBehind2vs0Count("0");
		if (e.getAwayLoseBehind0vs2Count() == null)
			e.setAwayLoseBehind0vs2Count("0");
		if (e.getAwayWinBehindOtherCount() == null)
			e.setAwayWinBehindOtherCount("0");
		if (e.getAwayLoseBehindOtherCount() == null)
			e.setAwayLoseBehindOtherCount("0");

		// 念のためコア数値も 0 埋め（DB制約対策）
		if (e.getWin() == null)
			e.setWin("0");
		if (e.getLose() == null)
			e.setLose("0");
		if (e.getDraw() == null)
			e.setDraw("0");
		if (e.getGames() == null)
			e.setGames("0");
		if (e.getWinningPoints() == null)
			e.setWinningPoints("0");
		if (e.getFailToScoreGameCount() == null)
			e.setFailToScoreGameCount("0");
		if (e.getUnbeatenStreakCount() == null)
			e.setUnbeatenStreakCount("0");
		if (e.getFirstWeekGameWinCount() == null)
			e.setFirstWeekGameWinCount("0");
		if (e.getFirstWeekGameLostCount() == null)
			e.setFirstWeekGameLostCount("0");
		if (e.getMidWeekGameWinCount() == null)
			e.setMidWeekGameWinCount("0");
		if (e.getMidWeekGameLostCount() == null)
			e.setMidWeekGameLostCount("0");
		if (e.getLastWeekGameWinCount() == null)
			e.setLastWeekGameWinCount("0");
		if (e.getLastWeekGameLostCount() == null)
			e.setLastWeekGameLostCount("0");
		if (e.getConsecutiveScoreCount() == null)
			e.setConsecutiveScoreCount("0");
		if (e.getConsecutiveLoseCount() == null)
			e.setConsecutiveLoseCount("0");
	}

	/** 同一チーム（country, league, team）の全行から roundConc をマージ */
	private RoundHistory loadMergedRoundHistory(String country, String league, String team) {
		RoundHistory merged = new RoundHistory();
		// ★ Repository に用意して下さい（同じシーズン範囲で絞るのが理想）
		// 例: 全年/月の当該チームの行を取得
		List<SurfaceOverviewEntity> rows = surfaceOverviewRepository.selectAllMonthsByTeam(country, league, team);

		for (SurfaceOverviewEntity row : rows) {
			RoundHistory h = parseRoundConc(row.getRoundConc());
			merged.all.addAll(h.all);
			merged.win.addAll(h.win);
			merged.lose.addAll(h.lose);
		}
		return merged;
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
		if (denom <= 0)
			return "0%";
		long pct = Math.round((num * 100.0) / denom);
		return pct + "%";
		// もし 10% 単位（X0%）に丸めたい場合は以下に差し替え：
		// long pct = Math.round((num * 100.0) / denom);
		// pct = Math.round(pct / 10.0) * 10; // 10%単位に丸め
		// return pct + "%";
	}

	/**
	 * gameTeamCategory から「ラウンド N」の N を抜き出す。
	 * 例: "アルゼンチン: トルネオ・ベターノ - アペルトゥラ - ラウンド 8" -> 8
	 * 英語 "Round 8" にも対応。全角数字/全角ハイフン/空白ゆれも吸収。
	 * 見つからない場合は null。
	 */
	private static Integer parseRoundFromGameTeamCategory(String s) {
		if (s == null)
			return null;

		// 前処理：トリム & よくある表記ゆれを正規化
		String t = s.trim()
				.replace('\u00A0', ' ') // NBSP
				.replace('－', '-') // 全角ハイフン
				.replace('–', '-') // en dash
				.replace('—', '-') // em dash
				.replace('：', ':'); // 全角コロン -> 半角

		// 全角数字を半角に（"ラウンド ８" など）
		t = toHalfWidthDigits(t);

		// 「ラウンド 8」または「Round 8」を拾う（どこにあってもOK）
		Matcher m = Pattern.compile("(?:ラウンド|Round)\\s*(\\d+)").matcher(t);
		if (m.find()) {
			try {
				return Integer.valueOf(m.group(1));
			} catch (NumberFormatException ignore) {
			}
		}
		return null;
	}

	// --- RoundHistory と roundConc 変換ヘルパ ---
	private static final class RoundHistory {
		final java.util.TreeSet<Integer> all = new java.util.TreeSet<>();
		final java.util.TreeSet<Integer> win = new java.util.TreeSet<>();
		final java.util.TreeSet<Integer> lose = new java.util.TreeSet<>();
	}

	private static RoundHistory parseRoundConc(String s) {
		RoundHistory h = new RoundHistory();
		if (s == null || s.isBlank())
			return h;
		String[] parts = s.split("\\|");
		for (String part : parts) {
			String[] kv = part.split("=", 2);
			if (kv.length != 2)
				continue;
			String k = kv[0].trim();
			String v = kv[1].trim();
			if (!v.isEmpty()) {
				for (String t : v.split(",")) {
					t = t.trim();
					if (t.matches("\\d+")) {
						int n = Integer.parseInt(t);
						switch (k) {
						case "A":
							h.all.add(n);
							break;
						case "W":
							h.win.add(n);
							break;
						case "L":
							h.lose.add(n);
							break;
						}
					}
				}
			}
		}
		return h;
	}

	/**
	 * round_concに保管するための文字列形成
	 * @param h
	 * @return
	 */
	private static String toRoundConc(RoundHistory h) {
		String A = h.all.stream().map(String::valueOf).reduce((x, y) -> x + "," + y).orElse("");
		String W = h.win.stream().map(String::valueOf).reduce((x, y) -> x + "," + y).orElse("");
		String L = h.lose.stream().map(String::valueOf).reduce((x, y) -> x + "," + y).orElse("");
		return "A=" + A + "|W=" + W + "|L=" + L;
	}

	// 末尾が end の連番本数（... end-2, end-1, end）
	private static int countConsecutiveEndingAt(java.util.NavigableSet<Integer> set, int end) {
		if (set.isEmpty())
			return 0;
		int cnt = 0;
		int r = end;
		while (r >= 0 && set.contains(r)) {
			cnt++;
			r--;
		}
		return cnt;
	}

	/**
	 * ラウンド総数を取得
	 * @param roundMap
	 * @param key
	 * @return
	 */
	private static Integer getRound(Map<String, Integer> roundMap, String key) {
		Integer seasonRoundsObj = roundMap.get(key);
		return seasonRoundsObj;
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