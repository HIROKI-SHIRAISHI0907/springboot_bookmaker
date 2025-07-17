package dev.common.readfile;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import dev.common.constant.BookMakersCommonConst;
import dev.common.entity.BookDataEntity;
import dev.common.readfile.dto.ReadFileOutputDTO;
import dev.common.util.DateUtil;


/**
 * ファイル読み込みクラス
 * @author shiraishitoshio
 *
 */
@Component
public class ReadFile {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = ReadFile.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = ReadFile.class.getSimpleName();

	/**
	 * 統計データファイルの中身を取得する
	 * @param fileFullPath ファイル名（フルパス）
	 * @return readFileOutputDTO
	 */
	public ReadFileOutputDTO getFileBody(String fileFullPath) {
		//logger.info(" read file start : {} " , CLASS_NAME);

		final String METHOD_NAME = "getFileBody";

		ReadFileOutputDTO readFileOutputDTO = new ReadFileOutputDTO();
		File file = new File(fileFullPath);
		List<BookDataEntity> entiryList = new ArrayList<BookDataEntity>();
		try (BufferedReader br = new BufferedReader(
				new InputStreamReader(new FileInputStream(file)))) {
			String text;
			int row = 0;
			while ((text = br.readLine()) != null) {
				// ヘッダーは読み込まない
				if (row > 0) {
					// カンマ分割
					String[] parts = text.split(",", -1);
					BookDataEntity mappingDto = new BookDataEntity();
					mappingDto.setHomeRank(parts[0]);
					mappingDto.setGameTeamCategory(parts[1]);
					mappingDto.setTime(parts[2]);
					mappingDto.setHomeTeamName(parts[3]);
					mappingDto.setHomeScore(parts[4]);
					mappingDto.setAwayRank(parts[5]);
					mappingDto.setAwayTeamName(parts[6]);
					mappingDto.setAwayScore(parts[7]);
					mappingDto.setHomeExp(parts[8]);
					mappingDto.setAwayExp(parts[9]);
					mappingDto.setHomeBallPossesion(parts[10]);
					mappingDto.setAwayBallPossesion(parts[11]);
					mappingDto.setHomeShootAll(parts[12]);
					mappingDto.setAwayShootAll(parts[13]);
					mappingDto.setHomeShootIn(parts[14]);
					mappingDto.setAwayShootIn(parts[15]);
					mappingDto.setHomeShootOut(parts[16]);
					mappingDto.setAwayShootOut(parts[17]);
					mappingDto.setHomeShootBlocked(parts[18]);
					mappingDto.setAwayShootBlocked(parts[19]);
					mappingDto.setHomeBigChance(parts[20]);
					mappingDto.setAwayBigChance(parts[21]);
					mappingDto.setHomeCornerKick(parts[22]);
					mappingDto.setAwayCornerKick(parts[23]);
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
					mappingDto.setHomeOffSide(parts[36]);
					mappingDto.setAwayOffSide(parts[37]);
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
					mappingDto.setHomeFinalThirdPassCount(parts[50]);
					mappingDto.setAwayFinalThirdPassCount(parts[51]);
					mappingDto.setHomeCrossCount(parts[52]);
					mappingDto.setAwayCrossCount(parts[53]);
					mappingDto.setHomeTackleCount(parts[54]);
					mappingDto.setAwayTackleCount(parts[55]);
					mappingDto.setHomeClearCount(parts[56]);
					mappingDto.setAwayClearCount(parts[57]);
					mappingDto.setHomeInterceptCount(parts[58]);
					mappingDto.setAwayInterceptCount(parts[59]);
					String times = (parts[60].contains("#")) ? DateUtil.getSysDate() : parts[60];
					mappingDto.setRecordTime(DateUtil.convertTimestamp(times));
					mappingDto.setWeather(parts[61]);
					mappingDto.setTemparature(parts[62]);
					mappingDto.setHumid(parts[63]);
					mappingDto.setJudgeMember(parts[64]);
					mappingDto.setHomeManager(parts[65]);
					mappingDto.setAwayManager(parts[66]);
					mappingDto.setHomeFormation(parts[67]);
					mappingDto.setAwayFormation(parts[68]);
					mappingDto.setStudium(parts[69]);
					mappingDto.setCapacity(parts[70]);
					mappingDto.setAudience(parts[71]);
					mappingDto.setHomeMaxGettingScorer(parts[72]);
					mappingDto.setAwayMaxGettingScorer(parts[73]);
					mappingDto.setHomeMaxGettingScorerGameSituation(parts[74]);
					mappingDto.setAwayMaxGettingScorerGameSituation(parts[75]);
					mappingDto.setHomeTeamHomeScore(parts[76]);
					mappingDto.setHomeTeamHomeLost(parts[77]);
					mappingDto.setAwayTeamHomeScore(parts[78]);
					mappingDto.setAwayTeamHomeLost(parts[79]);
					mappingDto.setHomeTeamAwayScore(parts[80]);
					mappingDto.setHomeTeamAwayLost(parts[81]);
					mappingDto.setAwayTeamAwayScore(parts[82]);
					mappingDto.setAwayTeamAwayLost(parts[83]);
					mappingDto.setNoticeFlg(parts[84]);
					mappingDto.setGoalTime(parts[86]);
					mappingDto.setGoalTeamMember(parts[87]);
					mappingDto.setHomeTeamStyle(parts[89]);
					mappingDto.setAwayTeamStyle(parts[90]);
					mappingDto.setProbablity(parts[91]);
					mappingDto.setPredictionScoreTime(parts[92]);
					entiryList.add(mappingDto);
				} else {
					row++;
				}
			}
			readFileOutputDTO.setResultCd(BookMakersCommonConst.NORMAL_CD);
			readFileOutputDTO.setReadDataList(entiryList);
		} catch (Exception e) {
			readFileOutputDTO.setExceptionProject(PROJECT_NAME);
			readFileOutputDTO.setExceptionClass(CLASS_NAME);
			readFileOutputDTO.setExceptionMethod(METHOD_NAME);
			readFileOutputDTO.setResultCd(BookMakersCommonConst.ERR_CD_ERR_FILE_READS);
			readFileOutputDTO.setErrMessage(BookMakersCommonConst.ERR_MESSAGE_ERR_FILE_READS);
			readFileOutputDTO.setThrowAble(e);
		}
		//logger.info(" read file end : {} " , CLASS_NAME);

		return readFileOutputDTO;
	}

	/**
	 * 条件分岐データファイルの中身を取得する
	 * @param fileFullPath ファイル名（フルパス）
	 * @return repData
	 * @throws Exception
	 */
	public String getConditionDataFileBody(String fileFullPath) throws Exception {
		File file = new File(fileFullPath);
		try (BufferedReader br = new BufferedReader(
				new InputStreamReader(new FileInputStream(file)))) {
			StringBuilder data = new StringBuilder();
			String text;
			while ((text = br.readLine()) != null) {
				data.append(text);
			}
			String repData = data.toString();
			// 全角,半角スペースを削除
			repData = repData.strip();
			repData = repData.replace(" ", "");
			return repData;
		} catch (Exception e) {
			//logger.error("get condition data error -> ", e);
			throw e;
		}
		//logger.info(" read file end : {} " , CLASS_NAME);
	}
}
