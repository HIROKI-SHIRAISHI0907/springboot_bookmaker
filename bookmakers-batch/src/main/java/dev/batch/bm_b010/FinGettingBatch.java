package dev.batch.bm_b010;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dev.batch.common.AbstractJobBatchTemplate;
import dev.batch.repository.bm.EcsScrapeTaskProgressBatchRepository;
import dev.batch.repository.bm.MatchKeySaveRepository;
import dev.common.config.PathConfig;
import dev.common.constant.MessageCdConst;
import dev.common.entity.DataEntity;
import dev.common.getinfo.GetOriginInfo;
import dev.common.readfile.dto.MatchKeyItem;
import dev.common.s3.S3Operator;
import dev.common.util.FileDeleteUtil;

/**
 * 「終了済」欠損データ登録バッチ実行クラス。
 * <p>
 * 国・リーグの全容マスタデータを取得し、登録ロジック（Transactional想定）を実行する。
 * </p>
 *
 * <p><b>実行方式</b></p>
 * <ul>
 *   <li>開始/終了ログを必ず出力する</li>
 *   <li>例外は内部で捕捉し、debugErrorLog に例外を付与して出力する</li>
 *   <li>戻り値で成功/失敗を返却する</li>
 * </ul>
 *
 * @author shiraishitoshio
 */
@Service("B010")
public class FinGettingBatch extends AbstractJobBatchTemplate {

    private static final Logger log = LoggerFactory.getLogger(FinGettingBatch.class);

    /** プロジェクト名 */
    private static final String PROJECT_NAME = FinGettingBatch.class.getProtectionDomain()
            .getCodeSource().getLocation().getPath();

    /** クラス名 */
    private static final String CLASS_NAME = FinGettingBatch.class.getName();

    /** エラーコード */
    private static final String ERROR_CODE = "BM_B010_ERROR";

    /** バッチコード */
    private static final String BATCH_CODE = "B010";

    /** 自動FAILED化のタイムアウト（分） */
    private static final int AUTO_FAIL_TIMEOUT_MINUTES = 20;

    /** 自動FAILED時のエラーメッセージ */
    private static final String AUTO_FAIL_ERROR_MESSAGE = "AUTO_FAILED_TIMEOUT_20_MINUTES";

    @Override
    protected String batchCode() {
        return BATCH_CODE;
    }

    @Override
    protected String errorCode() {
        return ERROR_CODE;
    }

    @Override
    protected String projectName() {
        return PROJECT_NAME;
    }

    @Override
    protected String className() {
        return CLASS_NAME;
    }

    /**
     * 同一バッチコードのECSタスクが起動要求中または実行中の場合はスキップする。
     *
     * ただし、最新1件が20分以上放置されている open status なら
     * 自動で FAILED に更新してから判定する。
     */
    @Override
    protected boolean shouldSkipExecution() {
        int updated = ecsScrapeTaskProgressBatchRepository.failLatestTimedOutTask(
                batchCode(),
                AUTO_FAIL_TIMEOUT_MINUTES,
                AUTO_FAIL_ERROR_MESSAGE
        );

        if (updated > 0) {
            log.warn("Auto failed stale ECS scrape task. batchCode={}, timeoutMinutes={}, errorMessage={}",
                    batchCode(), AUTO_FAIL_TIMEOUT_MINUTES, AUTO_FAIL_ERROR_MESSAGE);
        }

        return ecsScrapeTaskProgressBatchRepository.existsRunningTask(batchCode());
    }

    /**
     * スキップ理由を返す。
     *
     * @return スキップ理由
     */
    @Override
    protected String skipReason() {
        return "同一バッチコードのECSスクレイピングタスクが起動要求中または実行中のためスキップします。 batchCode=" + batchCode();
    }

    @Autowired
    private MatchKeySaveRepository matchKeySaveRepository;

    @Autowired
    private GetOriginInfo getOriginInfo;

    @Autowired
    private FinGettingStat finGettingStat;

    @Autowired
    private FinGettingTruncate finGettingTruncate;

    @Autowired
    private FutureStartFlgService futureStartFlgService;

    @Autowired
    private PathConfig pathConfig;

    @Autowired
    private S3Operator s3Operator;

    @Autowired
    private EcsScrapeTaskProgressBatchRepository ecsScrapeTaskProgressBatchRepository;

    @Override
    protected void doExecute(JobContext ctx) throws Exception {
        final String METHOD_NAME = "doExecute";

        this.manageLoggerComponent.init(null, null);
        this.manageLoggerComponent.debugStartInfoLog(
                PROJECT_NAME, CLASS_NAME, METHOD_NAME);

        List<String> insertPath = new ArrayList<String>();
        final String jsonFolder = pathConfig.getB008JsonFolder();
        final String jsonPath = jsonFolder + "b008_fin_getting_data.json";
        final Path jsonFilePath = Paths.get(jsonPath);
        final String s3Key = "fin/" + jsonFilePath.getFileName().toString();
        insertPath.add(s3Key);

        try {
            List<MatchKeyItem> items = matchKeySaveRepository.findMatchKeys().stream()
                    .map(k -> {
                        MatchKeyItem e = new MatchKeyItem();
                        e.setMatchKey(k);
                        return e;
                    })
                    .collect(Collectors.toList());

            if (items.isEmpty()) {
                String ERROR_CODE = MessageCdConst.MCD00003E_EXECUTION_SKIP;
                this.manageLoggerComponent.debugInfoLog(
                        PROJECT_NAME, CLASS_NAME, METHOD_NAME, ERROR_CODE,
                        "items.isEmpty() マッチキーが取得できなかったため処理を終了します。");
                updateFlg(null);
                return;
            }

            Map<String, List<DataEntity>> map = getOriginInfo.getData(items);
            this.finGettingStat.finGettingStat(map);

            finGettingTruncate.truncate();

            String bucket = pathConfig.getS3BucketsOutputs();
            FileDeleteUtil.deleteS3Files(
                    insertPath,
                    bucket,
                    s3Operator,
                    manageLoggerComponent,
                    PROJECT_NAME,
                    CLASS_NAME,
                    METHOD_NAME,
                    "b008_fin_getting_data.json");

            updateFlg(map);

            this.manageLoggerComponent.debugEndInfoLog(
                    PROJECT_NAME, CLASS_NAME, METHOD_NAME);
            this.manageLoggerComponent.clear();
        } catch (Exception e) {
            this.manageLoggerComponent.debugErrorLog(
                    PROJECT_NAME,
                    CLASS_NAME,
                    METHOD_NAME,
                    ERROR_CODE,
                    e);
            throw e;
        }
    }

    private void updateFlg(Map<String, List<DataEntity>> map) {
        final String METHOD_NAME = "updateFlg";

        try {
            futureStartFlgService.execute(map);
        } catch (Exception e) {
            String messageCd = MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION;
            this.manageLoggerComponent.createSystemException(
                    PROJECT_NAME,
                    CLASS_NAME,
                    METHOD_NAME,
                    messageCd,
                    null,
                    e);
        }
    }
}
