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
import dev.common.entity.CountryLeagueSeasonMasterEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.readfile.dto.ReadFileOutputDTO;


/**
 * ファイル読み込みクラス
 * @author shiraishitoshio
 *
 */
@Component
public class ReadSeason {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = ReadSeason.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = ReadSeason.class.getSimpleName();

	/** 実行モード */
	private static final String EXEC_MODE = "READ_SEASON";

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
		List<CountryLeagueSeasonMasterEntity> entiryList = new ArrayList<CountryLeagueSeasonMasterEntity>();
		try (BufferedReader br = new BufferedReader(
				new InputStreamReader(new FileInputStream(file)))) {
			String text;
			int row = 0;
			while ((text = br.readLine()) != null) {
				// ヘッダーは読み込まない
				if (row > 0) {
					// カンマ分割
					String[] parts = text.split(",", -1);
					CountryLeagueSeasonMasterEntity mappingDto = new CountryLeagueSeasonMasterEntity();
					mappingDto.setFile(fileFullPath);
					mappingDto.setCountry(parts[0]);
					mappingDto.setLeague(parts[1]);
					mappingDto.setStartSeasonDate(parts[2]);
					mappingDto.setEndSeasonDate(parts[3]);
					entiryList.add(mappingDto);
				} else {
					row++;
				}
			}
			readFileOutputDTO.setResultCd(BookMakersCommonConst.NORMAL_CD);
			readFileOutputDTO.setCountryLeagueSeasonList(entiryList);
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
