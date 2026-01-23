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
import dev.common.util.DateUtil;


/**
 * ファイル読み込みクラス
 * @author shiraishitoshio
 *
 */
@Component
public class ReadFuture implements ReadFileBodyIF {

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
	@Override
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
					mappingDto.setGameTeamCategory(parts[0]);
					// ===== futureTime =====
	                try {
	                    mappingDto.setFutureTime(
	                            DateUtil.normalizeToJapaneseFormat(parts[1])
	                    );
	                } catch (Exception e) {
	                    String msg = "futureTime parse error"
	                            + " file=" + fileFullPath
	                            + " row=" + row
	                            + " raw=[" + parts[1] + "]";
	                    this.manageLoggerComponent.debugErrorLog(
	                            PROJECT_NAME, CLASS_NAME, METHOD_NAME, msg, e);
	                    // 今回は空で続行（必要なら continue; で行スキップも可）
	                    mappingDto.setFutureTime("");
	                }
					mappingDto.setHomeRank(parts[2].replace(".0", ""));
					mappingDto.setAwayRank(parts[3].replace(".0", ""));
					mappingDto.setHomeTeamName(parts[4]);
					mappingDto.setAwayTeamName(parts[5]);
					mappingDto.setHomeMaxGettingScorer(parts[6]);
					mappingDto.setAwayMaxGettingScorer(parts[7]);
					mappingDto.setHomeTeamHomeScore(parts[8]);
					mappingDto.setHomeTeamHomeLost(parts[9]);
					mappingDto.setAwayTeamHomeScore(parts[10]);
					mappingDto.setAwayTeamHomeLost(parts[11]);
					mappingDto.setHomeTeamAwayScore(parts[12]);
					mappingDto.setHomeTeamAwayLost(parts[13]);
					mappingDto.setAwayTeamAwayScore(parts[14]);
					mappingDto.setAwayTeamAwayLost(parts[15]);
					mappingDto.setGameLink(parts[16]);
					mappingDto.setDataTime(parts[17]);
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
