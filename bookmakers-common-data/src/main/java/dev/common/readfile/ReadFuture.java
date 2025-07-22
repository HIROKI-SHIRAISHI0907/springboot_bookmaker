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
import dev.common.entity.FutureEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.readfile.dto.ReadFileOutputDTO;


/**
 * ファイル読み込みクラス
 * @author shiraishitoshio
 *
 */
@Component
public class ReadFuture {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = ReadFuture.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = ReadFuture.class.getSimpleName();

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
		List<FutureEntity> entiryList = new ArrayList<FutureEntity>();
		try (BufferedReader br = new BufferedReader(
				new InputStreamReader(new FileInputStream(file)))) {
			String text;
			int row = 0;
			while ((text = br.readLine()) != null) {
				// ヘッダーは読み込まない
				if (row > 0) {
					// カンマ分割
					String[] parts = text.split(",", -1);
					FutureEntity mappingDto = new FutureEntity();
					mappingDto.setFile(fileFullPath);
					mappingDto.setSeq(parts[0]);
					mappingDto.setGameTeamCategory(parts[1]);
					mappingDto.setFutureTime(parts[2]);
					mappingDto.setHomeRank(parts[3]);
					mappingDto.setAwayRank(parts[4]);
					mappingDto.setHomeTeamName(parts[5]);
					mappingDto.setHomeScore(parts[6]);
					mappingDto.setAwayTeamName(parts[7]);
					mappingDto.setAwayScore(parts[8]);
					mappingDto.setHomeMaxGettingScorer(parts[9]);
					mappingDto.setAwayMaxGettingScorer(parts[10]);
					mappingDto.setHomeTeamHomeScore(parts[11]);
					mappingDto.setHomeTeamHomeLost(parts[12]);
					mappingDto.setAwayTeamHomeScore(parts[13]);
					mappingDto.setAwayTeamHomeLost(parts[14]);
					mappingDto.setHomeTeamAwayScore(parts[15]);
					mappingDto.setHomeTeamAwayLost(parts[16]);
					mappingDto.setAwayTeamAwayScore(parts[17]);
					mappingDto.setAwayTeamAwayLost(parts[18]);
					mappingDto.setGameLink(parts[19]);
					mappingDto.setDataTime(parts[20]);
					entiryList.add(mappingDto);
				} else {
					row++;
				}
			}
			readFileOutputDTO.setResultCd(BookMakersCommonConst.NORMAL_CD);
			readFileOutputDTO.setFutureList(entiryList);
		} catch (Exception e) {
			readFileOutputDTO.setExceptionProject(PROJECT_NAME);
			readFileOutputDTO.setExceptionClass(CLASS_NAME);
			readFileOutputDTO.setExceptionMethod(METHOD_NAME);
			readFileOutputDTO.setResultCd(BookMakersCommonConst.ERR_CD_ERR_FILE_READS);
			readFileOutputDTO.setErrMessage(BookMakersCommonConst.ERR_MESSAGE_ERR_FILE_READS);
			readFileOutputDTO.setThrowAble(e);
			return readFileOutputDTO;
		}

		this.manageLoggerComponent.debugEndInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		this.manageLoggerComponent.clear();

		return readFileOutputDTO;
	}

}
