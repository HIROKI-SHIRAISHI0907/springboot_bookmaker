package dev.application.analyze.bm_m024;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import dev.application.analyze.bm_m023.AverageStatisticsSituationConst;
import dev.application.analyze.bm_m023.CorrelationSummary;
import dev.application.analyze.common.entity.ThresHoldEntity;
import dev.application.analyze.common.util.ExecuteMainUtil;
import dev.common.constant.BookMakersCommonConst;
import dev.common.constant.UniairConst;
import dev.common.exception.SystemException;

/**
 * 各得点における相関係数を導出するロジック(最も得点に起因しているスタッツは何かを調査する)
 * @author shiraishitoshio
 *
 */
public class CalcCorrelationSubLogic {

	/**
	 * 全体データ
	 */
	private static final String ALL_DATA = "ALL";

	/**
	 * 前半データ
	 */
	private static final String FIRST_DATA = "1st";

	/**
	 * 後半データ
	 */
	private static final String SECOND_DATA = "2nd";

	/**
	 * フラグ(前半,後半単位)
	 */
	private static final String HALF_SCORE = "1";

	/**
	 * フラグ(前半)
	 */
	private static final String FIRST_HALF_SCORE = "2";

	/**
	 * フラグ(後半)
	 */
	private static final String SECOND_HALF_SCORE = "3";

	/**
	 * 実行
	 * @param entityList
	 * @param file
	 * @throws IllegalAccessException
	 * @throws IllegalArgumentException
	 * @throws SecurityException
	 * @throws NoSuchFieldException
	 */
	public void execute(List<ThresHoldEntity> entityList, String file)
			throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, SecurityException {

		// 取得したデータリストについて最終スコアを確認する
		ThresHoldEntity returnMaxEntity = ExecuteMainUtil.getMaxSeqEntities(entityList);
		// 国,リーグ
		String[] data_category = ExecuteMainUtil.splitLeagueInfo(returnMaxEntity.getDataCategory());
		String country = data_category[0];
		String league = data_category[1];

		// 欠け値が存在するのを防ぐため最後のデータを取得する
		ThresHoldEntity allEntityList = entityList.get(entityList.size() - 1);

		String home = allEntityList.getHomeTeamName();
		String away = allEntityList.getAwayTeamName();

		String situation = (Integer.parseInt(returnMaxEntity.getHomeScore()) == 0
				&& Integer.parseInt(returnMaxEntity.getAwayScore()) == 0) ? AverageStatisticsSituationConst.NOSCORE
						: AverageStatisticsSituationConst.SCORE;

		// 相関係数導出
		if (AverageStatisticsSituationConst.SCORE.equals(situation)) {
			commonLogic(country, league, home, away,
					entityList, allEntityList, file, ALL_DATA, null, ALL_DATA);
		} else {
			System.out.println(
					"無得点のデータであるため,相関係数導出なし");
		}

		// 前後半データ(得点時と無得点で集計)
		for (int i = 1; i <= 2; i++) {
			String chkScore = (i == 1) ? FIRST_HALF_SCORE : SECOND_HALF_SCORE;
			String scoreFlg = (i == 1) ? FIRST_DATA : SECOND_DATA;
			commonLogic(country, league, home, away,
					entityList, allEntityList, file, HALF_SCORE, chkScore, scoreFlg);
		}

	}

	/**
	 * 共通ロジック
	 * @param country
	 * @param league
	 * @param home
	 * @param away
	 * @param entityList
	 * @param allEntityList
	 * @param file
	 * @throws IllegalArgumentException
	 * @throws IllegalAccessException
	 * @throws SecurityException
	 * @throws NoSuchFieldException
	 */
	private void commonLogic(String country, String league, String home, String away,
			List<ThresHoldEntity> entityList, ThresHoldEntity allEntityList, String file,
			String flg, String halfFlg, String scoreFlg)
			throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, SecurityException {

		List<ThresHoldEntity> filteredList = null;
		if (HALF_SCORE.equals(flg)) {
			// ハーフタイムの通番を特定
			String halfTimeSeq = findHalfTimeSeq(entityList);
			// ハーフタイム前の試合時間のデータをフィルタリング（通番が半分より小さいもの）
			if (FIRST_HALF_SCORE.equals(halfFlg)) {
				filteredList = entityList.stream()
						.filter(entity -> entity.getSeq().compareTo(halfTimeSeq) <= 0) // 通番がハーフタイムより前
						.collect(Collectors.toList());
			} else if (SECOND_HALF_SCORE.equals(halfFlg)) {
				filteredList = entityList.stream()
						.filter(entity -> entity.getSeq().compareTo(halfTimeSeq) > 0) // 通番がハーフタイムより後
						.collect(Collectors.toList());
			}
		} else if (ALL_DATA.equals(flg)) {
			filteredList = entityList.stream()
					.filter(entity -> (entity.getHomeScore() != null && !"".equals(entity.getHomeScore())) &&
							(entity.getAwayScore() != null && !"".equals(entity.getAwayScore())))
					.collect(Collectors.toList());
		}

		if (filteredList.isEmpty()) {
			return;
		}

		String chkBody = "PEARSON";

		// DBに保存済みか
		if (getTeamStaticsData(country, league, home, away, scoreFlg, chkBody)) {
			// 以降処理しない
			return;
		}

		NormalCorrelation normalCorrelation = new NormalCorrelation();
		List<CorrelationSummary> correlationData = normalCorrelation.execute(allEntityList, filteredList);


		// insert(すでに登録済みの場合はupdate)
		registerTeamStaticsData(country, league, home, away, file, scoreFlg, chkBody, correlationData);
	}

	/**
	 * 登録メソッド
	 * @param country 国
	 * @param league リーグ
	 * @param home ホーム
	 * @param away アウェー
	 * @param file ファイル
	 * @param scoreFlg フラグ
	 * @param chkBody 検証内容
	 * @param connectScore スコア連結
	 */
	private void registerTeamStaticsData(String country, String league,
			String home, String away, String file, String scoreFlg, String chkBody,
			List<CorrelationSummary> corrList) {
		List<CalcCorrelationEntity> insertEntities = new ArrayList<CalcCorrelationEntity>();
		CalcCorrelationEntity statSummaries = new CalcCorrelationEntity();
		statSummaries.setFile(file);
		statSummaries.setCountry(country);
		statSummaries.setLeague(league);
		statSummaries.setHome(home);
		statSummaries.setAway(away);
		statSummaries.setScore(scoreFlg);
		statSummaries.setChkBody(chkBody);
		statSummaries.setHomeExpInfo(corrList.get(0)); // インデックス0: homeExpInfo
		statSummaries.setAwayExpInfo(corrList.get(1)); // インデックス1: awayExpInfo
		statSummaries.setHomeDonationInfo(corrList.get(2)); // インデックス2: homeDonationInfo
		statSummaries.setAwayDonationInfo(corrList.get(3)); // インデックス3: awayDonationInfo
		statSummaries.setHomeShootAllInfo(corrList.get(4)); // インデックス4: homeShootAllInfo
		statSummaries.setAwayShootAllInfo(corrList.get(5)); // インデックス5: awayShootAllInfo
		statSummaries.setHomeShootInInfo(corrList.get(6)); // インデックス6: homeShootInInfo
		statSummaries.setAwayShootInInfo(corrList.get(7)); // インデックス7: awayShootInInfo
		statSummaries.setHomeShootOutInfo(corrList.get(8)); // インデックス8: homeShootOutInfo
		statSummaries.setAwayShootOutInfo(corrList.get(9)); // インデックス9: awayShootOutInfo
		statSummaries.setHomeBlockShootInfo(corrList.get(10)); // インデックス10: homeBlockShootInfo
		statSummaries.setAwayBlockShootInfo(corrList.get(11)); // インデックス11: awayBlockShootInfo
		statSummaries.setHomeBigChanceInfo(corrList.get(12)); // インデックス12: homeBigChanceInfo
		statSummaries.setAwayBigChanceInfo(corrList.get(13)); // インデックス13: awayBigChanceInfo
		statSummaries.setHomeCornerInfo(corrList.get(14)); // インデックス14: homeCornerInfo
		statSummaries.setAwayCornerInfo(corrList.get(15)); // インデックス15: awayCornerInfo
		statSummaries.setHomeBoxShootInInfo(corrList.get(16)); // インデックス16: homeBoxShootInInfo
		statSummaries.setAwayBoxShootInInfo(corrList.get(17)); // インデックス17: awayBoxShootInInfo
		statSummaries.setHomeBoxShootOutInfo(corrList.get(18)); // インデックス18: homeBoxShootOutInfo
		statSummaries.setAwayBoxShootOutInfo(corrList.get(19)); // インデックス19: awayBoxShootOutInfo
		statSummaries.setHomeGoalPostInfo(corrList.get(20)); // インデックス20: homeGoalPostInfo
		statSummaries.setAwayGoalPostInfo(corrList.get(21)); // インデックス21: awayGoalPostInfo
		statSummaries.setHomeGoalHeadInfo(corrList.get(22)); // インデックス22: homeGoalHeadInfo
		statSummaries.setAwayGoalHeadInfo(corrList.get(23)); // インデックス23: awayGoalHeadInfo
		statSummaries.setHomeKeeperSaveInfo(corrList.get(24)); // インデックス24: homeKeeperSaveInfo
		statSummaries.setAwayKeeperSaveInfo(corrList.get(25)); // インデックス25: awayKeeperSaveInfo
		statSummaries.setHomeFreeKickInfo(corrList.get(26)); // インデックス26: homeFreeKickInfo
		statSummaries.setAwayFreeKickInfo(corrList.get(27)); // インデックス27: awayFreeKickInfo
		statSummaries.setHomeOffsideInfo(corrList.get(28)); // インデックス28: homeOffsideInfo
		statSummaries.setAwayOffsideInfo(corrList.get(29)); // インデックス29: awayOffsideInfo
		statSummaries.setHomeFoulInfo(corrList.get(30)); // インデックス30: homeFoulInfo
		statSummaries.setAwayFoulInfo(corrList.get(31)); // インデックス31: awayFoulInfo
		statSummaries.setHomeYellowCardInfo(corrList.get(32)); // インデックス32: homeYellowCardInfo
		statSummaries.setAwayYellowCardInfo(corrList.get(33)); // インデックス33: awayYellowCardInfo
		statSummaries.setHomeRedCardInfo(corrList.get(34)); // インデックス34: homeRedCardInfo
		statSummaries.setAwayRedCardInfo(corrList.get(35)); // インデックス35: awayRedCardInfo
		statSummaries.setHomeSlowInInfo(corrList.get(36)); // インデックス36: homeSlowInInfo
		statSummaries.setAwaySlowInInfo(corrList.get(37)); // インデックス37: awaySlowInInfo
		statSummaries.setHomeBoxTouchInfo(corrList.get(38)); // インデックス38: homeBoxTouchInfo
		statSummaries.setAwayBoxTouchInfo(corrList.get(39)); // インデックス39: awayBoxTouchInfo
		statSummaries.setHomePassCountInfoOnSuccessRatio(corrList.get(40)); // インデックス40: homePassCountInfo
		statSummaries.setHomePassCountInfoOnSuccessCount(corrList.get(41)); // インデックス40: homePassCountInfo
		statSummaries.setHomePassCountInfoOnTryCount(corrList.get(42)); // インデックス40: homePassCountInfo
		statSummaries.setAwayPassCountInfoOnSuccessRatio(corrList.get(43)); // インデックス41: awayPassCountInfo
		statSummaries.setAwayPassCountInfoOnSuccessCount(corrList.get(44)); // インデックス41: awayPassCountInfo
		statSummaries.setAwayPassCountInfoOnTryCount(corrList.get(45)); // インデックス41: awayPassCountInfo
		statSummaries.setHomeFinalThirdPassCountInfoOnSuccessRatio(corrList.get(46)); // インデックス42: homeFinalThirdPassCountInfo
		statSummaries.setHomeFinalThirdPassCountInfoOnSuccessCount(corrList.get(47)); // インデックス42: homeFinalThirdPassCountInfo
		statSummaries.setHomeFinalThirdPassCountInfoOnTryCount(corrList.get(48)); // インデックス42: homeFinalThirdPassCountInfo
		statSummaries.setAwayFinalThirdPassCountInfoOnSuccessRatio(corrList.get(49)); // インデックス43: awayFinalThirdPassCountInfo
		statSummaries.setAwayFinalThirdPassCountInfoOnSuccessCount(corrList.get(50)); // インデックス43: awayFinalThirdPassCountInfo
		statSummaries.setAwayFinalThirdPassCountInfoOnTryCount(corrList.get(51)); // インデックス43: awayFinalThirdPassCountInfo
		statSummaries.setHomeCrossCountInfoOnSuccessRatio(corrList.get(52)); // インデックス44: homeCrossCountInfo
		statSummaries.setHomeCrossCountInfoOnSuccessCount(corrList.get(53)); // インデックス44: homeCrossCountInfo
		statSummaries.setHomeCrossCountInfoOnTryCount(corrList.get(54)); // インデックス44: homeCrossCountInfo
		statSummaries.setAwayCrossCountInfoOnSuccessRatio(corrList.get(55)); // インデックス45: awayCrossCountInfo
		statSummaries.setAwayCrossCountInfoOnSuccessCount(corrList.get(56)); // インデックス45: awayCrossCountInfo
		statSummaries.setAwayCrossCountInfoOnTryCount(corrList.get(57)); // インデックス45: awayCrossCountInfo
		statSummaries.setHomeTackleCountInfoOnSuccessRatio(corrList.get(58)); // インデックス46: homeTackleCountInfo
		statSummaries.setHomeTackleCountInfoOnSuccessCount(corrList.get(59)); // インデックス46: homeTackleCountInfo
		statSummaries.setHomeTackleCountInfoOnTryCount(corrList.get(60)); // インデックス46: homeTackleCountInfo
		statSummaries.setAwayTackleCountInfoOnSuccessRatio(corrList.get(61)); // インデックス47: awayTackleCountInfo
		statSummaries.setAwayTackleCountInfoOnSuccessCount(corrList.get(62)); // インデックス47: awayTackleCountInfo
		statSummaries.setAwayTackleCountInfoOnTryCount(corrList.get(63)); // インデックス47: awayTackleCountInfo
		statSummaries.setHomeClearCountInfo(corrList.get(64)); // インデックス48: homeClearCountInfo
		statSummaries.setAwayClearCountInfo(corrList.get(65)); // インデックス49: awayClearCountInfo
		statSummaries.setHomeInterceptCountInfo(corrList.get(66)); // インデックス50: homeInterceptCountInfo
		statSummaries.setAwayInterceptCountInfo(corrList.get(67)); // インデックス51: awayInterceptCountInfo
		insertEntities.add(statSummaries);

		CsvRegisterImpl csvRegisterImpl = new CsvRegisterImpl();
		try {
			csvRegisterImpl.executeInsert(UniairConst.BM_M024,
					insertEntities, 1, insertEntities.size());
		} catch (Exception e) {
			System.err.println("correlation_data insert err execute: " + e);
		}
	}

	/**
	 * 取得メソッド
	 * @param country 国
	 * @param league リーグ
	 * @param home ホーム
	 * @param away アウェー
	 * @param flg フラグ
	 * @param chkBody 検証内容
	 */
	private boolean getTeamStaticsData(String country, String league, String home, String away, String flg
			, String chkBody) {
		String[] selDataList = new String[1];
		selDataList[0] = "id";

		String where = "country = '" + country + "' and league = '" + league + "' "
				+ "and home = '" + home + "' and away = '" + away + "' and score = '" + flg + "' and "
						+ "chk_body = '" + chkBody + "'";

		List<List<String>> selectResultList = null;
		SqlMainLogic select = new SqlMainLogic();
		try {
			selectResultList = select.executeSelect(null, UniairConst.BM_M024, selDataList,
					where, null, "1");
		} catch (Exception e) {
			throw new SystemException("", "", "", "err");
		}

		if (!selectResultList.isEmpty()) {
			return true;
		}
		return false;
	}

	/**
	 * ハーフタイムの通番を特定するメソッド
	 * @param entityList レコードのリスト
	 * @return ハーフタイムの通番
	 */
	private String findHalfTimeSeq(List<ThresHoldEntity> entityList) {
		// 通番が最も大きいレコードの時がハーフタイムだと仮定
		// もしくは、最初に見つかるハーフタイム通番があればそれを返す
		for (ThresHoldEntity entity : entityList) {
			if (BookMakersCommonConst.FIRST_HALF_TIME.equals(entity.getTimes()) ||
					BookMakersCommonConst.HALF_TIME.equals(entity.getTimes())) {
				return entity.getSeq();
			}
		}
		// もしハーフタイムが見つからなければ、エラーやデフォルト値を返す（ケースに応じて）
		return "-1"; // エラー値（ハーフタイムが見つからない場合）
	}


}
