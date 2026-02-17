package dev.common.getinfo;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.common.config.PathConfig;
import dev.common.constant.BookMakersCommonConst;
import dev.common.constant.MessageCdConst;
import dev.common.entity.CountryLeagueMasterEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.readfile.ReadTeam;
import dev.common.readfile.dto.ReadFileOutputDTO;
import dev.common.s3.S3Operator;

/**
 * チームマスタ情報取得管理クラス
 * @author shiraishitoshio
 *
 */
@Component
public class GetTeamInfo {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = GetTeamInfo.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = GetTeamInfo.class.getName();

	/** LoggerFactory */
	private static final Logger log = LoggerFactory.getLogger(GetTeamInfo.class);

	/** 取得バケット正規表現：teamData_X.csv */
	private static final Pattern TEAMDATA_SEQ_CSV =
            Pattern.compile("^teamData_(\\d+)\\.csv$");

	/** S3オペレーター */
	@Autowired
	private S3Operator s3Operator;

	/** パス設定 */
	@Autowired
	private PathConfig config;

	/** ファイル読み込みクラス */
	@Autowired
	private ReadTeam readTeam;

	/**
	 * ログ管理クラス
	 */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * 取得メソッド
	 */
	public Map<String, List<CountryLeagueMasterEntity>> getData() {
	    final String METHOD_NAME = "getData";

	    String bucket = config.getS3BucketsTeamData();
	    List<String> keys = s3Operator.listTeamDataKeysSortedBySeqAsc(bucket, TEAMDATA_SEQ_CSV);

	    log.info("[B003] S3 bucket={} prefix={} keys.size={} keys(sample)={}",
	    		  bucket, TEAMDATA_SEQ_CSV,
	    		  (keys==null ? -1 : keys.size()),
	    		  (keys==null ? null : keys.stream().limit(5).collect(Collectors.toList()))
	    		);

	    if (keys == null || keys.isEmpty()) {
	    	String msgCd = MessageCdConst.MCD00002I_BATCH_EXECUTION_SKIP;
	        this.manageLoggerComponent.debugInfoLog(
	            PROJECT_NAME, CLASS_NAME, METHOD_NAME, msgCd, "データなし(S3)");
	        return null;
	    }

	    Map<String, List<CountryLeagueMasterEntity>> entityMap = new LinkedHashMap<>();

	    for (String key : keys) {
	        try (InputStream is = s3Operator.download(bucket, key)) {

	            ReadFileOutputDTO dto = this.readTeam.getFileBodyFromStream(is, key);

	            if (!BookMakersCommonConst.NORMAL_CD.equals(dto.getResultCd())) {
	            	String msgCd = MessageCdConst.MCD00003E_EXECUTION_SKIP;
	                this.manageLoggerComponent.debugErrorLog(
	                    PROJECT_NAME, CLASS_NAME, METHOD_NAME, msgCd, null, dto.getErrMessage());
	                continue;
	            }

	            entityMap.put(key, dto.getCountryLeagueMasterList());

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
