package dev.common.readfile;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import dev.common.constant.BookMakersCommonConst;
import dev.common.entity.DataEntity;
import dev.common.readfile.dto.ReadFileOutputDTO;

/**
 * ファイル読み込みクラス
 * @author shiraishitoshio
 *
 */
@Component
public class ReadOrigin {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = ReadOrigin.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = ReadOrigin.class.getSimpleName();

	/**
	 * 統計データファイルの中身を取得する
	 * @param fileFullPath ファイル名（フルパス）
	 * @return readFileOutputDTO
	 */
	public ReadFileOutputDTO getFileBody(String fileFullPath) {
		final String METHOD_NAME = "getFileBody";

		ReadFileOutputDTO readFileOutputDTO = new ReadFileOutputDTO();
		File file = new File(fileFullPath);
		List<DataEntity> entiryList = new ArrayList<DataEntity>();
		try (BufferedReader br = new BufferedReader(
				new InputStreamReader(new FileInputStream(file)))) {
			String text;
			int row = 0;
			while ((text = br.readLine()) != null) {
				// ヘッダーは読み込まない
				if (row > 0) {
					// カンマ分割
					String[] parts = text.split(",", -1);
					DataEntity mappingDto = new DataEntity();
					mappingDto.setFile(fileFullPath);
					mappingDto.setHomeRank(parts[0]);
					mappingDto.setDataCategory(parts[1]);
					mappingDto.setTimes(parts[2]);
					mappingDto.setHomeTeamName(parts[3]);
					mappingDto.setHomeScore(parts[4]);
					mappingDto.setAwayRank(parts[5]);
					mappingDto.setAwayTeamName(parts[6]);
					mappingDto.setAwayScore(parts[7]);
					mappingDto.setHomeExp(parts[8]);
					mappingDto.setAwayExp(parts[9]);
					mappingDto.setHomeInGoalExp(parts[10]);
					mappingDto.setAwayInGoalExp(parts[11]);
					mappingDto.setHomeDonation(parts[12]);
					mappingDto.setAwayDonation(parts[13]);
					mappingDto.setHomeShootAll(parts[14].replace(".0", ""));
					mappingDto.setAwayShootAll(parts[15].replace(".0", ""));
					mappingDto.setHomeShootIn(parts[16].replace(".0", ""));
					mappingDto.setAwayShootIn(parts[17].replace(".0", ""));
					mappingDto.setHomeShootOut(parts[18].replace(".0", ""));
					mappingDto.setAwayShootOut(parts[19].replace(".0", ""));
					mappingDto.setHomeBlockShoot(parts[20].replace(".0", ""));
					mappingDto.setAwayBlockShoot(parts[21].replace(".0", ""));
					mappingDto.setHomeBigChance(parts[22].replace(".0", ""));
					mappingDto.setAwayBigChance(parts[23].replace(".0", ""));
					mappingDto.setHomeCorner(parts[24].replace(".0", ""));
					mappingDto.setAwayCorner(parts[25].replace(".0", ""));
					mappingDto.setHomeBoxShootIn(parts[26].replace(".0", ""));
					mappingDto.setAwayBoxShootIn(parts[27].replace(".0", ""));
					mappingDto.setHomeBoxShootOut(parts[28].replace(".0", ""));
					mappingDto.setAwayBoxShootOut(parts[29].replace(".0", ""));
					mappingDto.setHomeGoalPost(parts[30].replace(".0", ""));
					mappingDto.setAwayGoalPost(parts[31].replace(".0", ""));
					mappingDto.setHomeGoalHead(parts[32].replace(".0", ""));
					mappingDto.setAwayGoalHead(parts[33].replace(".0", ""));
					mappingDto.setHomeKeeperSave(parts[34].replace(".0", ""));
					mappingDto.setAwayKeeperSave(parts[35].replace(".0", ""));
					mappingDto.setHomeFreeKick(parts[36].replace(".0", ""));
					mappingDto.setAwayFreeKick(parts[37].replace(".0", ""));
					mappingDto.setHomeOffside(parts[38].replace(".0", ""));
					mappingDto.setAwayOffside(parts[39].replace(".0", ""));
					mappingDto.setHomeFoul(parts[40].replace(".0", ""));
					mappingDto.setAwayFoul(parts[41].replace(".0", ""));
					mappingDto.setHomeYellowCard(parts[42].replace(".0", ""));
					mappingDto.setAwayYellowCard(parts[43].replace(".0", ""));
					mappingDto.setHomeRedCard(parts[44].replace(".0", ""));
					mappingDto.setAwayRedCard(parts[45].replace(".0", ""));
					mappingDto.setHomeSlowIn(parts[46].replace(".0", ""));
					mappingDto.setAwaySlowIn(parts[47].replace(".0", ""));
					mappingDto.setHomeBoxTouch(parts[48].replace(".0", ""));
					mappingDto.setAwayBoxTouch(parts[49].replace(".0", ""));
					mappingDto.setHomePassCount(parts[50]);
					mappingDto.setAwayPassCount(parts[51]);
					mappingDto.setHomeLongPassCount(parts[52]);
					mappingDto.setAwayLongPassCount(parts[53]);
					mappingDto.setHomeFinalThirdPassCount(parts[54]);
					mappingDto.setAwayFinalThirdPassCount(parts[55]);
					mappingDto.setHomeCrossCount(parts[56]);
					mappingDto.setAwayCrossCount(parts[57]);
					mappingDto.setHomeTackleCount(parts[58]);
					mappingDto.setAwayTackleCount(parts[59]);
					mappingDto.setHomeClearCount(parts[60].replace(".0", ""));
					mappingDto.setAwayClearCount(parts[61].replace(".0", ""));
					mappingDto.setHomeDuelCount(parts[62].replace(".0", ""));
					mappingDto.setAwayDuelCount(parts[63].replace(".0", ""));
					mappingDto.setHomeInterceptCount(parts[64].replace(".0", ""));
					mappingDto.setAwayInterceptCount(parts[65].replace(".0", ""));
					mappingDto.setRecordTime(parts[66]);
					mappingDto.setWeather(parts[67]);
					mappingDto.setTemparature(parts[68]);
					mappingDto.setHumid(parts[69]);
					mappingDto.setJudgeMember(parts[70]);
					mappingDto.setHomeManager(parts[71]);
					mappingDto.setAwayManager(parts[72]);
					mappingDto.setHomeFormation(parts[73]);
					mappingDto.setAwayFormation(parts[74]);
					mappingDto.setStudium(parts[75]);
					mappingDto.setCapacity(parts[76]);
					mappingDto.setAudience(parts[77]);
					mappingDto.setHomeMaxGettingScorer(parts[78]);
					mappingDto.setAwayMaxGettingScorer(parts[79]);
					mappingDto.setHomeMaxGettingScorerGameSituation(parts[80]);
					mappingDto.setAwayMaxGettingScorerGameSituation(parts[81]);
					mappingDto.setHomeTeamHomeScore(parts[82]);
					mappingDto.setHomeTeamHomeLost(parts[83]);
					mappingDto.setAwayTeamHomeScore(parts[84]);
					mappingDto.setAwayTeamHomeLost(parts[85]);
					mappingDto.setHomeTeamAwayScore(parts[86]);
					mappingDto.setHomeTeamAwayLost(parts[87]);
					mappingDto.setAwayTeamAwayScore(parts[88]);
					mappingDto.setAwayTeamAwayLost(parts[89]);
					mappingDto.setNoticeFlg(parts[90]);
					mappingDto.setGoalTime(parts[91]);
					mappingDto.setGoalTeamMember(parts[92]);
					mappingDto.setHomeTeamStyle(parts[93]);
					mappingDto.setAwayTeamStyle(parts[94]);
					mappingDto.setProbablity(parts[95]);
					mappingDto.setPredictionScoreTime(parts[96]);
					entiryList.add(mappingDto);
				} else {
					row++;
				}
			}
			readFileOutputDTO.setResultCd(BookMakersCommonConst.NORMAL_CD);
			readFileOutputDTO.setDataList(entiryList);
		} catch (Exception e) {
			readFileOutputDTO.setExceptionProject(PROJECT_NAME);
			readFileOutputDTO.setExceptionClass(CLASS_NAME);
			readFileOutputDTO.setExceptionMethod(METHOD_NAME);
			readFileOutputDTO.setResultCd(BookMakersCommonConst.ERR_CD_ERR_FILE_READS);
			readFileOutputDTO.setErrMessage(BookMakersCommonConst.ERR_MESSAGE_ERR_FILE_READS);
			readFileOutputDTO.setThrowAble(e);
		}

		return readFileOutputDTO;
	}
}
