package dev.common.readfile;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.common.constant.BookMakersCommonConst;
import dev.common.entity.BookDataEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.readfile.dto.ReadFileOutputDTO;


/**
 * ファイル読み込みクラス
 * @author shiraishitoshio
 *
 */
@Component
public class ReadStat {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = ReadStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = ReadStat.class.getSimpleName();

	/** 実行モード */
	private static final String EXEC_MODE = "READ_FILE";

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * 統計データファイルの中身を取得する
	 * @param fileFullPath ファイル名（フルパス）
	 * @return readFileOutputDTO
	 */
	public ReadFileOutputDTO getFileBody(String fileFullPath) {
		final String METHOD_NAME = "getFileBody";
		// ログ出力
		this.manageLoggerComponent.init(EXEC_MODE, fileFullPath);
		this.manageLoggerComponent.debugStartInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

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
					mappingDto.setHomeInGoalExp(parts[12]);
					mappingDto.setAwayInGoalExp(parts[13]);
					mappingDto.setHomeBallPossesion(parts[14]);
					mappingDto.setAwayBallPossesion(parts[15]);
					mappingDto.setHomeShootAll(parts[16]);
					mappingDto.setAwayShootAll(parts[17]);
					mappingDto.setHomeShootIn(parts[18]);
					mappingDto.setAwayShootIn(parts[19]);
					mappingDto.setHomeShootOut(parts[20]);
					mappingDto.setAwayShootOut(parts[21]);
					mappingDto.setHomeShootBlocked(parts[22]);
					mappingDto.setAwayShootBlocked(parts[23]);
					mappingDto.setHomeBigChance(parts[24]);
					mappingDto.setAwayBigChance(parts[25]);
					mappingDto.setHomeCornerKick(parts[26]);
					mappingDto.setAwayCornerKick(parts[27]);
					mappingDto.setHomeBoxShootIn(parts[28]);
					mappingDto.setAwayBoxShootIn(parts[29]);
					mappingDto.setHomeBoxShootOut(parts[30]);
					mappingDto.setAwayBoxShootOut(parts[31]);
					mappingDto.setHomeGoalPost(parts[32]);
					mappingDto.setAwayGoalPost(parts[33]);
					mappingDto.setHomeGoalHead(parts[34]);
					mappingDto.setAwayGoalHead(parts[35]);
					mappingDto.setHomeKeeperSave(parts[36]);
					mappingDto.setAwayKeeperSave(parts[37]);
					mappingDto.setHomeFreeKick(parts[38]);
					mappingDto.setAwayFreeKick(parts[39]);
					mappingDto.setHomeOffSide(parts[40]);
					mappingDto.setAwayOffSide(parts[41]);
					mappingDto.setHomeFoul(parts[42]);
					mappingDto.setAwayFoul(parts[43]);
					mappingDto.setHomeYellowCard(parts[44]);
					mappingDto.setAwayYellowCard(parts[45]);
					mappingDto.setHomeRedCard(parts[46]);
					mappingDto.setAwayRedCard(parts[47]);
					mappingDto.setHomeSlowIn(parts[48]);
					mappingDto.setAwaySlowIn(parts[49]);
					mappingDto.setHomeBoxTouch(parts[50]);
					mappingDto.setAwayBoxTouch(parts[51]);
					mappingDto.setHomePassCount(parts[52]);
					mappingDto.setAwayPassCount(parts[53]);
					mappingDto.setHomePassCount(parts[54]);
					mappingDto.setAwayPassCount(parts[55]);
					mappingDto.setHomeFinalThirdPassCount(parts[56]);
					mappingDto.setAwayFinalThirdPassCount(parts[57]);
					mappingDto.setHomeCrossCount(parts[58]);
					mappingDto.setAwayCrossCount(parts[59]);
					mappingDto.setHomeTackleCount(parts[60]);
					mappingDto.setAwayTackleCount(parts[61]);
					mappingDto.setHomeClearCount(parts[62]);
					mappingDto.setAwayClearCount(parts[63]);
					mappingDto.setHomeDuelCount(parts[64]);
					mappingDto.setAwayDuelCount(parts[65]);
					mappingDto.setHomeInterceptCount(parts[66]);
					mappingDto.setAwayInterceptCount(parts[67]);
					mappingDto.setRecordTime(parts[68]);
					mappingDto.setWeather(parts[69]);
					mappingDto.setTemperature(parts[70]);
					mappingDto.setHumid(parts[71]);
					mappingDto.setJudgeMember(parts[72]);
					mappingDto.setHomeManager(parts[73]);
					mappingDto.setAwayManager(parts[74]);
					mappingDto.setHomeFormation(parts[75]);
					mappingDto.setAwayFormation(parts[76]);
					mappingDto.setStudium(parts[77]);
					mappingDto.setCapacity(parts[78]);
					mappingDto.setAudience(parts[79]);
					mappingDto.setHomeMaxGettingScorer(parts[80]);
					mappingDto.setAwayMaxGettingScorer(parts[81]);
					mappingDto.setHomeMaxGettingScorerGameSituation(parts[82]);
					mappingDto.setAwayMaxGettingScorerGameSituation(parts[83]);
					mappingDto.setHomeTeamHomeScore(parts[84]);
					mappingDto.setHomeTeamHomeLost(parts[85]);
					mappingDto.setAwayTeamHomeScore(parts[86]);
					mappingDto.setAwayTeamHomeLost(parts[87]);
					mappingDto.setHomeTeamAwayScore(parts[88]);
					mappingDto.setHomeTeamAwayLost(parts[89]);
					mappingDto.setAwayTeamAwayScore(parts[90]);
					mappingDto.setAwayTeamAwayLost(parts[91]);
					mappingDto.setNoticeFlg(parts[92]);
					mappingDto.setGoalTime(parts[93]);
					mappingDto.setGoalTeamMember(parts[94]);
					mappingDto.setJudge(parts[95]);
					mappingDto.setHomeTeamStyle(parts[96]);
					mappingDto.setAwayTeamStyle(parts[97]);
					mappingDto.setProbablity(parts[98]);
					mappingDto.setPredictionScoreTime(parts[99]);
					mappingDto.setFilePath(fileFullPath);
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
			return readFileOutputDTO;
		}

		String messageCd = "";
		String fillChar = "読み取りファイル名: " + fileFullPath;
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, fillChar);

		this.manageLoggerComponent.debugEndInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		this.manageLoggerComponent.clear();

		return readFileOutputDTO;
	}

	/**
	 * 条件分岐データファイルの中身を取得する
	 * @param fileFullPath ファイル名（フルパス）
	 * @return repData
	 * @throws Exception
	 */
	public String getConditionDataFileBody(String fileFullPath) throws Exception {
		final String METHOD_NAME = "getFileBody";
		// ログ出力
		this.manageLoggerComponent.debugStartInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		File file = new File(fileFullPath);
		if (!file.exists()) {
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, null, null);
			return null;
		}
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

			this.manageLoggerComponent.debugEndInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME);

			return repData;
		} catch (Exception e) {
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, null, e);
			throw e;
		}
	}
}
