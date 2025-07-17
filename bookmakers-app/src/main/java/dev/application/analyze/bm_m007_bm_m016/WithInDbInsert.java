package dev.application.analyze.bm_m007_bm_m016;

import java.util.ArrayList;
import java.util.List;

import dev.common.constant.UniairConst;
import dev.common.entity.BookDataEntity;
import dev.common.util.DateUtil;


/**
 * within_dataに登録するロジック
 * @author shiraishitoshio
 *
 */
public class WithInDbInsert {

	/**
	 * 登録メソッド
	 * @param seq 通番
	 * @param target 探索数
	 * @param flg 登録テーブル判断フラグ
	 * @throws IllegalAccessException
	 * @throws IllegalArgumentException
	 */
	public void execute(String seq, Integer target, String flg)
			throws IllegalArgumentException, IllegalAccessException {
		List<String> selectList = UniairColumnMapUtil.getKeyMap(UniairConst.BM_M001);
		String[] selList = new String[selectList.size()];
		for (int i = 0; i < selectList.size(); i++) {
			selList[i] = selectList.get(i);
		}

		String where = "seq = '" + seq + "'";

		List<List<String>> selectResultList = null;
		List<BookDataEntity> conditionList = new ArrayList<BookDataEntity>();
		SqlMainLogic select = new SqlMainLogic();
		try {
			selectResultList = select.executeSelect(null, UniairConst.BM_M001, selList,
					where.toString(), null, "1");
			if (!selectResultList.isEmpty()) {
				// Entityにマッピングする
				for (List<String> list : selectResultList) {
					BookDataEntity mapSelectDestination = mappingSelectEntity(list);
					conditionList.add(mapSelectDestination);
				}
			}
		} catch (Exception e) {
			System.err.println("within_data insert err searchData: " + e);
		}

		// すでに登録済なら登録しない
		String[] sel7List = new String[1];
		sel7List[0] = "seq";

		String whereStr = "data_category = '" + conditionList.get(0).getGameTeamCategory() + "' "
				+ "and home_team_name = '" + conditionList.get(0).getHomeTeamName() + "' "
				+ "and away_team_name = '" + conditionList.get(0).getAwayTeamName() + "' "
						+ "and times = '" + conditionList.get(0).getTime() + "'";

		selectResultList = null;
		List<BookDataEntity> ChkConditionList = new ArrayList<BookDataEntity>();
		try {
			selectResultList = select.executeSelect(null, UniairConst.BM_M007, sel7List,
					whereStr, null, "1");
			if (!selectResultList.isEmpty()) {
				// Entityにマッピングする
				for (List<String> list : selectResultList) {
					BookDataEntity mapSelectDestination = mappingSelectEntity(list);
					ChkConditionList.add(mapSelectDestination);
				}
			}
		} catch (Exception e) {
			System.err.println("within_data insert err searchData: " + e);
		}

		if (ChkConditionList.isEmpty()) {
			CsvRegisterImpl csvRegisterImpl = new CsvRegisterImpl();
			try {
				csvRegisterImpl.executeInsert(UniairConst.BM_M007,
						conditionList, 1, 1);
			} catch (Exception e) {
				System.err.println("within_data insert err execute: " + e);
			}

			WithInXMinutesDbInsert withInXMinutesDbInsert = new WithInXMinutesDbInsert();
			withInXMinutesDbInsert.execute(conditionList, target, flg);
		}
	}

	/**
	 * ListからDTOにマッピングをかける
	 * @param mapSource list構造
	 * @return BookDataSelectEntity DTO
	 * @throws Exception
	 */
	private BookDataEntity mappingSelectEntity(List<String> parts) throws Exception {
		BookDataEntity mappingDto = new BookDataEntity();
		mappingDto.setSeq(parts.get(0));
		mappingDto.setConditionResultDataSeqId(parts.get(1));
		mappingDto.setGameTeamCategory(parts.get(2));
		mappingDto.setTime(parts.get(3));
		mappingDto.setHomeRank(parts.get(4));
		mappingDto.setHomeTeamName(parts.get(5));
		mappingDto.setHomeScore(parts.get(6));
		mappingDto.setAwayRank(parts.get(7));
		mappingDto.setAwayTeamName(parts.get(8));
		mappingDto.setAwayScore(parts.get(9));
		mappingDto.setHomeExp(parts.get(10));
		mappingDto.setAwayExp(parts.get(11));
		mappingDto.setHomeBallPossesion(parts.get(12));
		mappingDto.setAwayBallPossesion(parts.get(13));
		mappingDto.setHomeShootAll(parts.get(14));
		mappingDto.setAwayShootAll(parts.get(15));
		mappingDto.setHomeShootIn(parts.get(16));
		mappingDto.setAwayShootIn(parts.get(17));
		mappingDto.setHomeShootOut(parts.get(18));
		mappingDto.setAwayShootOut(parts.get(19));
		mappingDto.setHomeShootBlocked(parts.get(20));
		mappingDto.setAwayShootBlocked(parts.get(21));
		mappingDto.setHomeBigChance(parts.get(22));
		mappingDto.setAwayBigChance(parts.get(23));
		mappingDto.setHomeCornerKick(parts.get(24));
		mappingDto.setAwayCornerKick(parts.get(25));
		mappingDto.setHomeBoxShootIn(parts.get(26));
		mappingDto.setAwayBoxShootIn(parts.get(27));
		mappingDto.setHomeBoxShootOut(parts.get(28));
		mappingDto.setAwayBoxShootOut(parts.get(29));
		mappingDto.setHomeGoalPost(parts.get(30));
		mappingDto.setAwayGoalPost(parts.get(31));
		mappingDto.setHomeGoalHead(parts.get(32));
		mappingDto.setAwayGoalHead(parts.get(33));
		mappingDto.setHomeKeeperSave(parts.get(34));
		mappingDto.setAwayKeeperSave(parts.get(35));
		mappingDto.setHomeFreeKick(parts.get(36));
		mappingDto.setAwayFreeKick(parts.get(37));
		mappingDto.setHomeOffSide(parts.get(38));
		mappingDto.setAwayOffSide(parts.get(39));
		mappingDto.setHomeFoul(parts.get(40));
		mappingDto.setAwayFoul(parts.get(41));
		mappingDto.setHomeYellowCard(parts.get(42));
		mappingDto.setAwayYellowCard(parts.get(43));
		mappingDto.setHomeRedCard(parts.get(44));
		mappingDto.setAwayRedCard(parts.get(45));
		mappingDto.setHomeSlowIn(parts.get(46));
		mappingDto.setAwaySlowIn(parts.get(47));
		mappingDto.setHomeBoxTouch(parts.get(48));
		mappingDto.setAwayBoxTouch(parts.get(49));
		mappingDto.setHomePassCount(parts.get(50));
		mappingDto.setAwayPassCount(parts.get(51));
		mappingDto.setHomeFinalThirdPassCount(parts.get(52));
		mappingDto.setAwayFinalThirdPassCount(parts.get(53));
		mappingDto.setHomeCrossCount(parts.get(54));
		mappingDto.setAwayCrossCount(parts.get(55));
		mappingDto.setHomeTackleCount(parts.get(56));
		mappingDto.setAwayTackleCount(parts.get(57));
		mappingDto.setHomeClearCount(parts.get(58));
		mappingDto.setAwayClearCount(parts.get(59));
		mappingDto.setHomeInterceptCount(parts.get(60));
		mappingDto.setAwayInterceptCount(parts.get(61));
		mappingDto.setRecordTime(DateUtil.convertTimestamp(parts.get(62)));
		mappingDto.setWeather(parts.get(63));
		mappingDto.setTemparature(parts.get(64));
		mappingDto.setHumid(parts.get(65));
		mappingDto.setJudgeMember(parts.get(66));
		mappingDto.setHomeManager(parts.get(67));
		mappingDto.setAwayManager(parts.get(68));
		mappingDto.setHomeFormation(parts.get(69));
		mappingDto.setAwayFormation(parts.get(70));
		mappingDto.setStudium(parts.get(71));
		mappingDto.setCapacity(parts.get(72).replaceAll("\\s", ""));
		mappingDto.setAudience(parts.get(73).replaceAll("\\s", ""));
		mappingDto.setHomeMaxGettingScorer(parts.get(74));
		mappingDto.setAwayMaxGettingScorer(parts.get(75));
		mappingDto.setHomeMaxGettingScorerGameSituation(parts.get(76));
		mappingDto.setAwayMaxGettingScorerGameSituation(parts.get(77));
		mappingDto.setHomeTeamHomeScore(parts.get(78).replace(".0", ""));
		mappingDto.setHomeTeamHomeLost(parts.get(79).replace(".0", ""));
		mappingDto.setAwayTeamHomeScore(parts.get(80).replace(".0", ""));
		mappingDto.setAwayTeamHomeLost(parts.get(81).replace(".0", ""));
		mappingDto.setHomeTeamAwayScore(parts.get(82).replace(".0", ""));
		mappingDto.setHomeTeamAwayLost(parts.get(83).replace(".0", ""));
		mappingDto.setAwayTeamAwayScore(parts.get(84).replace(".0", ""));
		mappingDto.setAwayTeamAwayLost(parts.get(85).replace(".0", ""));
		mappingDto.setNoticeFlg(parts.get(86));
		mappingDto.setGoalTime(parts.get(87));
		mappingDto.setGoalTeamMember(parts.get(88));
		mappingDto.setJudge(parts.get(89));
		mappingDto.setHomeTeamStyle(parts.get(90));
		mappingDto.setAwayTeamStyle(parts.get(91));
		mappingDto.setProbablity(parts.get(92));
		mappingDto.setPredictionScoreTime(parts.get(93));
		return mappingDto;
	}

}
