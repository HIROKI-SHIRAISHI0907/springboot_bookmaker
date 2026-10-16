package dev.common.getinfo;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
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
import dev.common.util.CsvContentUtil;
import dev.common.util.S3QuarantineUtil;

/**
 * 全容マスタ情報取得管理クラス
 *
 * <p>2026-10 修正（ヘッダしかない CSV が S3 に残り続ける問題）</p>
 * <ul>
 *   <li>ヘッダのみ（データ行 0 件）のファイルは、読み込みクラスに渡す前に判定し（{@link CsvContentUtil#isHeaderOnly}）、
 *       key = S3 キー、value = 空のリスト で返す。B007 はこのファイルを登録せずに削除する。
 *       （以前は空のファイルを結果に入れず、結果が空なら null を返していたため、削除されずに毎回読み続けていた）</li>
 *   <li>読み込みに失敗したファイルは結果に入れない。以前は 1 ファイルの例外で全体が null（何も登録しない）になっていたが、
 *       そのファイルだけ飛ばして他は続ける。</li>
 * </ul>
 * <p>戻り値: key = S3 キー、value = 全容マスタ（ヘッダのみなら空のリスト）。対象ファイルが無い・1件も読めなければ null。</p>
 * @author shiraishitoshio
 *
 * <p>2026-10 修正（読み込めない CSV が S3 に残り続ける問題）</p>
 * <ul>
 *   <li>読み込めないファイル（読み込みクラスがエラーを返した、またはデータ行はあるのに正しい行が 1 件も無い）は、
 *       {@link S3QuarantineUtil} で error/＜元のキー＞.error に退避する。残すと Lambda が「.csv あり」と判定して
 *       ECS を起動し続け、毎回同じファイルで失敗するため。中身は退避先に残るので後から調べられる。</li>
 *   <li>ダウンロード失敗・タイムアウトは一時的なエラーとみなし、退避せず次回もう一度読む。</li>
 * </ul>
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
        int emptyFiles = 0;
        int failedFiles = 0;
        int quarantined = 0;

        for (String key : keys) {
            byte[] bytes;
            try (InputStream is = s3Operator.download(bucket, key)) {
                bytes = is.readAllBytes();
            } catch (Exception e) {
                // このファイルだけ飛ばす（削除せず次回もう一度読む）
                failedFiles++;
                String msgCd = MessageCdConst.MCD00005E_OTHER_EXECUTION_GREEN_FIN;
                this.manageLoggerComponent.debugErrorLog(
                        PROJECT_NAME, CLASS_NAME, METHOD_NAME, msgCd, e,
                        "S3 all_league_master.csv ダウンロード失敗 key=" + key + "（削除せず次回もう一度読む）");
                continue;
            }

            // ヘッダのみ: 空のリストで返し、B007 で削除する
            if (CsvContentUtil.isHeaderOnly(bytes)) {
                emptyFiles++;
                entityMap.put(key, new ArrayList<>());
                this.manageLoggerComponent.debugInfoLog(
                        PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG,
                        "データ行なし（ヘッダのみ）のため登録せず削除対象にします: key=" + key);
                continue;
            }

            try (InputStream is = new ByteArrayInputStream(bytes)) {
                ReadFileOutputDTO dto = this.readAllLeague.getFileBodyFromStream(is, key);
                if (dto == null || !BookMakersCommonConst.NORMAL_CD.equals(dto.getResultCd())) {
                    // 読み込みエラー: 退避する（他のキーは処理継続。残すと Lambda が ECS を起動し続けるため）
                    failedFiles++;
                    String msgCd = MessageCdConst.MCD00003E_EXECUTION_SKIP;
                    this.manageLoggerComponent.debugErrorLog(
                            PROJECT_NAME, CLASS_NAME, METHOD_NAME, msgCd, null,
                            "CSV解析エラー key=" + key + " msg=" + (dto == null ? "読み込み結果なし" : dto.getErrMessage())
                                    + "（error/ に退避します）" + dto.getThrowAble());
                    if (S3QuarantineUtil.quarantine(bucket, key, "読み込みエラー（ReadAllLeague）",
                            s3Operator, manageLoggerComponent, PROJECT_NAME, CLASS_NAME, METHOD_NAME)) {
                        quarantined++;
                    }
                    continue;
                }

                List<AllLeagueMasterEntity> list = dto.getAllLeagueMasterList();
                if (list == null || list.isEmpty()) {
                    // データ行はあるのに正しい行が 1 件も無い（ヘッダのみは上で判定済み）: 壊れたファイルとして退避する
                    failedFiles++;
                    this.manageLoggerComponent.debugErrorLog(
                            PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00003E_EXECUTION_SKIP, null,
                            "正しいデータ行が0件: key=" + key + "（error/ に退避します）");
                    if (S3QuarantineUtil.quarantine(bucket, key, "正しいデータ行が0件（ReadAllLeague）",
                            s3Operator, manageLoggerComponent, PROJECT_NAME, CLASS_NAME, METHOD_NAME)) {
                        quarantined++;
                    }
                    continue;
                }

                entityMap.put(key, list);

            } catch (Exception e) {
                failedFiles++;
                String msgCd = MessageCdConst.MCD00005E_OTHER_EXECUTION_GREEN_FIN;
                this.manageLoggerComponent.debugErrorLog(
                        PROJECT_NAME, CLASS_NAME, METHOD_NAME, msgCd, e,
                        "S3 all_league_master.csv 読込失敗 key=" + key + "（削除せず次回もう一度読む）");
            }
        }

        log.info("[B007] 読み込み結果: files={} (データあり={}, ヘッダのみ={}, 読み込み失敗={}, うち退避={})",
                keys.size(), entityMap.size() - emptyFiles, emptyFiles, failedFiles, quarantined);
        return entityMap.isEmpty() ? null : entityMap;
    }
}
