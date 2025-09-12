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
					mappingDto.setHomeDonation(parts[10]);
					mappingDto.setAwayDonation(parts[11]);
					mappingDto.setHomeShootAll(parts[12]);
					mappingDto.setAwayShootAll(parts[13]);
					mappingDto.setHomeShootIn(parts[14]);
					mappingDto.setAwayShootIn(parts[15]);
					mappingDto.setHomeShootOut(parts[16]);
					mappingDto.setAwayShootOut(parts[17]);
					mappingDto.setHomeBlockShoot(parts[18]);
					mappingDto.setAwayBlockShoot(parts[19]);
					mappingDto.setHomeBigChance(parts[20]);
					mappingDto.setAwayBigChance(parts[21]);
					mappingDto.setHomeCorner(parts[22]);
					mappingDto.setAwayCorner(parts[23]);
					mappingDto.setHomeBoxShootIn(parts[24]);
					mappingDto.setAwayBoxShootIn(parts[25]);
					mappingDto.setHomeBoxShootOut(parts[26]);
					mappingDto.setAwayBoxShootOut(parts[27]);
					mappingDto.setHomeGoalPost(parts[28]);
					mappingDto.setAwayGoalPost(parts[29]);
					mappingDto.setHomeGoalHead(parts[30]);
					mappingDto.setAwayGoalHead(parts[31]);
					mappingDto.setHomeKeeperSave(parts[32]);
					mappingDto.setAwayKeeperSave(parts[33]);
					mappingDto.setHomeFreeKick(parts[34]);
					mappingDto.setAwayFreeKick(parts[35]);
					mappingDto.setHomeOffside(parts[36]);
					mappingDto.setAwayOffside(parts[37]);
					mappingDto.setHomeFoul(parts[38]);
					mappingDto.setAwayFoul(parts[39]);
					mappingDto.setHomeYellowCard(parts[40]);
					mappingDto.setAwayYellowCard(parts[41]);
					mappingDto.setHomeRedCard(parts[42]);
					mappingDto.setAwayRedCard(parts[43]);
					mappingDto.setHomeSlowIn(parts[44]);
					mappingDto.setAwaySlowIn(parts[45]);
					mappingDto.setHomeBoxTouch(parts[46]);
					mappingDto.setAwayBoxTouch(parts[47]);
					mappingDto.setHomePassCount(parts[48]);
					mappingDto.setAwayPassCount(parts[49]);
					mappingDto.setHomeLongPassCount(parts[50]);
					mappingDto.setAwayLongPassCount(parts[51]);
					mappingDto.setHomeFinalThirdPassCount(parts[52]);
					mappingDto.setAwayFinalThirdPassCount(parts[53]);
					mappingDto.setHomeCrossCount(parts[54]);
					mappingDto.setAwayCrossCount(parts[55]);
					mappingDto.setHomeTackleCount(parts[56]);
					mappingDto.setAwayTackleCount(parts[57]);
					mappingDto.setHomeClearCount(parts[58]);
					mappingDto.setAwayClearCount(parts[59]);
					mappingDto.setHomeDuelCount(parts[60]);
					mappingDto.setAwayDuelCount(parts[61]);
					mappingDto.setHomeInterceptCount(parts[62]);
					mappingDto.setAwayInterceptCount(parts[63]);
					mappingDto.setRecordTime(parts[64]);
					mappingDto.setWeather(parts[65]);
					mappingDto.setTemparature(parts[66]);
					mappingDto.setHumid(parts[67]);
					mappingDto.setJudgeMember(parts[68]);
					mappingDto.setHomeManager(parts[69]);
					mappingDto.setAwayManager(parts[70]);
					mappingDto.setHomeFormation(parts[71]);
					mappingDto.setAwayFormation(parts[72]);
					mappingDto.setStudium(parts[73]);
					mappingDto.setCapacity(parts[74]);
					mappingDto.setAudience(parts[75]);
					mappingDto.setHomeMaxGettingScorer(parts[76]);
					mappingDto.setAwayMaxGettingScorer(parts[77]);
					mappingDto.setHomeMaxGettingScorerGameSituation(parts[78]);
					mappingDto.setAwayMaxGettingScorerGameSituation(parts[79]);
					mappingDto.setHomeTeamHomeScore(parts[80]);
					mappingDto.setHomeTeamHomeLost(parts[81]);
					mappingDto.setAwayTeamHomeScore(parts[82]);
					mappingDto.setAwayTeamHomeLost(parts[83]);
					mappingDto.setHomeTeamAwayScore(parts[84]);
					mappingDto.setHomeTeamAwayLost(parts[85]);
					mappingDto.setAwayTeamAwayScore(parts[86]);
					mappingDto.setAwayTeamAwayLost(parts[87]);
					mappingDto.setNoticeFlg(parts[88]);
					mappingDto.setGoalTime(parts[89]);
					mappingDto.setGoalTeamMember(parts[90]);
					mappingDto.setHomeTeamStyle(parts[91]);
					mappingDto.setAwayTeamStyle(parts[92]);
					mappingDto.setProbablity(parts[93]);
					mappingDto.setPredictionScoreTime(parts[94]);
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
