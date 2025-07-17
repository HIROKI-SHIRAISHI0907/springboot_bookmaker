package dev.application.analyze.bm_m021;

import java.util.ArrayList;
import java.util.List;

import dev.application.analyze.common.entity.ThresHoldEntity;
import dev.application.analyze.common.util.ExecuteMainUtil;
import dev.common.constant.BookMakersCommonConst;


/**
 * 平均特徴量導出サブロジック
 * @author shiraishitoshio
 *
 */
public class AverageFeatureSubLogic {

	/**
	 * @param entityList CSV読み込みEntityリスト
	 * @param halfList ハーフリスト
	 * @throws Exception
	 */
	public void execute(List<ThresHoldEntity> entityList, String file)
			throws Exception {

		// 最大の通番を持つ時間を返却する
		ThresHoldEntity returnMaxEntity = ExecuteMainUtil.getMaxSeqEntities(entityList);
		if (!BookMakersCommonConst.FIN.equals(returnMaxEntity.getTimes())) {
			System.out.println("終了済データないため勝敗がわかりません。スキップします: file: " + file);
			return;
		}
		// チーム名
		String home = returnMaxEntity.getHomeTeamName();
		String away = returnMaxEntity.getAwayTeamName();

		int homeAverageCounter = 0;
		int awayAverageCounter = 0;
		int homeDonationSum = 0;
		int awayDonationSum = 0;
		for (ThresHoldEntity entity : entityList) {
			AverageFeatureOutputDTO homeSumDonationDto = sumFeature(homeDonationSum,
					entity.getHomeDonation(), homeAverageCounter);
			homeDonationSum = homeSumDonationDto.getSum();
			homeAverageCounter = homeSumDonationDto.getCounter();
			AverageFeatureOutputDTO awaySumDonationDto = sumFeature(awayDonationSum,
					entity.getAwayDonation(), awayAverageCounter);
			awayDonationSum = awaySumDonationDto.getSum();
			awayAverageCounter = awaySumDonationDto.getCounter();
		}
		// 平均支配率
		String aveHomeDonation = "";
		if (homeAverageCounter != 0) {
			aveHomeDonation = String.format("%.2f", (double) homeDonationSum / homeAverageCounter) + "%";
		}
		String aveAwayDonation = "";
		if (homeAverageCounter != 0) {
			aveAwayDonation = String.format("%.2f", (double) awayDonationSum / awayAverageCounter) + "%";
		}

		// 3分割データ
		List<String> homePassList = ExecuteMainUtil.splitGroup(returnMaxEntity.getHomePassCount());
		List<String> awayPassList = ExecuteMainUtil.splitGroup(returnMaxEntity.getAwayPassCount());
		List<String> homeFinalThirdPassList = ExecuteMainUtil.splitGroup(returnMaxEntity.getHomeFinalThirdPassCount());
		List<String> awayFinalThirdPassList = ExecuteMainUtil.splitGroup(returnMaxEntity.getAwayFinalThirdPassCount());
		List<String> homeCrossList = ExecuteMainUtil.splitGroup(returnMaxEntity.getHomeCrossCount());
		List<String> awayCrossList = ExecuteMainUtil.splitGroup(returnMaxEntity.getAwayCrossCount());
		List<String> homeTackleList = ExecuteMainUtil.splitGroup(returnMaxEntity.getHomeTackleCount());
		List<String> awayTackleList = ExecuteMainUtil.splitGroup(returnMaxEntity.getAwayTackleCount());

		// 設定
		List<TeamMatchFinalStatsEntity> insertEntities = new ArrayList<TeamMatchFinalStatsEntity>();
		TeamMatchFinalStatsEntity averageFeatureEntity = new TeamMatchFinalStatsEntity();
		averageFeatureEntity.setTeamName(home);
		averageFeatureEntity.setVersusTeamName("vs" + away);
		averageFeatureEntity.setHa("H");
		String result = compareScore(returnMaxEntity.getHomeScore(), returnMaxEntity.getAwayScore());
		String mark = setSymbol(result);
		averageFeatureEntity.setScore(mark + returnMaxEntity.getHomeScore() + "-" + returnMaxEntity.getAwayScore());
		averageFeatureEntity.setResult(result);
		averageFeatureEntity.setGameFinRank(returnMaxEntity.getHomeRank());
		averageFeatureEntity.setOppositeGameFinRank(returnMaxEntity.getAwayRank());
		averageFeatureEntity.setExp(returnMaxEntity.getHomeExp());
		averageFeatureEntity.setOppositeExp(returnMaxEntity.getAwayExp());
		averageFeatureEntity.setDonation(aveHomeDonation);
		averageFeatureEntity.setOppositeDonation(aveAwayDonation);
		averageFeatureEntity.setShootAll(returnMaxEntity.getHomeShootAll());
		averageFeatureEntity.setOppositeShootAll(returnMaxEntity.getAwayShootAll());
		averageFeatureEntity.setShootIn(returnMaxEntity.getHomeShootIn());
		averageFeatureEntity.setOppositeShootIn(returnMaxEntity.getAwayShootIn());
		averageFeatureEntity.setShootOut(returnMaxEntity.getHomeShootOut());
		averageFeatureEntity.setOppositeShootOut(returnMaxEntity.getAwayShootOut());
		averageFeatureEntity.setBlockShoot(returnMaxEntity.getHomeBlockShoot());
		averageFeatureEntity.setOppositeBlockShoot(returnMaxEntity.getAwayBlockShoot());
		averageFeatureEntity.setBigChance(returnMaxEntity.getHomeBigChance());
		averageFeatureEntity.setOppositeBigChance(returnMaxEntity.getAwayBigChance());
		averageFeatureEntity.setCorner(returnMaxEntity.getHomeCorner());
		averageFeatureEntity.setOppositeCorner(returnMaxEntity.getAwayCorner());
		averageFeatureEntity.setBoxShootIn(returnMaxEntity.getHomeBoxShootIn());
		averageFeatureEntity.setOppositeBoxShootIn(returnMaxEntity.getAwayBoxShootIn());
		averageFeatureEntity.setBoxShootOut(returnMaxEntity.getHomeBoxShootOut());
		averageFeatureEntity.setOppositeBoxShootOut(returnMaxEntity.getAwayBoxShootOut());
		averageFeatureEntity.setGoalPost(returnMaxEntity.getHomeGoalPost());
		averageFeatureEntity.setOppositeGoalPost(returnMaxEntity.getAwayGoalPost());
		averageFeatureEntity.setGoalHead(returnMaxEntity.getHomeGoalHead());
		averageFeatureEntity.setOppositeGoalHead(returnMaxEntity.getAwayGoalHead());
		averageFeatureEntity.setKeeperSave(returnMaxEntity.getHomeKeeperSave());
		averageFeatureEntity.setOppositeKeeperSave(returnMaxEntity.getAwayKeeperSave());
		averageFeatureEntity.setFreeKick(returnMaxEntity.getHomeFreeKick());
		averageFeatureEntity.setOppositeFreeKick(returnMaxEntity.getAwayFreeKick());
		averageFeatureEntity.setOffside(returnMaxEntity.getHomeOffside());
		averageFeatureEntity.setOppositeOffside(returnMaxEntity.getAwayOffside());
		averageFeatureEntity.setFoul(returnMaxEntity.getHomeFoul());
		averageFeatureEntity.setOppositeFoul(returnMaxEntity.getAwayFoul());
		averageFeatureEntity.setYellowCard(returnMaxEntity.getHomeYellowCard());
		averageFeatureEntity.setOppositeYellowCard(returnMaxEntity.getAwayYellowCard());
		averageFeatureEntity.setRedCard(returnMaxEntity.getHomeRedCard());
		averageFeatureEntity.setOppositeRedCard(returnMaxEntity.getAwayRedCard());
		averageFeatureEntity.setSlowIn(returnMaxEntity.getHomeSlowIn());
		averageFeatureEntity.setOppositeSlowIn(returnMaxEntity.getAwaySlowIn());
		averageFeatureEntity.setBoxTouch(returnMaxEntity.getHomeBoxTouch());
		averageFeatureEntity.setOppositeBoxTouch(returnMaxEntity.getAwayBoxTouch());
		averageFeatureEntity.setPassCountSuccessRatio(homePassList.get(0));
		averageFeatureEntity.setPassCountSuccessCount(homePassList.get(1));
		averageFeatureEntity.setPassCountTryCount(homePassList.get(2));
		averageFeatureEntity.setOppositePassCountSuccessRatio(awayPassList.get(0));
		averageFeatureEntity.setOppositePassCountSuccessCount(awayPassList.get(1));
		averageFeatureEntity.setOppositePassCountTryCount(awayPassList.get(2));
		averageFeatureEntity.setFinalThirdPassCountSuccessRatio(homeFinalThirdPassList.get(0));
		averageFeatureEntity.setFinalThirdPassCountSuccessCount(homeFinalThirdPassList.get(1));
		averageFeatureEntity.setFinalThirdPassCountTryCount(homeFinalThirdPassList.get(2));
		averageFeatureEntity.setOppositeFinalThirdPassCountSuccessRatio(awayFinalThirdPassList.get(0));
		averageFeatureEntity.setOppositeFinalThirdPassCountSuccessCount(awayFinalThirdPassList.get(1));
		averageFeatureEntity.setOppositeFinalThirdPassCountTryCount(awayFinalThirdPassList.get(2));
		averageFeatureEntity.setCrossCountSuccessRatio(homeCrossList.get(0));
		averageFeatureEntity.setCrossCountSuccessCount(homeCrossList.get(1));
		averageFeatureEntity.setCrossCountTryCount(homeCrossList.get(2));
		averageFeatureEntity.setOppositeCrossCountSuccessRatio(awayCrossList.get(0));
		averageFeatureEntity.setOppositeCrossCountSuccessCount(awayCrossList.get(1));
		averageFeatureEntity.setOppositeCrossCountTryCount(awayCrossList.get(2));
		averageFeatureEntity.setTackleCountSuccessRatio(homeTackleList.get(0));
		averageFeatureEntity.setTackleCountSuccessCount(homeTackleList.get(1));
		averageFeatureEntity.setTackleCountTryCount(homeTackleList.get(2));
		averageFeatureEntity.setOppositeTackleCountSuccessRatio(awayTackleList.get(0));
		averageFeatureEntity.setOppositeTackleCountSuccessCount(awayTackleList.get(1));
		averageFeatureEntity.setOppositeTackleCountTryCount(awayTackleList.get(2));
		averageFeatureEntity.setClearCount(returnMaxEntity.getHomeClearCount());
		averageFeatureEntity.setOppositeClearCount(returnMaxEntity.getAwayClearCount());
		averageFeatureEntity.setInterceptCount(returnMaxEntity.getHomeInterceptCount());
		averageFeatureEntity.setOppositeInterceptCount(returnMaxEntity.getAwayInterceptCount());
		averageFeatureEntity.setWeather(returnMaxEntity.getWeather());
		averageFeatureEntity.setTemparature(returnMaxEntity.getTemparature());
		averageFeatureEntity.setHumid(returnMaxEntity.getHumid());
		insertEntities.add(averageFeatureEntity);

		averageFeatureEntity = new TeamMatchFinalStatsEntity();
		averageFeatureEntity.setTeamName(away);
		averageFeatureEntity.setVersusTeamName("vs" + home);
		averageFeatureEntity.setHa("A");
		result = compareScore(returnMaxEntity.getAwayScore(), returnMaxEntity.getHomeScore());
		mark = setSymbol(result);
		averageFeatureEntity.setScore(mark + returnMaxEntity.getAwayScore() + "-" + returnMaxEntity.getHomeScore());
		averageFeatureEntity.setResult(result);
		averageFeatureEntity.setGameFinRank(returnMaxEntity.getAwayRank());
		averageFeatureEntity.setOppositeGameFinRank(returnMaxEntity.getHomeRank());
		averageFeatureEntity.setExp(returnMaxEntity.getAwayExp());
		averageFeatureEntity.setOppositeExp(returnMaxEntity.getHomeExp());
		averageFeatureEntity.setDonation(aveAwayDonation);
		averageFeatureEntity.setOppositeDonation(aveHomeDonation);
		averageFeatureEntity.setShootAll(returnMaxEntity.getAwayShootAll());
		averageFeatureEntity.setOppositeShootAll(returnMaxEntity.getHomeShootAll());
		averageFeatureEntity.setShootIn(returnMaxEntity.getAwayShootIn());
		averageFeatureEntity.setOppositeShootIn(returnMaxEntity.getHomeShootIn());
		averageFeatureEntity.setShootOut(returnMaxEntity.getAwayShootOut());
		averageFeatureEntity.setOppositeShootOut(returnMaxEntity.getHomeShootOut());
		averageFeatureEntity.setBlockShoot(returnMaxEntity.getAwayBlockShoot());
		averageFeatureEntity.setOppositeBlockShoot(returnMaxEntity.getHomeBlockShoot());
		averageFeatureEntity.setBigChance(returnMaxEntity.getAwayBigChance());
		averageFeatureEntity.setOppositeBigChance(returnMaxEntity.getHomeBigChance());
		averageFeatureEntity.setCorner(returnMaxEntity.getAwayCorner());
		averageFeatureEntity.setOppositeCorner(returnMaxEntity.getHomeCorner());
		averageFeatureEntity.setBoxShootIn(returnMaxEntity.getAwayBoxShootIn());
		averageFeatureEntity.setOppositeBoxShootIn(returnMaxEntity.getHomeBoxShootIn());
		averageFeatureEntity.setBoxShootOut(returnMaxEntity.getAwayBoxShootOut());
		averageFeatureEntity.setOppositeBoxShootOut(returnMaxEntity.getHomeBoxShootOut());
		averageFeatureEntity.setGoalPost(returnMaxEntity.getAwayGoalPost());
		averageFeatureEntity.setOppositeGoalPost(returnMaxEntity.getHomeGoalPost());
		averageFeatureEntity.setGoalHead(returnMaxEntity.getAwayGoalHead());
		averageFeatureEntity.setOppositeGoalHead(returnMaxEntity.getHomeGoalHead());
		averageFeatureEntity.setKeeperSave(returnMaxEntity.getAwayKeeperSave());
		averageFeatureEntity.setOppositeKeeperSave(returnMaxEntity.getHomeKeeperSave());
		averageFeatureEntity.setFreeKick(returnMaxEntity.getAwayFreeKick());
		averageFeatureEntity.setOppositeFreeKick(returnMaxEntity.getHomeFreeKick());
		averageFeatureEntity.setOffside(returnMaxEntity.getAwayOffside());
		averageFeatureEntity.setOppositeOffside(returnMaxEntity.getHomeOffside());
		averageFeatureEntity.setFoul(returnMaxEntity.getAwayFoul());
		averageFeatureEntity.setOppositeFoul(returnMaxEntity.getHomeFoul());
		averageFeatureEntity.setYellowCard(returnMaxEntity.getAwayYellowCard());
		averageFeatureEntity.setOppositeYellowCard(returnMaxEntity.getHomeYellowCard());
		averageFeatureEntity.setRedCard(returnMaxEntity.getAwayRedCard());
		averageFeatureEntity.setOppositeRedCard(returnMaxEntity.getHomeRedCard());
		averageFeatureEntity.setSlowIn(returnMaxEntity.getAwaySlowIn());
		averageFeatureEntity.setOppositeSlowIn(returnMaxEntity.getHomeSlowIn());
		averageFeatureEntity.setBoxTouch(returnMaxEntity.getAwayBoxTouch());
		averageFeatureEntity.setOppositeBoxTouch(returnMaxEntity.getHomeBoxTouch());
		averageFeatureEntity.setPassCountSuccessRatio(awayPassList.get(0));
		averageFeatureEntity.setPassCountSuccessCount(awayPassList.get(1));
		averageFeatureEntity.setPassCountTryCount(awayPassList.get(2));
		averageFeatureEntity.setOppositePassCountSuccessRatio(homePassList.get(0));
		averageFeatureEntity.setOppositePassCountSuccessCount(homePassList.get(1));
		averageFeatureEntity.setOppositePassCountTryCount(homePassList.get(2));
		averageFeatureEntity.setFinalThirdPassCountSuccessRatio(awayFinalThirdPassList.get(0));
		averageFeatureEntity.setFinalThirdPassCountSuccessCount(awayFinalThirdPassList.get(1));
		averageFeatureEntity.setFinalThirdPassCountTryCount(awayFinalThirdPassList.get(2));
		averageFeatureEntity.setOppositeFinalThirdPassCountSuccessRatio(homeFinalThirdPassList.get(0));
		averageFeatureEntity.setOppositeFinalThirdPassCountSuccessCount(homeFinalThirdPassList.get(1));
		averageFeatureEntity.setOppositeFinalThirdPassCountTryCount(homeFinalThirdPassList.get(2));
		averageFeatureEntity.setCrossCountSuccessRatio(awayCrossList.get(0));
		averageFeatureEntity.setCrossCountSuccessCount(awayCrossList.get(1));
		averageFeatureEntity.setCrossCountTryCount(awayCrossList.get(2));
		averageFeatureEntity.setOppositeCrossCountSuccessRatio(homeCrossList.get(0));
		averageFeatureEntity.setOppositeCrossCountSuccessCount(homeCrossList.get(1));
		averageFeatureEntity.setOppositeCrossCountTryCount(homeCrossList.get(2));
		averageFeatureEntity.setTackleCountSuccessRatio(awayTackleList.get(0));
		averageFeatureEntity.setTackleCountSuccessCount(awayTackleList.get(1));
		averageFeatureEntity.setTackleCountTryCount(awayTackleList.get(2));
		averageFeatureEntity.setOppositeTackleCountSuccessRatio(homeTackleList.get(0));
		averageFeatureEntity.setOppositeTackleCountSuccessCount(homeTackleList.get(1));
		averageFeatureEntity.setOppositeTackleCountTryCount(homeTackleList.get(2));
		averageFeatureEntity.setClearCount(returnMaxEntity.getAwayClearCount());
		averageFeatureEntity.setOppositeClearCount(returnMaxEntity.getHomeClearCount());
		averageFeatureEntity.setInterceptCount(returnMaxEntity.getAwayInterceptCount());
		averageFeatureEntity.setOppositeInterceptCount(returnMaxEntity.getHomeInterceptCount());
		averageFeatureEntity.setWeather(returnMaxEntity.getWeather());
		averageFeatureEntity.setTemparature(returnMaxEntity.getTemparature());
		averageFeatureEntity.setHumid(returnMaxEntity.getHumid());
		insertEntities.add(averageFeatureEntity);

		executeAverageFeatureMain(insertEntities);
	}

	/**
	 *
	 * @param insertEntities
	 */
	public static void executeAverageFeatureMain(List<TeamMatchFinalStatsEntity> insertEntities) {

		AverageFeatureDbInsert averageFeatureDbInsert = new AverageFeatureDbInsert();
		averageFeatureDbInsert.execute(insertEntities);
	}

	/**
	 * 加算メソッド
	 * @param origin
	 * @param feature
	 * @param counter
	 * @return
	 */
	private AverageFeatureOutputDTO sumFeature(int origin, String feature, int counter) {
		AverageFeatureOutputDTO dto = new AverageFeatureOutputDTO();
		if (feature == null || "".equals(feature)) {
			dto.setSum(origin);
			dto.setCounter(counter);
		} else {
			if (feature.contains("%")) {
				feature = feature.replace("%", "");
			}
			origin += Integer.parseInt(feature);
			dto.setSum(origin);
			dto.setCounter(counter + 1);
		}
		return dto;
	}

	/**
	 * スコア比較
	 * @param homeScore
	 * @param awayScore
	 * @return
	 */
	private String compareScore(String homeScore, String awayScore) {
		String result = "";
		if (homeScore.compareTo(awayScore) > 0) {
			result = "WIN";
		} else if (homeScore.compareTo(awayScore) < 0) {
			result = "LOSE";
		} else {
			result = "DRAW";
		}
		return result;
	}

	/**
	 * 勝敗のマークを返す
	 * @param homeScore
	 * @param awayScore
	 * @return
	 */
	private String setSymbol(String result) {
		String mark = "";
		if ("WIN".equals(result)) {
			mark = "○";
		} else if ("LOSE".equals(result)) {
			mark = "●";
		} else {
			mark = "△";
		}
		return mark;
	}

}
