package dev.application.analyze.bm_m003;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import dev.application.analyze.common.entity.ThresHoldEntity;
import dev.application.analyze.common.util.ExecuteMainUtil;
import dev.common.constant.UniairConst;
import dev.common.exception.SystemException;
import dev.common.util.DateUtil;


/**
 * team_statistics_dataテーブルに登録するロジック
 * @author shiraishitoshio
 *
 */
@Component
public class TeamStatisticsDataSubLogic {

	/** Logger */
	private static final Logger logger = LoggerFactory.getLogger(TeamStatisticsDataSubLogic.class);

	/**
	 * 探索パス(外部設定値)
	 */
	@Value("${bmbusiness.aftercopypath:/Users/shiraishitoshio/bookmaker/conditiondata/}")
	private String findPath = "/Users/shiraishitoshio/bookmaker/conditiondata/";

	/**
	 * コピー先パス(外部設定値)
	 */
	@Value("${bmbusiness.aftercopypath:/Users/shiraishitoshio/bookmaker/conditiondata/}")
	private String copyPath = "/Users/shiraishitoshio/bookmaker/conditiondata/copyfolder";

	/**
	 * 処理実行
	 * @return 0:正常終了, 4:警告終了, 9:異常終了
	 */
	public void execute(List<ThresHoldEntity> entityList, String file) {
		final String METHOD = "execute";

		// 導出内容
		// 国,リーグ,チーム名
		// ホームスコア平均時間,アウェースコア平均時間
		// X月合計スコア

		// 国とリーグに分割
		String[] data_List = ExecuteMainUtil.splitLeagueInfo(entityList.get(0).getDataCategory());
		String country = data_List[0];
		String league = data_List[1];
		String home = entityList.get(0).getHomeTeamName();
		String away = entityList.get(0).getAwayTeamName();

		// 記録時間を見て日にちが異なる場合が想定されるため分割する
		Map<String, List<ThresHoldEntity>> replaceEntitiesMap = new LinkedHashMap<>();
		for (ThresHoldEntity entity : entityList) {
			String record = entity.getRecordTime();
			String year = record.split("-")[0];
			String month = "";
			if (record.split("-")[1].startsWith("0")) {
				month = record.split("-")[1].replace("0", "");
			} else {
				month = record.split("-")[1];
			}
			replaceEntitiesMap.computeIfAbsent(year + "-" + month, k -> new ArrayList<>()).add(entity);
		}

		// 分割したDTOリストで合計リストを出す
		for (Map.Entry<String, List<ThresHoldEntity>> map : replaceEntitiesMap.entrySet()) {
			// 最小,最大の通番を持つデータを取得
			ThresHoldEntity maxEntity = ExecuteMainUtil.getMaxSeqEntities(map.getValue());

			// 最大の通番から最小の通番の差分を取得した得点とし,得点マップに設定する
			int homeScore = Integer.parseInt(maxEntity.getHomeScore());
			int awayScore = Integer.parseInt(maxEntity.getAwayScore());

			String year = map.getKey().split("-")[0];
			String month = map.getKey().split("-")[1];

			// 得点リストがDBに存在したら取得,なければ初期リストを返却
			// ホーム
			TeamStaticDataOutputDTO homeDto = getTeamStaticsData(
					country, league, home, "H", year);
			Integer[] homeScoreList = homeDto.getScoreList();

			logger.info("before homescore -> {},{},{},{},{},{},{},{},{},{},{} ",
					homeScoreList[0], homeScoreList[1], homeScoreList[2], homeScoreList[3], homeScoreList[4],
					homeScoreList[5], homeScoreList[6], homeScoreList[7], homeScoreList[8], homeScoreList[9],
					homeScoreList[10]);

			// 取得した月-1をindexにする
			homeScoreList[Integer.parseInt(month) - 1] += homeScore;

			logger.info("after homescore -> {},{},{},{},{},{},{},{},{},{},{} ",
					homeScoreList[0], homeScoreList[1], homeScoreList[2], homeScoreList[3], homeScoreList[4],
					homeScoreList[5], homeScoreList[6], homeScoreList[7], homeScoreList[8], homeScoreList[9],
					homeScoreList[10]);

			// 登録する
			registerTeamStaticsData(country, league, home, "H", year, homeScoreList, homeDto.isUpdFlg(), homeDto.getSeq());

			TeamStaticDataOutputDTO awayDto = getTeamStaticsData(
					country, league, away, "A", year);
			Integer[] awayScoreList = awayDto.getScoreList();

			logger.info("before awayscore -> {},{},{},{},{},{},{},{},{},{},{} ",
					awayScoreList[0], awayScoreList[1], awayScoreList[2], awayScoreList[3], awayScoreList[4],
					awayScoreList[5], awayScoreList[6], awayScoreList[7], awayScoreList[8], awayScoreList[9],
					awayScoreList[10]);

			// 取得した月-1をindexにする
			awayScoreList[Integer.parseInt(month) - 1] += awayScore;

			logger.info("after awayscore -> {},{},{},{},{},{},{},{},{},{},{} ",
					awayScoreList[0], awayScoreList[1], awayScoreList[2], awayScoreList[3], awayScoreList[4],
					awayScoreList[5], awayScoreList[6], awayScoreList[7], awayScoreList[8], awayScoreList[9],
					awayScoreList[10]);

			// 登録する
			registerTeamStaticsData(country, league, away, "A", year, awayScoreList, awayDto.isUpdFlg(), awayDto.getSeq());
		}

	}

	/**
	 * 登録メソッド
	 * @param country 国
	 * @param league リーグ
	 * @param team チーム
	 * @param ha home&away
	 * @param year 年
	 * @param sumList 合計リスト
	 */
	private void registerTeamStaticsData(String country, String league,
			String team, String ha, String year, Integer[] sumList, boolean updFlg, String seq) {
		if (updFlg) {
			List<String> selectList = UniairColumnMapUtil.getKeyMap(UniairConst.BM_M003);
			String[] selDataList = new String[selectList.size()];
			for (int i = 0; i < selectList.size(); i++) {
				selDataList[i] = selectList.get(i);
			}
			StringBuilder sBuilder = new StringBuilder();
			for (int ind = 6; ind <= 17; ind++) {
				if (sBuilder.toString().length() > 0) {
					sBuilder.append(", ");
				}
				sBuilder.append(" " + selDataList[ind] + " = '" + sumList[ind - 6] + "'");
			}
			sBuilder.append(", update_time = '" + DateUtil.getSysDate() + "'");
			UpdateWrapper updateWrapper = new UpdateWrapper();

			String where = "seq = '" + seq + "'";
			updateWrapper.updateExecute(UniairConst.BM_M003, where,
					sBuilder.toString());
		} else {
			List<TeamMonthlyScoreSummaryEntity> insertEntities = new ArrayList<TeamMonthlyScoreSummaryEntity>();
			TeamMonthlyScoreSummaryEntity teamStatisticsDataEntity = new TeamMonthlyScoreSummaryEntity();
			teamStatisticsDataEntity.setCountry(country);
			teamStatisticsDataEntity.setLeague(league);
			teamStatisticsDataEntity.setTeamName(team);
			teamStatisticsDataEntity.setHa(ha);
			teamStatisticsDataEntity.setYear(year);
			teamStatisticsDataEntity.setJanuaryScoreSumCount(String.valueOf(sumList[0]));
			teamStatisticsDataEntity.setFebruaryScoreSumCount(String.valueOf(sumList[1]));
			teamStatisticsDataEntity.setMarchScoreSumCount(String.valueOf(sumList[2]));
			teamStatisticsDataEntity.setAprilScoreSumCount(String.valueOf(sumList[3]));
			teamStatisticsDataEntity.setMayScoreSumCount(String.valueOf(sumList[4]));
			teamStatisticsDataEntity.setJuneScoreSumCount(String.valueOf(sumList[5]));
			teamStatisticsDataEntity.setJulyScoreSumCount(String.valueOf(sumList[6]));
			teamStatisticsDataEntity.setAugustScoreSumCount(String.valueOf(sumList[7]));
			teamStatisticsDataEntity.setSeptemberScoreSumCount(String.valueOf(sumList[8]));
			teamStatisticsDataEntity.setOctoberScoreSumCount(String.valueOf(sumList[9]));
			teamStatisticsDataEntity.setNovemberScoreSumCount(String.valueOf(sumList[10]));
			teamStatisticsDataEntity.setDecemberScoreSumCount(String.valueOf(sumList[11]));
			insertEntities.add(teamStatisticsDataEntity);

			CsvRegisterImpl csvRegisterImpl = new CsvRegisterImpl();
			try {
				csvRegisterImpl.executeInsert(UniairConst.BM_M003,
						insertEntities, 1, insertEntities.size());
			} catch (Exception e) {
				System.err.println("team_statistics_data insert err execute: " + e);
			}
		}
	}

	/**
	 * 取得メソッド
	 * @param country 国
	 * @param league リーグ
	 * @param team チーム
	 * @param ha home&away
	 * @param year 年
	 */
	private TeamStaticDataOutputDTO getTeamStaticsData(String country, String league,
			String team, String ha, String year) {
		List<String> selectList = UniairColumnMapUtil.getKeyMap(UniairConst.BM_M003);
		String[] selDataList = new String[selectList.size()];
		for (int i = 0; i < selectList.size(); i++) {
			selDataList[i] = selectList.get(i);
		}

		String where = "country = '" + country + "' and league = '" + league + "' and "
				+ "team_name = '" + team + "' and HA = '" + ha + "' and year = '" + year + "'";

		List<List<String>> selectResultList = null;
		SqlMainLogic select = new SqlMainLogic();
		try {
			selectResultList = select.executeSelect(null, UniairConst.BM_M003, selDataList,
					where, null, "1");
		} catch (Exception e) {
			logger.error("select error -> ", e);
			throw new SystemException("", "", "", "err");
		}

		TeamStaticDataOutputDTO teamStaticDataOutputDTO = new TeamStaticDataOutputDTO();
		teamStaticDataOutputDTO.setUpdFlg(false);

		Integer[] scoreList = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
		if (!selectResultList.isEmpty()) {
			for (List<String> score : selectResultList) {
				scoreList[0] = Integer.parseInt(score.get(6));
				scoreList[1] = Integer.parseInt(score.get(7));
				scoreList[2] = Integer.parseInt(score.get(8));
				scoreList[3] = Integer.parseInt(score.get(9));
				scoreList[4] = Integer.parseInt(score.get(10));
				scoreList[5] = Integer.parseInt(score.get(11));
			}
			teamStaticDataOutputDTO.setUpdFlg(true);
			teamStaticDataOutputDTO.setSeq(selectResultList.get(0).get(0));
		}
		teamStaticDataOutputDTO.setScoreList(scoreList);
		return teamStaticDataOutputDTO;
	}

}
