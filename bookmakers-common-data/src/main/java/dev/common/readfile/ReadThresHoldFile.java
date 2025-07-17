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


/**
 * ファイル読み込みクラス
 * @author shiraishitoshio
 *
 */
@Component
public class ReadThresHoldFile {

	/** Logger */
	//private static final Logger logger = LoggerFactory.getLogger(ReadFile.class);

	/** プロジェクト名 */
	private static final String PROJECT_NAME = ReadThresHoldFile.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = ReadThresHoldFile.class.getSimpleName();

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
					mappingDto.setSeq(parts[0]);
					mappingDto.setConditionResultDataSeqId(parts[1]);
					mappingDto.setGameTeamCategory(parts[2]);
					mappingDto.setTime(parts[3]);
					mappingDto.setHomeRank(parts[4]);
					mappingDto.setHomeTeamName(parts[5]);
					mappingDto.setHomeScore(parts[6]);
					mappingDto.setAwayRank(parts[7]);
					mappingDto.setAwayTeamName(parts[8]);
					mappingDto.setAwayScore(parts[9]);
					mappingDto.setHomeExp(parts[10]);
					mappingDto.setAwayExp(parts[11]);
					mappingDto.setHomeBallPossesion(parts[12]);
					mappingDto.setAwayBallPossesion(parts[13]);
					mappingDto.setHomeShootAll(parts[14]);
					mappingDto.setAwayShootAll(parts[15]);
					mappingDto.setHomeShootIn(parts[16]);
					mappingDto.setAwayShootIn(parts[17]);
					mappingDto.setHomeShootOut(parts[18]);
					mappingDto.setAwayShootOut(parts[19]);
					mappingDto.setHomeShootBlocked(parts[20]);
					mappingDto.setAwayShootBlocked(parts[21]);
					mappingDto.setHomeBigChance(parts[22]);
					mappingDto.setAwayBigChance(parts[23]);
					mappingDto.setHomeCornerKick(parts[24]);
					mappingDto.setAwayCornerKick(parts[25]);
					mappingDto.setHomeBoxShootIn(parts[26]);
					mappingDto.setAwayBoxShootIn(parts[27]);
					mappingDto.setHomeBoxShootOut(parts[28]);
					mappingDto.setAwayBoxShootOut(parts[29]);
					mappingDto.setHomeGoalPost(parts[30]);
					mappingDto.setAwayGoalPost(parts[31]);
					mappingDto.setHomeGoalHead(parts[32]);
					mappingDto.setAwayGoalHead(parts[33]);
					mappingDto.setHomeKeeperSave(parts[34]);
					mappingDto.setAwayKeeperSave(parts[35]);
					mappingDto.setHomeFreeKick(parts[36]);
					mappingDto.setAwayFreeKick(parts[37]);
					mappingDto.setHomeOffSide(parts[38]);
					mappingDto.setAwayOffSide(parts[39]);
					mappingDto.setHomeFoul(parts[40]);
					mappingDto.setAwayFoul(parts[41]);
					mappingDto.setHomeYellowCard(parts[42]);
					mappingDto.setAwayYellowCard(parts[43]);
					mappingDto.setHomeRedCard(parts[44]);
					mappingDto.setAwayRedCard(parts[45]);
					mappingDto.setHomeSlowIn(parts[46]);
					mappingDto.setAwaySlowIn(parts[47]);
					mappingDto.setHomeBoxTouch(parts[48]);
					mappingDto.setAwayBoxTouch(parts[49]);
					mappingDto.setHomePassCount(parts[50]);
					mappingDto.setAwayPassCount(parts[51]);
					mappingDto.setHomeFinalThirdPassCount(parts[52]);
					mappingDto.setAwayFinalThirdPassCount(parts[53]);
					mappingDto.setHomeCrossCount(parts[54]);
					mappingDto.setAwayCrossCount(parts[55]);
					mappingDto.setHomeTackleCount(parts[56]);
					mappingDto.setAwayTackleCount(parts[57]);
					mappingDto.setHomeClearCount(parts[58]);
					mappingDto.setAwayClearCount(parts[59]);
					mappingDto.setHomeInterceptCount(parts[60]);
					mappingDto.setAwayInterceptCount(parts[61]);
					mappingDto.setRecordTime(parts[62]);
					mappingDto.setWeather(parts[63]);
					mappingDto.setTemparature(parts[64]);
					mappingDto.setHumid(parts[65]);
					mappingDto.setJudgeMember(parts[66]);
					mappingDto.setHomeManager(parts[67]);
					mappingDto.setAwayManager(parts[68]);
					mappingDto.setHomeFormation(parts[69]);
					mappingDto.setAwayFormation(parts[70]);
					mappingDto.setStudium(parts[71]);
					mappingDto.setCapacity(parts[72]);
					mappingDto.setAudience(parts[73]);
					mappingDto.setHomeMaxGettingScorer(parts[74]);
					mappingDto.setAwayMaxGettingScorer(parts[75]);
					mappingDto.setHomeMaxGettingScorerGameSituation(parts[76]);
					mappingDto.setAwayMaxGettingScorerGameSituation(parts[77]);
					mappingDto.setHomeTeamHomeScore(parts[78]);
					mappingDto.setHomeTeamHomeLost(parts[79]);
					mappingDto.setAwayTeamHomeScore(parts[80]);
					mappingDto.setAwayTeamHomeLost(parts[81]);
					mappingDto.setHomeTeamAwayScore(parts[82]);
					mappingDto.setHomeTeamAwayLost(parts[83]);
					mappingDto.setAwayTeamAwayScore(parts[84]);
					mappingDto.setAwayTeamAwayLost(parts[85]);
					mappingDto.setNoticeFlg(parts[86]);
					mappingDto.setGoalTime(parts[87]);
					mappingDto.setGoalTeamMember(parts[88]);
					mappingDto.setJudge(parts[89]);
					mappingDto.setHomeTeamStyle(parts[90]);
					mappingDto.setAwayTeamStyle(parts[91]);
					mappingDto.setProbablity(parts[92]);
					mappingDto.setPredictionScoreTime(parts[93]);
					entiryList.add(mappingDto);
				} else {
					row++;
				}
			}
			readFileOutputDTO.setResultCd(BookMakersCommonConst.NORMAL_CD);
			readFileOutputDTO.setReadHoldDataList(entiryList);
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
}
