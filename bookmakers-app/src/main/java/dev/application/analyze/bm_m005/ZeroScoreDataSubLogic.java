package dev.application.analyze.bm_m005;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import dev.application.analyze.common.entity.ThresHoldEntity;
import dev.application.analyze.common.util.ExecuteMainUtil;


/**
 * 無得点マスタサブロジック
 * @author shiraishitoshio
 *
 */
public class ZeroScoreDataSubLogic {

	/**
	 * @param entityList CSV読み込みEntityリスト
	 * @param halfList ハーフリスト
	 * @throws Exception
	 */
	public void execute(List<ThresHoldEntity> entityList, String file)
			throws Exception {

		List<String> seqList = new ArrayList<String>();

		List<NoGoalMatchStatisticsEntity> insertEntities = new ArrayList<NoGoalMatchStatisticsEntity>();
		// 最大の通番を持つ時間を返却する
		ThresHoldEntity returnMaxEntity = ExecuteMainUtil.getMaxSeqEntities(entityList);
		int maxHomeScore = Integer.parseInt(returnMaxEntity.getHomeScore());
		int maxAwayScore = Integer.parseInt(returnMaxEntity.getAwayScore());

		if (maxHomeScore == 0 && maxAwayScore == 0) {
			// 何もしない
		} else {
			System.out.println("無得点ではないためスキップ: file: " + file);
			return;
		}

		ThresHoldEntity returnHalfEntity = ExecuteMainUtil.getHalfEntities(entityList);
		ThresHoldEntity returnMinEntity = ExecuteMainUtil.getMinSeqEntities(entityList);

		insertEntities.add(mappingEntity(returnMinEntity));
		seqList.add(returnMinEntity.getSeq());
		if (!seqList.contains(returnHalfEntity.getSeq())) {
			insertEntities.add(mappingEntity(returnHalfEntity));
		}
		seqList.add(returnHalfEntity.getSeq());
		if (!seqList.contains(returnMaxEntity.getSeq())) {
			insertEntities.add(mappingEntity(returnMaxEntity));
		}

		executeNoScoredMain(insertEntities);
	}

	/**
	 *
	 * @param insertEntities
	 */
	public static void executeNoScoredMain(List<NoGoalMatchStatisticsEntity> insertEntities) {

		NoScoredDbInsert noScoredDbInsert = new NoScoredDbInsert();
		noScoredDbInsert.execute(insertEntities);
	}

	/**
	 * クラスに対応する情報を設定するメソッド
	 *
	 * @param innerSeq 内部シーケンス
	 * @param thresholdEntity ThresHoldEntityのインスタンス
	 * @return 設定されたClassifyResultDataEntity
	 */
	private NoGoalMatchStatisticsEntity mappingEntity(ThresHoldEntity thresholdEntity) {
		NoGoalMatchStatisticsEntity mappingDto = new NoGoalMatchStatisticsEntity();
		// 各フィールドにThresHoldEntityのゲッターを使ってデータを設定
		mappingDto.setSeq(thresholdEntity.getSeq());
		mappingDto.setDataCategory(thresholdEntity.getDataCategory());
		mappingDto.setTimes(thresholdEntity.getTimes());
		mappingDto.setHomeRank(thresholdEntity.getHomeRank());
		mappingDto.setHomeTeamName(thresholdEntity.getHomeTeamName());
		mappingDto.setHomeScore(thresholdEntity.getHomeScore());
		mappingDto.setAwayRank(thresholdEntity.getAwayRank());
		mappingDto.setAwayTeamName(thresholdEntity.getAwayTeamName());
		mappingDto.setAwayScore(thresholdEntity.getAwayScore());
		mappingDto.setHomeExp(thresholdEntity.getHomeExp());
		mappingDto.setAwayExp(thresholdEntity.getAwayExp());
		mappingDto.setHomeDonation(thresholdEntity.getHomeDonation());
		mappingDto.setAwayDonation(thresholdEntity.getAwayDonation());
		mappingDto.setHomeShootAll(thresholdEntity.getHomeShootAll());
		mappingDto.setAwayShootAll(thresholdEntity.getAwayShootAll());
		mappingDto.setHomeShootIn(thresholdEntity.getHomeShootIn());
		mappingDto.setAwayShootIn(thresholdEntity.getAwayShootIn());
		mappingDto.setHomeShootOut(thresholdEntity.getHomeShootOut());
		mappingDto.setAwayShootOut(thresholdEntity.getAwayShootOut());
		mappingDto.setHomeBlockShoot(thresholdEntity.getHomeBlockShoot());
		mappingDto.setAwayBlockShoot(thresholdEntity.getAwayBlockShoot());
		mappingDto.setHomeBigChance(thresholdEntity.getHomeBigChance());
		mappingDto.setAwayBigChance(thresholdEntity.getAwayBigChance());
		mappingDto.setHomeCorner(thresholdEntity.getHomeCorner());
		mappingDto.setAwayCorner(thresholdEntity.getAwayCorner());
		mappingDto.setHomeBoxShootIn(thresholdEntity.getHomeBoxShootIn());
		mappingDto.setAwayBoxShootIn(thresholdEntity.getAwayBoxShootIn());
		mappingDto.setHomeBoxShootOut(thresholdEntity.getHomeBoxShootOut());
		mappingDto.setAwayBoxShootOut(thresholdEntity.getAwayBoxShootOut());
		mappingDto.setHomeGoalPost(thresholdEntity.getHomeGoalPost());
		mappingDto.setAwayGoalPost(thresholdEntity.getAwayGoalPost());
		mappingDto.setHomeGoalHead(thresholdEntity.getHomeGoalHead());
		mappingDto.setAwayGoalHead(thresholdEntity.getAwayGoalHead());
		mappingDto.setHomeKeeperSave(thresholdEntity.getHomeKeeperSave());
		mappingDto.setAwayKeeperSave(thresholdEntity.getAwayKeeperSave());
		mappingDto.setHomeFreeKick(thresholdEntity.getHomeFreeKick());
		mappingDto.setAwayFreeKick(thresholdEntity.getAwayFreeKick());
		mappingDto.setHomeOffside(thresholdEntity.getHomeOffside());
		mappingDto.setAwayOffside(thresholdEntity.getAwayOffside());
		mappingDto.setHomeFoul(thresholdEntity.getHomeFoul());
		mappingDto.setAwayFoul(thresholdEntity.getAwayFoul());
		mappingDto.setHomeYellowCard(thresholdEntity.getHomeYellowCard());
		mappingDto.setAwayYellowCard(thresholdEntity.getAwayYellowCard());
		mappingDto.setHomeRedCard(thresholdEntity.getHomeRedCard());
		mappingDto.setAwayRedCard(thresholdEntity.getAwayRedCard());
		mappingDto.setHomeSlowIn(thresholdEntity.getHomeSlowIn());
		mappingDto.setAwaySlowIn(thresholdEntity.getAwaySlowIn());
		mappingDto.setHomeBoxTouch(thresholdEntity.getHomeBoxTouch());
		mappingDto.setAwayBoxTouch(thresholdEntity.getAwayBoxTouch());
		mappingDto.setHomePassCount(thresholdEntity.getHomePassCount());
		mappingDto.setAwayPassCount(thresholdEntity.getAwayPassCount());
		mappingDto.setHomeFinalThirdPassCount(thresholdEntity.getHomeFinalThirdPassCount());
		mappingDto.setAwayFinalThirdPassCount(thresholdEntity.getAwayFinalThirdPassCount());
		mappingDto.setHomeCrossCount(thresholdEntity.getHomeCrossCount());
		mappingDto.setAwayCrossCount(thresholdEntity.getAwayCrossCount());
		mappingDto.setHomeTackleCount(thresholdEntity.getHomeTackleCount());
		mappingDto.setAwayTackleCount(thresholdEntity.getAwayTackleCount());
		mappingDto.setHomeClearCount(thresholdEntity.getHomeClearCount());
		mappingDto.setAwayClearCount(thresholdEntity.getAwayClearCount());
		mappingDto.setHomeInterceptCount(thresholdEntity.getHomeInterceptCount());
		mappingDto.setAwayInterceptCount(thresholdEntity.getAwayInterceptCount());
		mappingDto.setRecordTime(Timestamp.valueOf(thresholdEntity.getRecordTime()));
		mappingDto.setWeather(thresholdEntity.getWeather());
		mappingDto.setTemparature(thresholdEntity.getTemparature());
		mappingDto.setHumid(thresholdEntity.getHumid());
		mappingDto.setJudgeMember(thresholdEntity.getJudgeMember());
		mappingDto.setHomeManager(thresholdEntity.getHomeManager());
		mappingDto.setAwayManager(thresholdEntity.getAwayManager());
		mappingDto.setHomeFormation(thresholdEntity.getHomeFormation());
		mappingDto.setAwayFormation(thresholdEntity.getAwayFormation());
		mappingDto.setStudium(thresholdEntity.getStudium());
		mappingDto.setCapacity(thresholdEntity.getCapacity());
		mappingDto.setAudience(thresholdEntity.getAudience());
		mappingDto.setHomeMaxGettingScorer(thresholdEntity.getHomeMaxGettingScorer());
		mappingDto.setAwayMaxGettingScorer(thresholdEntity.getAwayMaxGettingScorer());
		mappingDto.setHomeMaxGettingScorerGameSituation(thresholdEntity.getHomeMaxGettingScorerGameSituation());
		mappingDto.setAwayMaxGettingScorerGameSituation(thresholdEntity.getAwayMaxGettingScorerGameSituation());
		mappingDto.setHomeTeamHomeScore(thresholdEntity.getHomeTeamHomeScore());
		mappingDto.setHomeTeamHomeLost(thresholdEntity.getHomeTeamHomeLost());
		mappingDto.setAwayTeamHomeScore(thresholdEntity.getAwayTeamHomeScore());
		mappingDto.setAwayTeamHomeLost(thresholdEntity.getAwayTeamHomeLost());
		mappingDto.setHomeTeamAwayScore(thresholdEntity.getHomeTeamAwayScore());
		mappingDto.setHomeTeamAwayLost(thresholdEntity.getHomeTeamAwayLost());
		mappingDto.setAwayTeamAwayScore(thresholdEntity.getAwayTeamAwayScore());
		mappingDto.setAwayTeamAwayLost(thresholdEntity.getAwayTeamAwayLost());
		mappingDto.setNoticeFlg(thresholdEntity.getNoticeFlg());
		mappingDto.setGoalTime(thresholdEntity.getGoalTime());
		mappingDto.setGoalTeamMember(thresholdEntity.getGoalTeamMember());
		mappingDto.setJudge(thresholdEntity.getJudge());
		mappingDto.setHomeTeamStyle(thresholdEntity.getHomeTeamStyle());
		mappingDto.setAwayTeamStyle(thresholdEntity.getAwayTeamStyle());
		mappingDto.setProbablity(thresholdEntity.getProbablity());
		mappingDto.setPredictionScoreTime(thresholdEntity.getPredictionScoreTime());

		return mappingDto;
	}

}
