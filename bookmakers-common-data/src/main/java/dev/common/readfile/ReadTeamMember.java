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
import dev.common.entity.TeamMemberMasterEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.readfile.dto.ReadFileOutputDTO;
import dev.common.util.DateUtil;

/**
 * ファイル読み込みクラス
 * @author shiraishitoshio
 *
 */
@Component
public class ReadTeamMember {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = ReadTeamMember.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = ReadTeamMember.class.getSimpleName();

	/** 実行モード */
	private static final String EXEC_MODE = "READ_TEAM_MEMBER";

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

		String errCd = BookMakersCommonConst.NORMAL_CD;
		String fillChar = "";

		ReadFileOutputDTO readFileOutputDTO = new ReadFileOutputDTO();
		File file = new File(fileFullPath);
		List<TeamMemberMasterEntity> entiryList = new ArrayList<TeamMemberMasterEntity>();
		try (BufferedReader br = new BufferedReader(
				new InputStreamReader(new FileInputStream(file)))) {
			String text;
			int row = 0;
			while ((text = br.readLine()) != null) {
				// ヘッダーは読み込まない
				if (row > 0) {
					// カンマ分割
					String[] parts = text.split(",", -1);
					TeamMemberMasterEntity mappingDto = new TeamMemberMasterEntity();
					mappingDto.setFile(fileFullPath);
					mappingDto.setCountry(parts[0]);
					mappingDto.setLeague(parts[1]);
					mappingDto.setTeam(parts[2]);
					mappingDto.setMember(parts[3]);
					mappingDto.setPosition(parts[4]);
					mappingDto.setJersey(parts[5].replace("N/A", "").replace(".0", ""));
					mappingDto.setScore(parts[6].replace(".0", ""));
					mappingDto.setAge(parts[7].replace(".0", ""));
					try {
						mappingDto.setBirth(DateUtil.convertOnlyDD_MM_YYYY(parts[8]));
					} catch (Exception e) {
						// 何もしないがエラーコード設定
						errCd = BookMakersCommonConst.ERR_CD_ABNORMALY_DATA;
						fillChar += "data: " + parts[8] + ", " + e + "|| ";
					}
					mappingDto.setMarketValue(parts[9].replace("N/A", ""));
					mappingDto.setLoanBelong(parts[10]);
					try {
						mappingDto
								.setDeadlineContractDate(DateUtil.convertOnlyDD_MM_YYYY(parts[11].replace("N/A", "")));
					} catch (Exception e) {
						// 一部のエラーについてはそのデータを除外して登録するがエラーコードは設定
						errCd = BookMakersCommonConst.ERR_CD_ABNORMALY_DATA;
						fillChar += "data: " + parts[8] + ", " + e + "|| ";
					}
					mappingDto.setFacePicPath(parts[12]);
					mappingDto.setInjury(parts[13]);
					mappingDto.setLatestInfoDate(parts[14]);
					mappingDto = (BookMakersCommonConst.NORMAL_CD.equals(errCd)) ? mappingDto : null;
					if (mappingDto != null) {
						entiryList.add(mappingDto);
					}
				} else {
					row++;
				}
			}
			readFileOutputDTO.setResultCd(errCd);
			readFileOutputDTO.setMemberList(entiryList);
			readFileOutputDTO.setErrMessage(fillChar);
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
