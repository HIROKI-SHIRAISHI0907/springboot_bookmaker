package dev.common.getinfo;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
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
import dev.common.util.CsvContentUtil;

/**
 * チームマスタ情報取得管理クラス
 *
 * <p>2026-10 修正（ヘッダしかない CSV が S3 に残り続ける問題）</p>
 * <ul>
 *   <li>ヘッダのみ（データ行 0 件）のファイルは、読み込みクラスに渡す前に判定し（{@link CsvContentUtil#isHeaderOnly}）、
 *       key = S3 キー、value = 空のリスト で返す。B004 はこのファイルを登録せずに削除する。
 *       （以前は ReadTeam が異常コードを返すと結果に入らず、削除されずに毎回読み続けていた）</li>
 *   <li>読み込みに失敗したファイル（例外・異常コード）は結果に入れない（削除せず次回もう一度読む）。
 *       以前は 1 ファイルの例外で全体が null（何も登録しない）になっていたが、そのファイルだけ飛ばして他は続ける。</li>
 * </ul>
 * <p>戻り値: key = S3 キー、value = その国・リーグ（ヘッダのみなら空のリスト）。対象ファイルが無ければ null。</p>
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

	    log.info("[B004] S3 bucket={} prefix={} keys.size={} keys(sample)={}",
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
	    int emptyFiles = 0;
	    int failedFiles = 0;

	    for (String key : keys) {
	        byte[] bytes;
	        try (InputStream is = s3Operator.download(bucket, key)) {
	            bytes = is.readAllBytes();
	        } catch (Exception e) {
	            // このファイルだけ飛ばす（削除せず次回もう一度読む）
	            failedFiles++;
	        	String msgCd = MessageCdConst.MCD00005E_OTHER_EXECUTION_GREEN_FIN;
		        this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, msgCd, e,
		        		"S3 teamData ダウンロード失敗 key=" + key + "（削除せず次回もう一度読む）");
		        continue;
	        }

	        // ヘッダのみ: 空のリストで返し、B004 で削除する
	        if (CsvContentUtil.isHeaderOnly(bytes)) {
	            emptyFiles++;
	            entityMap.put(key, new ArrayList<>());
	            this.manageLoggerComponent.debugInfoLog(
	                    PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG,
	                    "データ行なし（ヘッダのみ）のため登録せず削除対象にします: key=" + key);
	            continue;
	        }

	        try (InputStream is = new ByteArrayInputStream(bytes)) {
	            ReadFileOutputDTO dto = this.readTeam.getFileBodyFromStream(is, key);
	            if (dto == null || !BookMakersCommonConst.NORMAL_CD.equals(dto.getResultCd())) {
	                failedFiles++;
	            	String msgCd = MessageCdConst.MCD00003E_EXECUTION_SKIP;
	                this.manageLoggerComponent.debugErrorLog(
	                    PROJECT_NAME, CLASS_NAME, METHOD_NAME, msgCd, null,
	                    (dto == null ? "読み込み結果なし" : dto.getErrMessage()) + " key=" + key + "（削除せず次回もう一度読む）");
	                continue;
	            }

	            List<CountryLeagueMasterEntity> list = dto.getCountryLeagueMasterList();
	            if (list == null || list.isEmpty()) {
	                // 読み込みクラスが 0 件と判定した場合もヘッダのみ扱い
	                emptyFiles++;
	                entityMap.put(key, new ArrayList<>());
	                this.manageLoggerComponent.debugInfoLog(
	                        PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG,
	                        "登録対象0件のため登録せず削除対象にします: key=" + key);
	                continue;
	            }
	            entityMap.put(key, list);

	        } catch (Exception e) {
	            failedFiles++;
	        	String msgCd = MessageCdConst.MCD00005E_OTHER_EXECUTION_GREEN_FIN;
		        this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, msgCd, e,
		        		"S3 teamData 読み込み失敗 key=" + key + "（削除せず次回もう一度読む）");
	        }
	    }

	    log.info("[B004] 読み込み結果: files={} (データあり={}, ヘッダのみ={}, 読み込み失敗={})",
	            keys.size(), entityMap.size() - emptyFiles, emptyFiles, failedFiles);
	    return entityMap;
	}
}