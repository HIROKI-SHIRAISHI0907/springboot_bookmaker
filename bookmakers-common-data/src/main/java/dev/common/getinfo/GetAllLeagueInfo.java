package dev.common.getinfo;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.common.config.PathConfig;
import dev.common.constant.BookMakersCommonConst;
import dev.common.constant.MessageCdConst;
import dev.common.entity.AllLeagueMasterEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.readfile.ReadAllLeague;
import dev.common.readfile.dto.ReadFileOutputDTO;
import dev.common.s3.S3Operator;

/**
 * 全容マスタ情報取得管理クラス
 * @author shiraishitoshio
 *
 */
@Component
public class GetAllLeagueInfo {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = GetAllLeagueInfo.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = GetAllLeagueInfo.class.getSimpleName();

	/** 取得バケット正規表現：all_league_master.csv */
	private static final String ALLLEAGUEDATA_CSV = "all_league_master.csv";

	/** S3オペレーター */
	@Autowired
	private S3Operator s3Operator;

	/** パス設定 */
	@Autowired
	private PathConfig config;

	/** ファイル読み込みクラス */
	@Autowired
	private ReadAllLeague readAllLeague;

	/**
	 * ログ管理クラス
	 */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * 取得メソッド
	 */
	public Map<String, List<AllLeagueMasterEntity>> getData() {
	    final String METHOD_NAME = "getData";

	    String bucket = config.getS3BucketsAllLeagueData();
	    List<String> keys = s3Operator.listKeysBySuffix(bucket, ALLLEAGUEDATA_CSV);

	    if (keys == null || keys.isEmpty()) {
	    	String msgCd = MessageCdConst.MCD00002I_BATCH_EXECUTION_SKIP;
	        this.manageLoggerComponent.debugInfoLog(
	            PROJECT_NAME, CLASS_NAME, METHOD_NAME, msgCd, "データなし(S3)");
	        return null;
	    }

	    Map<String, List<AllLeagueMasterEntity>> entityMap = new LinkedHashMap<>();

	    for (String key : keys) {
	        try (InputStream is = s3Operator.download(bucket, key)) {

	            ReadFileOutputDTO dto = this.readAllLeague.getFileBodyFromStream(is, key);

	            if (!BookMakersCommonConst.NORMAL_CD.equals(dto.getResultCd())) {
	            	String msgCd = MessageCdConst.MCD00003E_EXECUTION_SKIP;
	                this.manageLoggerComponent.debugErrorLog(
	                    PROJECT_NAME, CLASS_NAME, METHOD_NAME, msgCd, null, dto.getErrMessage());
	                continue;
	            }

	            entityMap.put(key, dto.getAllLeagueMasterList());

	        } catch (Exception e) {
	        	String msgCd = MessageCdConst.MCD00005E_OTHER_EXECUTION_GREEN_FIN;
		        this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, msgCd, e, "S3 teamData 読み込み失敗 key=" + key);
		        this.manageLoggerComponent.createBusinessException(
		            PROJECT_NAME, CLASS_NAME, METHOD_NAME, msgCd, null, e);
	        }
	    }
	    return entityMap;
	}
}
