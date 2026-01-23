package dev.common.getinfo;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.common.config.PathConfig;
import dev.common.constant.BookMakersCommonConst;
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
	private static final String CLASS_NAME = GetTeamInfo.class.getSimpleName();

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

	    if (keys == null || keys.isEmpty()) {
	        this.manageLoggerComponent.debugInfoLog(
	                PROJECT_NAME, CLASS_NAME, METHOD_NAME, "データなし(S3)", "GetTeamInfo");
	        return null;
	    }

	    Map<String, List<CountryLeagueMasterEntity>> entityMap = new LinkedHashMap<>();

	    for (String key : keys) {
	        try (InputStream is = s3Operator.download(bucket, key)) {

	            ReadFileOutputDTO dto = this.readTeam.getFileBodyFromStream(is, key);

	            if (!BookMakersCommonConst.NORMAL_CD.equals(dto.getResultCd())) {
	                this.manageLoggerComponent.createBusinessException(
	                        dto.getExceptionProject(),
	                        dto.getExceptionClass(),
	                        dto.getExceptionMethod(),
	                        dto.getErrMessage(),
	                        dto.getThrowAble());
	            }

	            entityMap.put(key, dto.getCountryLeagueMasterList());

	        } catch (Exception e) {
	            this.manageLoggerComponent.debugErrorLog(
	                    PROJECT_NAME, CLASS_NAME, METHOD_NAME, "S3 teamData 読み込み失敗 key=" + key, e);
	            this.manageLoggerComponent.createBusinessException(
	                    PROJECT_NAME, CLASS_NAME, METHOD_NAME, "S3 teamData 読み込みエラー: " + key, e);
	        }
	    }
	    return entityMap;
	}
}
