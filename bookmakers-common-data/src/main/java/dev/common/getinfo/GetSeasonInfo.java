package dev.common.getinfo;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.common.config.PathConfig;
import dev.common.constant.BookMakersCommonConst;
import dev.common.constant.MessageCdConst;
import dev.common.entity.CountryLeagueSeasonMasterEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.readfile.ReadSeason;
import dev.common.readfile.dto.ReadFileOutputDTO;
import dev.common.s3.S3Operator;
import dev.common.util.CsvContentUtil;

/**
 * シーズン情報取得管理クラス
 *
 * <p>2026-10 修正（ヘッダしかない CSV が S3 に残り続ける問題）</p>
 * <ul>
 *   <li>以前はデータ行が 0 件（ヘッダのみ）でも null を返していたため、B003 が「データなし」で終わり、
 *       season_data.csv が削除されずに毎回読み続けていた。</li>
 *   <li>→ 戻り値を次のように分けた。
 *     <ul>
 *       <li>null … ファイルが無い／読み込みエラー（削除しない。次回もう一度読む）</li>
 *       <li>空のリスト … ファイルはあるがデータ行が 0 件（ヘッダのみ。呼び出し側で削除する）</li>
 *       <li>1 件以上 … 通常どおり登録する</li>
 *     </ul></li>
 *   <li>ファイルが無いときは、ダウンロードエラーではなく「データなし」の情報ログにした（存在を先に確認する）。</li>
 *   <li>ヘッダのみかは読み込みクラスに渡す前に判定する（{@link CsvContentUtil#isHeaderOnly}。ReadSeason の返し方に左右されない）。</li>
 * </ul>
 * @author shiraishitoshio
 *
 */
@Component
public class GetSeasonInfo {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = GetSeasonInfo.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = GetSeasonInfo.class.getName();

	/** LoggerFactory */
	private static final Logger log = LoggerFactory.getLogger(GetSeasonInfo.class);

	/** 取得ファイル（バケット直下） */
	public static final String SEASON_KEY = "season_data.csv";

	/** S3オペレーター */
	@Autowired
	private S3Operator s3Operator;

	/** パス設定 */
	@Autowired
	private PathConfig config;

	/** ファイル読み込みクラス */
	@Autowired
	private ReadSeason readSeason;

	/**
	 * ログ管理クラス
	 */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * 取得メソッド
	 *
	 * @return null = ファイルなし・読み込みエラー / 空のリスト = ヘッダのみ（削除対象） / 1件以上 = 登録対象
	 */
	public List<CountryLeagueSeasonMasterEntity> getData() {
		final String METHOD_NAME = "getData";

        String bucket = config.getS3BucketsTeamSeasonDateData();
        String key = SEASON_KEY;                 // ★ バケット直下にある前提
        // もし "YYYY-mm-dd/season_data.csv" なら key をそれに合わせる
        log.info("[B003] S3 bucket={} prefix={} ",
	    		  bucket, key
	    		);

        // ファイルが無い場合は「データなし」（エラーにしない）
        try {
            if (!s3Operator.existsOnS3(bucket, key)) {
            	String msgCd = MessageCdConst.MCD00002I_BATCH_EXECUTION_SKIP;
    	        this.manageLoggerComponent.debugInfoLog(
    	            PROJECT_NAME, CLASS_NAME, METHOD_NAME, msgCd, "データなし(S3: ファイルなし)");
                return null;
            }
        } catch (Exception e) {
            // 存在確認に失敗した場合は、従来どおりダウンロードを試みる
            log.warn("[B003] S3 存在確認に失敗（ダウンロードを試みます）: {}", e.toString());
        }

        byte[] bytes;
        try (InputStream is = s3Operator.download(bucket, key)) {
            bytes = is.readAllBytes();
        } catch (Exception e) {
        	String msgCd = MessageCdConst.MCD00005E_OTHER_EXECUTION_GREEN_FIN;
	        this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, msgCd, e, "S3 season_data.csv ダウンロードエラー");
            return null;
        }

        // ヘッダのみ: 読み込みクラスに渡さず、空のリストを返す（B003 でファイルを削除する）
        if (CsvContentUtil.isHeaderOnly(bytes)) {
        	String msgCd = MessageCdConst.MCD00002I_BATCH_EXECUTION_SKIP;
	        this.manageLoggerComponent.debugInfoLog(
	            PROJECT_NAME, CLASS_NAME, METHOD_NAME, msgCd,
	            "データ行なし（ヘッダのみ）のため登録せず削除対象にします: " + key);
            return new ArrayList<>();
        }

        try (InputStream is = new ByteArrayInputStream(bytes)) {
            ReadFileOutputDTO dto = readSeason.getFileBodyFromStream(is, key);
            if (dto == null) {
            	String msgCd = MessageCdConst.MCD00005E_OTHER_EXECUTION_GREEN_FIN;
    	        this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME,
    	        		METHOD_NAME, msgCd, null, "S3 season_data.csv 読み込み結果なし（削除せず次回もう一度読む）");
                return null;
            }
            if (!BookMakersCommonConst.NORMAL_CD.equals(dto.getResultCd())) {
            	String msgCd = MessageCdConst.MCD00005E_OTHER_EXECUTION_GREEN_FIN;
    	        this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME,
    	        		METHOD_NAME, msgCd, null, "S3 season_data.csv 読み込みエラー");
            	this.manageLoggerComponent.createBusinessException(
        	            PROJECT_NAME, CLASS_NAME, METHOD_NAME, msgCd, null, null);
            }

            List<CountryLeagueSeasonMasterEntity> entity = dto.getCountryLeagueSeasonList();
            if (entity == null || entity.isEmpty()) {
            	// 読み込みクラスが 0 件と判定した場合もヘッダのみ扱い: 空のリストを返し、B003 でファイルを削除する
            	String msgCd = MessageCdConst.MCD00002I_BATCH_EXECUTION_SKIP;
    	        this.manageLoggerComponent.debugInfoLog(
    	            PROJECT_NAME, CLASS_NAME, METHOD_NAME, msgCd,
    	            "データ行なし（ヘッダのみ）のため登録せず削除対象にします: " + key);
                return new ArrayList<>();
            }
            return entity;

        } catch (Exception e) {
        	String msgCd = MessageCdConst.MCD00005E_OTHER_EXECUTION_GREEN_FIN;
	        this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, msgCd, e, "S3 season_data.csv 読み込みエラー");
            return null;

        }
	}
}