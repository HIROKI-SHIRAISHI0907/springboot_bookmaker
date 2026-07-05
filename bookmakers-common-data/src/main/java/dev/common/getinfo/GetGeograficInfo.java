package dev.common.getinfo;

import java.io.InputStream;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.common.config.PathConfig;
import dev.common.constant.BookMakersCommonConst;
import dev.common.constant.MessageCdConst;
import dev.common.entity.TeamLocationEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.readfile.ReadGeografic;
import dev.common.readfile.dto.ReadFileOutputDTO;
import dev.common.s3.S3Operator;

/**
 * 地理情報取得管理クラス
 * @author shiraishitoshio
 *
 */
@Component
public class GetGeograficInfo {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = GetGeograficInfo.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = GetGeograficInfo.class.getName();

	/** LoggerFactory */
	private static final Logger log = LoggerFactory.getLogger(GetGeograficInfo.class);

	/** S3オペレーター */
	@Autowired
	private S3Operator s3Operator;

	/** パス設定 */
	@Autowired
	private PathConfig config;

	/** ファイル読み込みクラス */
	@Autowired
	private ReadGeografic readGeografic;

	/**
	 * ログ管理クラス
	 */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * 取得メソッド
	 */
	public List<TeamLocationEntity> getData() {
		final String METHOD_NAME = "getData";

        String bucket = config.getS3Geografic();
        String key = "outputs/b015_team_location.csv";    // outputsフォルダ
        log.info("[B015] S3 bucket={} prefix={} ",
	    		  bucket, key
	    		);

        try (InputStream is = s3Operator.download(bucket, key)) {
            ReadFileOutputDTO dto = readGeografic.getFileBodyFromStream(is, key);
            if (!BookMakersCommonConst.NORMAL_CD.equals(dto.getResultCd())) {
            	String msgCd = MessageCdConst.MCD00005E_OTHER_EXECUTION_GREEN_FIN;
    	        this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME,
    	        		METHOD_NAME, msgCd, null, "S3 b015_team_location.csv 読み込みエラー");
            	this.manageLoggerComponent.createBusinessException(
        	            PROJECT_NAME, CLASS_NAME, METHOD_NAME, msgCd, null, null);
            }

            List<TeamLocationEntity> entity = dto.getTeamLocationList();
            if (entity == null || entity.isEmpty()) {
            	String msgCd = MessageCdConst.MCD00002I_BATCH_EXECUTION_SKIP;
    	        this.manageLoggerComponent.debugInfoLog(
    	            PROJECT_NAME, CLASS_NAME, METHOD_NAME, msgCd, "データなし(S3)");
                return null;
            }
            return entity;

        } catch (Exception e) {
        	String msgCd = MessageCdConst.MCD00005E_OTHER_EXECUTION_GREEN_FIN;
	        this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, msgCd, e, "S3 b015_team_location.csv ダウンロードエラー");
	        this.manageLoggerComponent.createBusinessException(
		            PROJECT_NAME, CLASS_NAME, METHOD_NAME, msgCd, null, e);
            return null;

        }
	}
}
