package dev.common.readfile;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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
public class ReadTeamMember implements ReadFileBodyIF {

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
	@Override
	public ReadFileOutputDTO getFileBodyFromStream(InputStream is, String key) {
	    final String METHOD_NAME = "getFileBodyFromStream";

	    this.manageLoggerComponent.init(EXEC_MODE, key);
	    this.manageLoggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);

	    String errCd = BookMakersCommonConst.NORMAL_CD;
	    String fillChar = "";

	    ReadFileOutputDTO dto = new ReadFileOutputDTO();
	    List<TeamMemberMasterEntity> entiryList = new ArrayList<>();

	    try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
	        String text;
	        int row = 0;
	        while ((text = br.readLine()) != null) {
	            row++;
	            // 1行目ヘッダーはスキップ
	            if (row == 1) continue;
	            if (text.trim().isEmpty()) continue;
	            String[] parts = text.split(",", -1);
	            // 列数ガード（最低15列想定）
	            if (parts.length < 15) {
	                errCd = BookMakersCommonConst.ERR_CD_ABNORMALY_DATA;
	                String msg = "column shortage"
	                        + " key=" + key
	                        + " row=" + row
	                        + " cols=" + parts.length;
	                this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, msg, null);
	                fillChar += msg + "|| ";
	                continue;
	            }

	            TeamMemberMasterEntity mappingDto = new TeamMemberMasterEntity();
	            mappingDto.setFile(key);
	            mappingDto.setCountry(parts[0]);
	            mappingDto.setLeague(parts[1]);
	            mappingDto.setTeam(parts[2]);
	            mappingDto.setMember(parts[3]);
	            mappingDto.setPosition(parts[4]);
	            mappingDto.setJersey(parts[5].replace("N/A", "").replace(".0", ""));
	            mappingDto.setScore(parts[6].replace(".0", ""));
	            mappingDto.setAge(parts[7].replace(".0", ""));

	            // birth
	            try {
	                mappingDto.setBirth(DateUtil.convertOnlyDD_MM_YYYY(parts[8]));
	            } catch (Exception e) {
	                errCd = BookMakersCommonConst.ERR_CD_ABNORMALY_DATA;
	                String msg = "birth parse error"
	                        + " key=" + key
	                        + " row=" + row
	                        + " raw=[" + parts[8] + "]";
	                this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, msg, e);
	                fillChar += msg + ", " + e + "|| ";
	                mappingDto.setBirth("");
	            }

	            mappingDto.setMarketValue(parts[9].replace("N/A", ""));
	            mappingDto.setLoanBelong(parts[10]);

	            // deadlineContractDate
	            try {
	                mappingDto.setDeadlineContractDate(
	                        DateUtil.convertOnlyDD_MM_YYYY(parts[11].replace("N/A", ""))
	                );
	            } catch (Exception e) {
	                errCd = BookMakersCommonConst.ERR_CD_ABNORMALY_DATA;
	                String msg = "deadlineContractDate parse error"
	                        + " key=" + key
	                        + " row=" + row
	                        + " raw=[" + parts[11] + "]";
	                this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, msg, e);
	                fillChar += msg + ", " + e + "|| ";
	                mappingDto.setDeadlineContractDate("");
	            }

	            mappingDto.setFacePicPath(parts[12]);
	            mappingDto.setInjury(parts[13]);
	            mappingDto.setLatestInfoDate(parts[14]);

	            entiryList.add(mappingDto);
	        }

	        dto.setResultCd(errCd);
	        dto.setMemberList(entiryList);
	        dto.setErrMessage(fillChar);
	        return dto;

	    } catch (Exception e) {
	        dto.setExceptionProject(PROJECT_NAME);
	        dto.setExceptionClass(CLASS_NAME);
	        dto.setExceptionMethod(METHOD_NAME);
	        dto.setResultCd(BookMakersCommonConst.ERR_CD_ERR_FILE_READS);
	        dto.setErrMessage(BookMakersCommonConst.ERR_MESSAGE_ERR_FILE_READS);
	        dto.setThrowAble(e);
	        return dto;

	    } finally {
	        this.manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);
	        this.manageLoggerComponent.clear();
	    }
	}
}
