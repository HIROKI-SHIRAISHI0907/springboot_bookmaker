package dev.application.main.service.sub;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.application.common.constant.BookMakersCommonConst;
import dev.application.common.file.MakeCsv;
import dev.application.common.util.DateUtil;
import dev.application.common.util.UniairColumnMapUtil;
import dev.application.db.BookDataSelectWrapper;
import dev.application.db.CsvRegisterImpl;
import dev.application.db.SqlMainLogic;
import dev.application.db.UniairConst;
import dev.application.entity.BookDataSelectEntity;
import dev.application.entity.TypeOfCountryLeagueDataEntity;

public class RDataOutputSubLogic {

	/** Logger */
	private static final Logger logger = LoggerFactory.getLogger(RDataOutputSubLogic.class);

	/** プロジェクト名 */
	private static final String PROJECT_NAME = RDataOutputSubLogic.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = RDataOutputSubLogic.class.getSimpleName();

	/** ログ出力 */
	private static final String FILE = "/Users/shiraishitoshio/bookmaker/csv/seqList.txt";

	/** ログ出力 */
	private static final String START_END_FILE = "/Users/shiraishitoshio/bookmaker/csv/seqList_time.txt";

	/** ログ出力 */
	private static final String TEAM_FILE = "/Users/shiraishitoshio/bookmaker/csv/seqList_team.txt";

	/**
	 * 実行メソッド
	 * @param csvId
	 * @param seqList
	 * @throws Exception
	 */
	public int execute(int csvId, List<String> seqList, boolean seqListFlg) throws Exception {
		final String METHOD = "execute";

		logger.info("csvId thread -> No: {} ", csvId);

		logger.info("seqList -> {} ", seqList);

		try {
			File file = new File(START_END_FILE);

			FileWriter filewriter = new FileWriter(file, true);
			filewriter.write("RDataOutputSubLogic start time : " + new Timestamp(System.currentTimeMillis()) + "\r\n");
			filewriter.close();
		} catch (IOException e) {
			System.out.println(e);
		}

		SqlMainLogic select = new SqlMainLogic();
		// 通番を設定
		StringBuilder whereBuilder = new StringBuilder();
		for (String seqs : seqList) {
			if (whereBuilder.toString().length() > 0) {
				whereBuilder.append(" OR ");
			}
			whereBuilder.append("seq = " + seqs + " ");
		}

		List<String> selectList = UniairColumnMapUtil.getKeyMap(UniairConst.BM_M001);
		String[] selList = new String[selectList.size()];
		for (int i = 0; i < selectList.size(); i++) {
			selList[i] = selectList.get(i);
		}

		List<List<String>> selectResultList = null;
		List<BookDataSelectEntity> conditionList = new ArrayList<BookDataSelectEntity>();
		try {
			selectResultList = select.executeSelect(null, UniairConst.BM_M001, selList, whereBuilder.toString(), null,
					"1");
			if (!selectResultList.isEmpty()) {
				// Entityにマッピングする
				for (List<String> list : selectResultList) {
					BookDataSelectEntity mapSelectDestination = mappingSelectEntity(list);
					conditionList.add(mapSelectDestination);
				}
			}
		} catch (Exception e) {
			logger.error("select error -> ", e);
		}

		// 条件に当てはまらないとbreak
		if (conditionList == null || conditionList.isEmpty() || conditionList.get(0) == null) {
			return csvId;
		}

		if (!seqListFlg) {
			try {
				File file = new File(FILE);

				FileWriter filewriter = new FileWriter(file, true);
				filewriter.write(seqList + "\r\n");
				filewriter.close();
			} catch (IOException e) {
				System.out.println(e);
			}
		}

		BookDataSelectWrapper selectWrapper = new BookDataSelectWrapper();
		List<BookDataSelectEntity> selectResultAllList = new ArrayList<BookDataSelectEntity>();
		try {
			selectResultAllList = selectWrapper.executeSelect(-1, conditionList, true);
		} catch (Exception e) {
			logger.error("select error -> ", e);
		}

		if (!selectResultAllList.isEmpty()) {
			// U20,U21,U22,U23,U25チーム
			if ((selectResultAllList.get(0).getHomeTeamName().contains("U20") ||
					selectResultAllList.get(0).getAwayTeamName().contains("U20")) ||
					(selectResultAllList.get(0).getHomeTeamName().contains("U21") ||
							selectResultAllList.get(0).getAwayTeamName().contains("U21"))
					||
					(selectResultAllList.get(0).getHomeTeamName().contains("U22") ||
							selectResultAllList.get(0).getAwayTeamName().contains("U22"))
					||
					(selectResultAllList.get(0).getHomeTeamName().contains("U23") ||
							selectResultAllList.get(0).getAwayTeamName().contains("U23"))
					||
					(selectResultAllList.get(0).getHomeTeamName().contains("U25") ||
							selectResultAllList.get(0).getAwayTeamName().contains("U25"))) {
				return csvId;
			}

			// 終了済,ハーフタイムが存在すること
//			boolean firstFlg = false;
			boolean finFlg = false;
			boolean halfFlg = false;
//			Pattern firstPattern1 = Pattern.compile("(10:|11:|12:|13:|14:|15:|16:|17:|18:|19:)");
//			Pattern firstPattern2 = Pattern.compile("^(10'|11'|12'|13'|14'|15'|16'|17'|18'|19')$");
			for (BookDataSelectEntity entity : selectResultAllList) {
//				Matcher matcher1 = firstPattern1.matcher(entity.getTimes());
//				Matcher matcher2 = firstPattern2.matcher(entity.getTimes());
//				if (matcher1.find() && !firstFlg) {
//					firstFlg = true;
//				}
//				if (matcher2.find() && !firstFlg) {
//					firstFlg = true;
//				}
				if (BookMakersCommonConst.FIN.equals(entity.getTimes()) && !finFlg) {
					finFlg = true;
				}
				if (BookMakersCommonConst.HALF_TIME.equals(entity.getTimes()) && !halfFlg) {
					halfFlg = true;
				}
				if (BookMakersCommonConst.FIRST_HALF_TIME.equals(entity.getTimes()) && !halfFlg) {
					halfFlg = true;
				}
			}

			//System.out.println("firstflg:" + firstFlg);
			System.out.println("halfFlg:" + halfFlg);
			System.out.println("finFlg:" + finFlg);

			//if (!firstFlg || !halfFlg || !finFlg) {
			if (!halfFlg || !finFlg) {
				return csvId;
			}

			// 重複データ,試合時間に不純物が紛れているレコードは取り入れない
			List<List<String>> selectResultConvList = new ArrayList<List<String>>();
			for (BookDataSelectEntity entity : selectResultAllList) {
				List<String> subList = new ArrayList<>();
				try {
					subList = mappingConvertEntity(entity);
				} catch (Exception e) {
					throw e;
				}
				selectResultConvList.add(subList);
			}

			List<BookDataSelectEntity> selectResultConvAllList = new ArrayList<BookDataSelectEntity>();
			List<String> dupList = new ArrayList<>();
			for (BookDataSelectEntity entity : selectResultAllList) {
				if (!dupList.contains(entity.getTimes()) &&
						(entity.getTimes().contains("+") ||
								entity.getTimes().contains(":") ||
								entity.getTimes().contains("'") ||
								entity.getTimes().contains(BookMakersCommonConst.HALF_TIME) ||
								entity.getTimes().contains(BookMakersCommonConst.FIRST_HALF_TIME) ||
								entity.getTimes().contains(BookMakersCommonConst.FIN))) {
					selectResultConvAllList.add(entity);
				}
				dupList.add(entity.getTimes());
			}

			if (selectResultConvAllList.size() < 3) {
				return csvId;
			}

			try {
				File file = new File(START_END_FILE);

				FileWriter filewriter = new FileWriter(file, true);
				filewriter
						.write("RDataOutputSubLogic end time : " + new Timestamp(System.currentTimeMillis()) + "\r\n");
				filewriter.close();
			} catch (IOException e) {
				System.out.println(e);
			}

			try {
				File file = new File(TEAM_FILE);

				FileWriter filewriter = new FileWriter(file, true);
				filewriter.write(csvId + ".csv, " + conditionList.get(0).getHomeTeamName() + " vs "
						+ conditionList.get(0).getAwayTeamName() + " data" + "\r\n");
				filewriter.close();
			} catch (IOException e) {
				System.out.println(e);
			}

			List<List<String>> convList = new ArrayList<List<String>>();
			for (BookDataSelectEntity entity : selectResultConvAllList) {
				List<String> list = mappingConvertEntity(entity);
				convList.add(list);
			}

			if (!selectResultConvAllList.isEmpty()) {
				// CSV作成
				MakeCsv makeCsv = new MakeCsv();
				String file = "/Users/shiraishitoshio/bookmaker/csv/" + String.valueOf(csvId) + ".csv";
				makeCsv.execute(file, UniairConst.BM_M001, null, convList);
				return csvId + 1;
			}

		}
		return csvId;
	}

	/**
	 * ListからDTOにマッピングをかける
	 * @param mapSource list構造
	 * @return BookDataSelectEntity DTO
	 * @throws Exception
	 */
	private BookDataSelectEntity mappingSelectEntity(List<String> parts) throws Exception {
		BookDataSelectEntity mappingDto = new BookDataSelectEntity();
		mappingDto.setSeq(parts.get(0));
		mappingDto.setConditionResultDataSeqId(parts.get(1));
		mappingDto.setDataCategory(parts.get(2));
		mappingDto.setTimes(parts.get(3));
		mappingDto.setHomeRank(parts.get(4));
		mappingDto.setHomeTeamName(parts.get(5));
		mappingDto.setHomeScore(parts.get(6));
		mappingDto.setAwayRank(parts.get(7));
		mappingDto.setAwayTeamName(parts.get(8));
		mappingDto.setAwayScore(parts.get(9));
		mappingDto.setHomeExp(parts.get(10));
		mappingDto.setAwayExp(parts.get(11));
		mappingDto.setHomeDonation(parts.get(12));
		mappingDto.setAwayDonation(parts.get(13));
		mappingDto.setHomeShootAll(parts.get(14));
		mappingDto.setAwayShootAll(parts.get(15));
		mappingDto.setHomeShootIn(parts.get(16));
		mappingDto.setAwayShootIn(parts.get(17));
		mappingDto.setHomeShootOut(parts.get(18));
		mappingDto.setAwayShootOut(parts.get(19));
		mappingDto.setHomeBlockShoot(parts.get(20));
		mappingDto.setAwayBlockShoot(parts.get(21));
		mappingDto.setHomeBigChance(parts.get(22));
		mappingDto.setAwayBigChance(parts.get(23));
		mappingDto.setHomeCorner(parts.get(24));
		mappingDto.setAwayCorner(parts.get(25));
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
		mappingDto.setHomeOffside(parts.get(38));
		mappingDto.setAwayOffside(parts.get(39));
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

	/**
	 * DTOからListにマッピングをかける
	 * @param mapSource list構造
	 * @return BookDataSelectEntity DTO
	 * @throws Exception
	 */
	private List<String> mappingConvertEntity(BookDataSelectEntity parts) throws Exception {
		List<String> mappingList = new ArrayList<String>();
		mappingList.add(parts.getSeq());
		mappingList.add(parts.getConditionResultDataSeqId());
		mappingList.add(parts.getDataCategory());
		mappingList.add(parts.getTimes());
		mappingList.add(parts.getHomeRank());
		mappingList.add(parts.getHomeTeamName());
		mappingList.add(parts.getHomeScore());
		mappingList.add(parts.getAwayRank());
		mappingList.add(parts.getAwayTeamName());
		mappingList.add(parts.getAwayScore());
		mappingList.add(parts.getHomeExp());
		mappingList.add(parts.getAwayExp());
		mappingList.add(parts.getHomeDonation());
		mappingList.add(parts.getAwayDonation());
		mappingList.add(parts.getHomeShootAll());
		mappingList.add(parts.getAwayShootAll());
		mappingList.add(parts.getHomeShootIn());
		mappingList.add(parts.getAwayShootIn());
		mappingList.add(parts.getHomeShootOut());
		mappingList.add(parts.getAwayShootOut());
		mappingList.add(parts.getHomeBlockShoot());
		mappingList.add(parts.getAwayBlockShoot());
		mappingList.add(parts.getHomeBigChance());
		mappingList.add(parts.getAwayBigChance());
		mappingList.add(parts.getHomeCorner());
		mappingList.add(parts.getAwayCorner());
		mappingList.add(parts.getHomeBoxShootIn());
		mappingList.add(parts.getAwayBoxShootIn());
		mappingList.add(parts.getHomeBoxShootOut());
		mappingList.add(parts.getAwayBoxShootOut());
		mappingList.add(parts.getHomeGoalPost());
		mappingList.add(parts.getAwayGoalPost());
		mappingList.add(parts.getHomeGoalHead());
		mappingList.add(parts.getAwayGoalHead());
		mappingList.add(parts.getHomeKeeperSave());
		mappingList.add(parts.getAwayKeeperSave());
		mappingList.add(parts.getHomeFreeKick());
		mappingList.add(parts.getAwayFreeKick());
		mappingList.add(parts.getHomeOffside());
		mappingList.add(parts.getAwayOffside());
		mappingList.add(parts.getHomeFoul());
		mappingList.add(parts.getAwayFoul());
		mappingList.add(parts.getHomeYellowCard());
		mappingList.add(parts.getAwayYellowCard());
		mappingList.add(parts.getHomeRedCard());
		mappingList.add(parts.getAwayRedCard());
		mappingList.add(parts.getHomeSlowIn());
		mappingList.add(parts.getAwaySlowIn());
		mappingList.add(parts.getHomeBoxTouch());
		mappingList.add(parts.getAwayBoxTouch());
		mappingList.add(parts.getHomePassCount());
		mappingList.add(parts.getAwayPassCount());
		mappingList.add(parts.getHomeFinalThirdPassCount());
		mappingList.add(parts.getAwayFinalThirdPassCount());
		mappingList.add(parts.getHomeCrossCount());
		mappingList.add(parts.getAwayCrossCount());
		mappingList.add(parts.getHomeTackleCount());
		mappingList.add(parts.getAwayTackleCount());
		mappingList.add(parts.getHomeClearCount().replace(".0", ""));
		mappingList.add(parts.getAwayClearCount().replace(".0", ""));
		mappingList.add(parts.getHomeInterceptCount().replace(".0", ""));
		mappingList.add(parts.getAwayInterceptCount().replace(".0", ""));
		mappingList.add(String.valueOf(parts.getRecordTime()));
		mappingList.add(parts.getWeather());
		mappingList.add(parts.getTemparature());
		mappingList.add(parts.getHumid());
		mappingList.add(parts.getJudgeMember());
		mappingList.add(parts.getHomeManager());
		mappingList.add(parts.getAwayManager());
		mappingList.add(parts.getHomeFormation());
		mappingList.add(parts.getAwayFormation());
		mappingList.add(parts.getStudium());
		mappingList.add(parts.getCapacity().replaceAll("\\s", ""));
		mappingList.add(parts.getAudience().replaceAll("\\s", ""));
		mappingList.add(parts.getHomeMaxGettingScorer());
		mappingList.add(parts.getAwayMaxGettingScorer());
		mappingList.add(parts.getHomeMaxGettingScorerGameSituation());
		mappingList.add(parts.getAwayMaxGettingScorerGameSituation());
		mappingList.add(parts.getHomeTeamHomeScore().replace(".0", ""));
		mappingList.add(parts.getHomeTeamHomeLost().replace(".0", ""));
		mappingList.add(parts.getAwayTeamHomeScore().replace(".0", ""));
		mappingList.add(parts.getAwayTeamHomeLost().replace(".0", ""));
		mappingList.add(parts.getHomeTeamAwayScore().replace(".0", ""));
		mappingList.add(parts.getHomeTeamAwayLost().replace(".0", ""));
		mappingList.add(parts.getAwayTeamAwayScore().replace(".0", ""));
		mappingList.add(parts.getAwayTeamAwayLost().replace(".0", ""));
		mappingList.add(parts.getNoticeFlg());
		mappingList.add(parts.getGoalTime());
		mappingList.add(parts.getGoalTeamMember());
		mappingList.add(parts.getJudge());
		mappingList.add(parts.getHomeTeamStyle());
		mappingList.add(parts.getAwayTeamStyle());
		mappingList.add(parts.getProbablity());
		mappingList.add(parts.getPredictionScoreTime());
		return mappingList;
	}


	/**
	 * DBに登録する
	 * @param country 国
	 * @param league リーグ
	 * @throws Exception
	 */
	private void createTypeOfCountryLeagueDataVerData(String country, String league) throws Exception {
		List<String> selectList = new ArrayList<String>();
		selectList.add("id");
		selectList.add("data_count");
		String[] selList = new String[selectList.size()];
		for (int i = 0; i < selectList.size(); i++) {
			selList[i] = selectList.get(i);
		}

		SqlMainLogic select = new SqlMainLogic();
		List<List<String>> selectResultList = null;
		String where = "country = '" + country + "' and league = '" + league + "'";
		try {
			selectResultList = select.executeSelect(null, UniairConst.BM_M006, selList, where, null, "1");
		} catch (Exception e) {
			logger.error("select error -> ", e);
			return;
		}

		if (!selectResultList.isEmpty()) {
			String new_where = "id = " + selectResultList.get(0).get(0);
			String new_data = selectResultList.get(0).get(1);
			String set = "data_count = '" + String.valueOf(Integer.parseInt(new_data) + 1) + "' , "
							+ "update_time = '" + DateUtil.getSysDate() + "'";
			int result = select.executeUpdate(null, UniairConst.BM_M006, new_where, set);
			if (result == 1) {
				logger.info("更新しました。(国: {} リーグ: {})", country , league);
			}
		} else {
			List<TypeOfCountryLeagueDataEntity> insertList = new ArrayList<TypeOfCountryLeagueDataEntity>();
			TypeOfCountryLeagueDataEntity typeOfCountryLeagueDataEntity = new TypeOfCountryLeagueDataEntity();
			typeOfCountryLeagueDataEntity.setCountry(country);
			typeOfCountryLeagueDataEntity.setLeague(league);
			typeOfCountryLeagueDataEntity.setDataCount("1");
			typeOfCountryLeagueDataEntity.setCsvCount("0");
			insertList.add(typeOfCountryLeagueDataEntity);
			CsvRegisterImpl csvRegisterImpl = new CsvRegisterImpl();
			csvRegisterImpl.executeInsert(UniairConst.BM_M006, insertList, 1, 1);
			logger.info("登録しました。(国: {} リーグ: {})", country , league);
		}

	}

}
