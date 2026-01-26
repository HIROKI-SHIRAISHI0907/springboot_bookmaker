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
import dev.common.constant.MessageCdConst;
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
	 * @param is ストリーム名
	 * @param key キー
	 * @return readFileOutputDTO
	 */
	@Override
	public ReadFileOutputDTO getFileBodyFromStream(InputStream is, String key) {
	    final String METHOD_NAME = "getFileBodyFromStream";

	    ReadFileOutputDTO dto = new ReadFileOutputDTO();
	    try {
	        // ここでS3 keyをログ共通項目に入れるなら init してもOK
	        this.manageLoggerComponent.init(EXEC_MODE, key);

	        List<FutureEntity> list = parseCsv(is, key);

	        dto.setResultCd(BookMakersCommonConst.NORMAL_CD);
	        dto.setFutureList(list);
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
	    	this.manageLoggerComponent.clear();
	    }
	}

	/**
	 * パースラッパー
	 * @param is
	 * @param fileIdForLog
	 * @return
	 * @throws Exception
	 */
	private List<FutureEntity> parseCsv(InputStream is, String fileIdForLog) throws Exception {
	    try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
	        return parseCsv(br, fileIdForLog);
	    }
	}

	/**
	 * パースを行う
	 * @param br
	 * @param fileIdForLog
	 * @return
	 * @throws Exception
	 */
	private List<FutureEntity> parseCsv(BufferedReader br, String fileIdForLog) throws Exception {
	    final String METHOD_NAME = "parseCsv";
	    List<FutureEntity> entiryList = new ArrayList<>();

	    String text;
	    int row = 0;

	    while ((text = br.readLine()) != null) {
	        // 1行ごとにrowを増やす（ヘッダ行も含めてカウント）
	        row++;

	        // 1行目はヘッダーなのでスキップ
	        if (row == 1) continue;

	        String[] parts = text.split(",", -1);

	        // CSV列数が足りないと ArrayIndexOutOfBounds になるので最低限ガード
	        if (parts.length < 18) {
	        	String messageCd = MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION;
	            String msg = "CSV column error: file=" + fileIdForLog + " row=" + row + " cols=" + parts.length;
	            this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME,
	            		METHOD_NAME, messageCd, null, msg);
	            continue; // 欠損行はスキップ（必要なら例外でも可）
	        }

	        FutureEntity mappingDto = new FutureEntity();
	        mappingDto.setFile(fileIdForLog);
	        mappingDto.setGameTeamCategory(parts[0]);

	        // futureTime
	        try {
	            mappingDto.setFutureTime(DateUtil.normalizeToJapaneseFormat(parts[1]));
	        } catch (Exception e) {
	        	String messageCd = MessageCdConst.MCD00020E_DATE_ERROR;
	            String msg = "futureTime parse error"
	                    + " file=" + fileIdForLog
	                    + " row=" + row
	                    + " raw=[" + parts[1] + "]";
	            this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME,
	            		METHOD_NAME, messageCd, e, msg);
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
	    }
	    return entiryList;
	}
}
