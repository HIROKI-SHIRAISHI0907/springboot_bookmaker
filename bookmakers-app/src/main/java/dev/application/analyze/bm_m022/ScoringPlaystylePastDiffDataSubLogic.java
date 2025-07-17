package dev.application.analyze.bm_m022;

import java.util.ArrayList;
import java.util.List;

import dev.application.analyze.bm_m023.DecidePlaystyle;
import dev.application.analyze.common.entity.ThresHoldEntity;
import dev.application.analyze.common.util.ExecuteMainUtil;


/**
 * リアルタイムの直近のデータを比べ得点パターンとチームのプレースタイルを予想するサブロジック<br>
 * プレースタイル決定後,各プレースタイル同士の対戦について得点が生まれているかを確認する
 * @author shiraishitoshio
 *
 */
public class ScoringPlaystylePastDiffDataSubLogic {

	/**
	 * @param entityList CSV読み込みEntityリスト
	 * @param halfList ハーフリスト
	 * @throws Exception
	 */
	public void execute(List<ThresHoldEntity> entityList, String file)
			throws Exception {

		// プレースタイル判定部品
		DecidePlaystyle decidePlaystyle = new DecidePlaystyle();

		// カテゴリ
		String dataCategory = entityList.get(0).getDataCategory();
		// チーム名
		String home = entityList.get(0).getHomeTeamName();
		String away = entityList.get(0).getAwayTeamName();

		List<StatsDiffEntity> diffFeatureEntities = new ArrayList<StatsDiffEntity>();
		for (int entityInd = 0; entityInd < entityList.size() - 1; entityInd++) {
			// DiffEntityインスタンスを生成
			StatsDiffEntity diffEntity = new StatsDiffEntity();

			diffEntity.setDataCategory(dataCategory);
			diffEntity.setHomeTeamName(home);
			diffEntity.setAwayTeamName(away);

			// ホームフィールドの差分を格納
			diffEntity.setDiffHomeScore(diffData(
					entityList.get(entityInd + 1).getHomeScore(),
					entityList.get(entityInd).getHomeScore()));
			diffEntity.setDiffHomeDonation(diffData(
					entityList.get(entityInd + 1).getHomeDonation(),
					entityList.get(entityInd).getHomeDonation()));
			diffEntity.setDiffHomeShootAll(diffData(
					entityList.get(entityInd + 1).getHomeShootAll(),
					entityList.get(entityInd).getHomeShootAll()));
			diffEntity.setDiffHomeShootIn(diffData(
					entityList.get(entityInd + 1).getHomeShootIn(),
					entityList.get(entityInd).getHomeShootIn()));
			diffEntity.setDiffHomeShootOut(diffData(
					entityList.get(entityInd + 1).getHomeShootOut(),
					entityList.get(entityInd).getHomeShootOut()));
			diffEntity.setDiffHomeBlockShoot(diffData(
					entityList.get(entityInd + 1).getHomeBlockShoot(),
					entityList.get(entityInd).getHomeBlockShoot()));
			diffEntity.setDiffHomeBigChance(diffData(
					entityList.get(entityInd + 1).getHomeBigChance(),
					entityList.get(entityInd).getHomeBigChance()));
			diffEntity.setDiffHomeCorner(diffData(
					entityList.get(entityInd + 1).getHomeCorner(),
					entityList.get(entityInd).getHomeCorner()));
			diffEntity.setDiffHomeBoxShootIn(diffData(
					entityList.get(entityInd + 1).getHomeBoxShootIn(),
					entityList.get(entityInd).getHomeBoxShootIn()));
			diffEntity.setDiffHomeBoxShootOut(diffData(
					entityList.get(entityInd + 1).getHomeBoxShootOut(),
					entityList.get(entityInd).getHomeBoxShootOut()));
			diffEntity.setDiffHomeGoalPost(diffData(
					entityList.get(entityInd + 1).getHomeGoalPost(),
					entityList.get(entityInd).getHomeGoalPost()));
			diffEntity.setDiffHomeGoalHead(diffData(
					entityList.get(entityInd + 1).getHomeGoalHead(),
					entityList.get(entityInd).getHomeGoalHead()));
			diffEntity.setDiffHomeKeeperSave(diffData(
					entityList.get(entityInd + 1).getHomeKeeperSave(),
					entityList.get(entityInd).getHomeKeeperSave()));
			diffEntity.setDiffHomeFreeKick(diffData(
					entityList.get(entityInd + 1).getHomeFreeKick(),
					entityList.get(entityInd).getHomeFreeKick()));
			diffEntity.setDiffHomeOffside(diffData(
					entityList.get(entityInd + 1).getHomeOffside(),
					entityList.get(entityInd).getHomeOffside()));
			diffEntity.setDiffHomeFoul(diffData(
					entityList.get(entityInd + 1).getHomeFoul(),
					entityList.get(entityInd).getHomeFoul()));
			diffEntity.setDiffHomeYellowCard(diffData(
					entityList.get(entityInd + 1).getHomeYellowCard(),
					entityList.get(entityInd).getHomeYellowCard()));
			diffEntity.setDiffHomeRedCard(diffData(
					entityList.get(entityInd + 1).getHomeRedCard(),
					entityList.get(entityInd).getHomeRedCard()));
			diffEntity.setDiffHomeSlowIn(diffData(
					entityList.get(entityInd + 1).getHomeSlowIn(),
					entityList.get(entityInd).getHomeSlowIn()));
			diffEntity.setDiffHomeBoxTouch(diffData(
					entityList.get(entityInd + 1).getHomeBoxTouch(),
					entityList.get(entityInd).getHomeBoxTouch()));
			diffEntity.setDiffHomePassCount(threeSplitDiffData(
					entityList.get(entityInd + 1).getHomePassCount(),
					entityList.get(entityInd).getHomePassCount()));
			diffEntity.setDiffHomeFinalThirdPassCount(threeSplitDiffData(
					entityList.get(entityInd + 1).getHomeFinalThirdPassCount(),
					entityList.get(entityInd).getHomeFinalThirdPassCount()));
			diffEntity.setDiffHomeCrossCount(threeSplitDiffData(
					entityList.get(entityInd + 1).getHomeCrossCount(),
					entityList.get(entityInd).getHomeCrossCount()));
			diffEntity.setDiffHomeTackleCount(threeSplitDiffData(
					entityList.get(entityInd + 1).getHomeTackleCount(),
					entityList.get(entityInd).getHomeTackleCount()));
			diffEntity.setDiffHomeClearCount(diffData(
					entityList.get(entityInd + 1).getHomeClearCount(),
					entityList.get(entityInd).getHomeClearCount()));
			diffEntity.setDiffHomeInterceptCount(diffData(
					entityList.get(entityInd + 1).getHomeInterceptCount(),
					entityList.get(entityInd).getHomeInterceptCount()));

			// アウェーフィールドの差分を格納
			diffEntity.setDiffAwayScore(diffData(
					entityList.get(entityInd + 1).getAwayScore(),
					entityList.get(entityInd).getAwayScore()));
			diffEntity.setDiffAwayDonation(diffData(
					entityList.get(entityInd + 1).getAwayDonation(),
					entityList.get(entityInd).getAwayDonation()));
			diffEntity.setDiffAwayShootAll(diffData(
					entityList.get(entityInd + 1).getAwayShootAll(),
					entityList.get(entityInd).getAwayShootAll()));
			diffEntity.setDiffAwayShootIn(diffData(
					entityList.get(entityInd + 1).getAwayShootIn(),
					entityList.get(entityInd).getAwayShootIn()));
			diffEntity.setDiffAwayShootOut(diffData(
					entityList.get(entityInd + 1).getAwayShootOut(),
					entityList.get(entityInd).getAwayShootOut()));
			diffEntity.setDiffAwayBlockShoot(diffData(
					entityList.get(entityInd + 1).getAwayBlockShoot(),
					entityList.get(entityInd).getAwayBlockShoot()));
			diffEntity.setDiffAwayBigChance(diffData(
					entityList.get(entityInd + 1).getAwayBigChance(),
					entityList.get(entityInd).getAwayBigChance()));
			diffEntity.setDiffAwayCorner(diffData(
					entityList.get(entityInd + 1).getAwayCorner(),
					entityList.get(entityInd).getAwayCorner()));
			diffEntity.setDiffAwayBoxShootIn(diffData(
					entityList.get(entityInd + 1).getAwayBoxShootIn(),
					entityList.get(entityInd).getAwayBoxShootIn()));
			diffEntity.setDiffAwayBoxShootOut(diffData(
					entityList.get(entityInd + 1).getAwayBoxShootOut(),
					entityList.get(entityInd).getAwayBoxShootOut()));
			diffEntity.setDiffAwayGoalPost(diffData(
					entityList.get(entityInd + 1).getAwayGoalPost(),
					entityList.get(entityInd).getAwayGoalPost()));
			diffEntity.setDiffAwayGoalHead(diffData(
					entityList.get(entityInd + 1).getAwayGoalHead(),
					entityList.get(entityInd).getAwayGoalHead()));
			diffEntity.setDiffAwayKeeperSave(diffData(
					entityList.get(entityInd + 1).getAwayKeeperSave(),
					entityList.get(entityInd).getAwayKeeperSave()));
			diffEntity.setDiffAwayFreeKick(diffData(
					entityList.get(entityInd + 1).getAwayFreeKick(),
					entityList.get(entityInd).getAwayFreeKick()));
			diffEntity.setDiffAwayOffside(diffData(
					entityList.get(entityInd + 1).getAwayOffside(),
					entityList.get(entityInd).getAwayOffside()));
			diffEntity.setDiffAwayFoul(diffData(
					entityList.get(entityInd + 1).getAwayFoul(),
					entityList.get(entityInd).getAwayFoul()));
			diffEntity.setDiffAwayYellowCard(diffData(
					entityList.get(entityInd + 1).getAwayYellowCard(),
					entityList.get(entityInd).getAwayYellowCard()));
			diffEntity.setDiffAwayRedCard(diffData(
					entityList.get(entityInd + 1).getAwayRedCard(),
					entityList.get(entityInd).getAwayRedCard()));
			diffEntity.setDiffAwaySlowIn(diffData(
					entityList.get(entityInd + 1).getAwaySlowIn(),
					entityList.get(entityInd).getAwaySlowIn()));
			diffEntity.setDiffAwayBoxTouch(diffData(
					entityList.get(entityInd + 1).getAwayBoxTouch(),
					entityList.get(entityInd).getAwayBoxTouch()));
			diffEntity.setDiffAwayPassCount(threeSplitDiffData(
					entityList.get(entityInd + 1).getAwayPassCount(),
					entityList.get(entityInd).getAwayPassCount()));
			diffEntity.setDiffAwayFinalThirdPassCount(threeSplitDiffData(
					entityList.get(entityInd + 1).getAwayFinalThirdPassCount(),
					entityList.get(entityInd).getAwayFinalThirdPassCount()));
			diffEntity.setDiffAwayCrossCount(threeSplitDiffData(
					entityList.get(entityInd + 1).getAwayCrossCount(),
					entityList.get(entityInd).getAwayCrossCount()));
			diffEntity.setDiffAwayTackleCount(threeSplitDiffData(
					entityList.get(entityInd + 1).getAwayTackleCount(),
					entityList.get(entityInd).getAwayTackleCount()));
			diffEntity.setDiffAwayClearCount(diffData(
					entityList.get(entityInd + 1).getAwayClearCount(),
					entityList.get(entityInd).getAwayClearCount()));
			diffEntity.setDiffAwayInterceptCount(diffData(
					entityList.get(entityInd + 1).getAwayInterceptCount(),
					entityList.get(entityInd).getAwayInterceptCount()));

			List<String> playStyle = decidePlaystyle.execute(diffEntity, entityList.get(entityInd + 1));

			diffEntity.setHomePlayStyle(playStyle.get(0));
			diffEntity.setAwayPlayStyle(playStyle.get(1));

			diffFeatureEntities.add(diffEntity);
		}

		executeDiffDataMain(diffFeatureEntities);
	}

	/**
	 *
	 * @param insertEntities
	 */
	public static void executeDiffDataMain(List<StatsDiffEntity> insertEntities) {
		ScoringPlaystylePastDataDbInsert scoringPlaystylePastDataDbInsert = new ScoringPlaystylePastDataDbInsert();
		scoringPlaystylePastDataDbInsert.execute(insertEntities);
	}

	/**
	 * 加算メソッド
	 * @param befFeature
	 * @param afFeature
	 * @return
	 */
	private String diffData(String afFeature, String befFeature) {
		String hozon = "";
		String diff = "";
		if ((befFeature == null || "".equals(befFeature) || " ".equals(befFeature)) ||
				(afFeature == null || "".equals(afFeature) || " ".equals(afFeature))) {
			diff = "-";
		} else {
			if (befFeature.contains("%")) {
				hozon = "%";
				befFeature = befFeature.replace("%", "");
			}
			if (afFeature.contains("%")) {
				hozon = "%";
				afFeature = afFeature.replace("%", "");
			}
			int diffInt = Integer.parseInt(afFeature) - Integer.parseInt(befFeature);
			diff = String.valueOf(diffInt + hozon);
			if (diffInt > 0) {
				diff = String.valueOf("+" + diffInt + hozon);
			}
		}
		return diff;
	}

	/**
	 * 加算メソッド
	 * @param befFeature
	 * @param afFeature
	 * @return
	 */
	private String threeSplitDiffData(String afFeature, String befFeature) {
		String diffAll = "";
		if ((befFeature == null || "".equals(befFeature) || " ".equals(befFeature)) ||
				(afFeature == null || "".equals(afFeature) || " ".equals(afFeature))) {
			diffAll = "-";
		} else {
			List<String> befFeatureList = ExecuteMainUtil.splitGroup(befFeature);
			List<String> afFeatureList = ExecuteMainUtil.splitGroup(afFeature);
			List<String> list = new ArrayList<String>();
			for (int ind = 0; ind < befFeatureList.size(); ind++) {
				String diff = diffData(afFeatureList.get(ind), befFeatureList.get(ind));
				list.add(diff);
			}
			diffAll = list.get(0);
			if (list.size() == 3) {
				diffAll += " (" + list.get(1) + "/" + list.get(2) + ")";
			}
		}
		return diffAll;
	}

}
