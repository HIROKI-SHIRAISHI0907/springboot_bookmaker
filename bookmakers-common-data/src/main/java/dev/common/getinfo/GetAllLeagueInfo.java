package dev.common.getinfo;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 */
@Component
public class GetAllLeagueInfo {

    /** プロジェクト名 */
    private static final String PROJECT_NAME = GetAllLeagueInfo.class.getProtectionDomain()
            .getCodeSource().getLocation().getPath();

    /** クラス名 */
    private static final String CLASS_NAME = GetAllLeagueInfo.class.getName();

    /** Logger */
    private static final Logger log = LoggerFactory.getLogger(GetAllLeagueInfo.class);

    /** 取得対象ファイル名 */
    private static final String ALL_LEAGUE_CSV = "all_league_master.csv";

    @Autowired
    private S3Operator s3Operator;

    @Autowired
    private PathConfig config;

    @Autowired
    private ReadAllLeague readAllLeague;

    @Autowired
    private ManageLoggerComponent manageLoggerComponent;

    /**
     * 取得メソッド
     */
    public Map<String, List<AllLeagueMasterEntity>> getData() {
        final String METHOD_NAME = "getData";

        String bucket = config.getS3BucketsAllLeagueData();
        List<String> keys = s3Operator.listKeysBySuffix(bucket, ALL_LEAGUE_CSV);

        log.info("[B007] S3 bucket={} suffix={} keys.size={} keys(sample)={}",
                bucket, ALL_LEAGUE_CSV,
                (keys == null ? -1 : keys.size()),
                (keys == null ? null : keys.stream().limit(5).collect(Collectors.toList()))
        );

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
                    // ファイル単位でスキップ（他のキーは処理継続）
                    String msgCd = MessageCdConst.MCD00003E_EXECUTION_SKIP;
                    this.manageLoggerComponent.debugErrorLog(
                            PROJECT_NAME, CLASS_NAME, METHOD_NAME, msgCd, null,
                            "CSV解析エラー key=" + key + " msg=" + dto.getErrMessage());
                    continue;
                }

                List<AllLeagueMasterEntity> list = dto.getAllLeagueMasterList();
                if (list == null || list.isEmpty()) {
                    // 空でも map には入れない（必要なら入れる方針に変えてOK）
                    String msgCd = MessageCdConst.MCD00002I_BATCH_EXECUTION_SKIP;
                    this.manageLoggerComponent.debugInfoLog(
                            PROJECT_NAME, CLASS_NAME, METHOD_NAME, msgCd, "データなし key=" + key);
                    continue;
                }

                entityMap.put(key, list);

            } catch (Exception e) {
                // ダウンロード/ストリーム例外は業務例外（止める想定）
                String msgCd = MessageCdConst.MCD00005E_OTHER_EXECUTION_GREEN_FIN;
                this.manageLoggerComponent.debugErrorLog(
                        PROJECT_NAME, CLASS_NAME, METHOD_NAME, msgCd, e,
                        "S3 all_league_master.csv ダウンロード/読込失敗 key=" + key);
                this.manageLoggerComponent.createBusinessException(
                        PROJECT_NAME, CLASS_NAME, METHOD_NAME, msgCd, null, e);
                return null; // createBusinessExceptionがthrowしない設計の保険
            }
        }

        return entityMap.isEmpty() ? null : entityMap;
    }
}
