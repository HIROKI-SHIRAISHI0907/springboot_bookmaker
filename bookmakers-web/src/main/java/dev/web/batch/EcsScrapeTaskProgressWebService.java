// dev/web/batch/EcsScrapeTaskProgressWebService.java
package dev.web.batch;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.common.util.ProcessKeyUtil;
import dev.web.api.bm_a009.EcsScrapeTaskProgressRecordEntity;
import dev.web.com.OpenProgressRecord;
import dev.web.repository.bm.EcsScrapeTaskProgressWebRepository;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.DescribeTasksRequest;
import software.amazon.awssdk.services.ecs.model.DescribeTasksResponse;
import software.amazon.awssdk.services.ecs.model.Task;

/**
 * EcsScrapeTaskProgressWebServiceクラス
 *
 * ECSスクレイピングタスクの進捗レコード（開始・タスク情報・終了状態）の登録/更新を担当する。
 * 併せて、進捗が「開いた状態→終了状態」へ実際に遷移したタイミングで、
 * スクレイピング処理完了通知メール（成功時のみ）の登録を行う。
 *
 * @author shiraishitoshio
 */
@Slf4j
@Service
public class EcsScrapeTaskProgressWebService {

    private static final String SYSTEM_ID = "SYSTEM";

    private final EcsClient ecs;
    private final EcsScrapeTaskProgressWebRepository repository;
    private final ObjectMapper objectMapper;

    /**
     * コンストラクタ。
     *
     * @param ecs             ECSクライアント
     * @param repository      進捗管理リポジトリ
     * @param objectMapper    メタデータのJSON変換用ObjectMapper
     */
    public EcsScrapeTaskProgressWebService(
            EcsClient ecs,
            EcsScrapeTaskProgressWebRepository repository,
            ObjectMapper objectMapper) {
        this.ecs = ecs;
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * 開始レコードを登録する。
     *
     * <p>DBの時刻カラムは全てUTC運用のため、start_time もUTCで保存する
     * （JSTのウォールクロック値をそのまま入れると、タイムアウト判定SQL等で
     * 実際の経過時間とズレて誤動作するため）。</p>
     *
     * @param batchCd  バッチコード
     * @param status   ステータス
     * @param metadata メタデータ
     * @return progressId
     */
    public String insertStarted(String batchCd, String status, Map<String, Object> metadata) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String progressId = ProcessKeyUtil.getMailSendKey();
        EcsScrapeTaskProgressRecordEntity entity = new EcsScrapeTaskProgressRecordEntity();
        entity.setProgressId(progressId);
        entity.setBatchCd(batchCd);
        entity.setStatus(status);
        entity.setMetadata(toJson(metadata));
        entity.setStartTime(now);
        repository.insertStarted(entity);
        return progressId;
    }

    /**
     * ECSタスク情報を更新する。
     *
     * @param progressId 進捗ID
     * @param taskId     タスクID
     * @param taskArn    タスクARN
     * @param status     ステータス
     * @param metadata   メタデータ
     */
    public void updateTaskInfo(
            String progressId,
            String taskId,
            String taskArn,
            String status,
            Map<String, Object> metadata) {
        repository.updateTaskInfo(
                progressId,
                taskId,
                taskArn,
                status,
                toJson(metadata),
                SYSTEM_ID
        );
    }

    /**
     * 終了状態を更新する。
     *
     * @param progressId   進捗ID
     * @param status       ステータス
     * @param metadata     メタデータ
     * @param errorMessage エラーメッセージ
     */
    public void updateFinished(
            String progressId,
            String status,
            Map<String, Object> metadata,
            String errorMessage) {
        repository.updateFinished(
                progressId,
                status,
                toJson(metadata),
                errorMessage,
                SYSTEM_ID
        );
    }

    /**
     * 同一バッチコードで taskArn 未設定の未完了レコードがあれば、
     * 補完的に正常終了へ更新する。
     *
     * @param batchCd バッチコード
     * @return 更新した場合 true
     */
    public boolean completeOpenRecordWithoutTaskArn(String batchCd) {
        String progressId = repository.findLatestOpenProgressIdWithoutTaskArn(batchCd);
        if (progressId == null || progressId.isBlank()) {
            return false;
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("completedBy", "getLatestProgress");
        metadata.put("reason", "no running ECS task and taskArn was not assigned");
        metadata.put("batchCd", batchCd);
        repository.updateFinished(
                progressId,
                "SUCCESS",
                toJson(metadata),
                null,
                SYSTEM_ID
        );
        return true;
    }

    /**
     * 指定バッチコードの最新の未完了進捗レコードを確認し、必要であれば終了状態へ更新する。
     *
     * taskArnが未設定の場合や、ECS側にタスクが見つからない場合は正常終了として補完する。
     * taskArnが設定済みでタスクが取得できた場合は、ECS上のタスクがSTOPPEDになるまでは
     * 何もせずfalseを返す（呼び出し元のポーリングで再確認する想定）。
     * STOPPEDであれば、コンテナのexitCodeから成功/失敗を判定して終了状態を更新する。
     *
     * 進捗レコードを実際に「開いた状態→終了状態」へ更新できたときのみtrueを返すため、
     * この戻り値をスクレイピング処理完了通知メールの多重送信防止に利用できる。
     *
     * @param batchCd バッチコード
     * @param cluster ECSクラスタ名
     * @return 進捗レコードを終了状態へ更新した場合 true。更新対象がない、またはまだ実行中の場合は false
     */
    public boolean completeLatestOpenRecord(String batchCd, String cluster) {
        OpenProgressRecord record = repository.findLatestOpenRecord(batchCd);
        if (record == null) {
            return false;
        }
        // taskArn が無い場合は既存補完ロジック
        if (record.getTaskArn() == null || record.getTaskArn().isBlank()) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("completedBy", "completeLatestOpenRecord");
            metadata.put("reason", "taskArn was not assigned");
            metadata.put("batchCd", batchCd);
            repository.updateFinished(
                    record.getProgressId(),
                    "SUCCESS",
                    toJson(metadata),
                    null,
                    SYSTEM_ID
            );
            return true;
        }
        DescribeTasksResponse dt = ecs.describeTasks(DescribeTasksRequest.builder()
                .cluster(cluster)
                .tasks(record.getTaskArn())
                .build());
        if (dt.tasks() == null || dt.tasks().isEmpty()) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("completedBy", "completeLatestOpenRecord");
            metadata.put("reason", "task not found in ECS");
            metadata.put("batchCd", batchCd);
            metadata.put("taskArn", record.getTaskArn());
            repository.updateFinished(
                    record.getProgressId(),
                    "SUCCESS",
                    toJson(metadata),
                    null,
                    SYSTEM_ID
            );
            return true;
        }
        Task task = dt.tasks().get(0);
        if (!"STOPPED".equals(task.lastStatus())) {
            return false;
        }
        Integer exitCode = null;
        if (task.containers() != null && !task.containers().isEmpty()) {
            exitCode = task.containers().get(0).exitCode();
        }
        String finalStatus = (exitCode != null && exitCode == 0) ? "SUCCESS" : "FAILED";
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("completedBy", "completeLatestOpenRecord");
        metadata.put("batchCd", batchCd);
        metadata.put("taskArn", record.getTaskArn());
        metadata.put("lastStatus", task.lastStatus());
        metadata.put("stopCode", task.stopCodeAsString());
        metadata.put("stoppedReason", task.stoppedReason());
        metadata.put("exitCode", exitCode);
        String errorMessage = "FAILED".equals(finalStatus)
                ? "ECS task stopped abnormally. stoppedReason=" + task.stoppedReason() + ", exitCode=" + exitCode
                : null;
        repository.updateFinished(
                record.getProgressId(),
                finalStatus,
                toJson(metadata),
                errorMessage,
                SYSTEM_ID
        );
        return true;
    }

    /**
     * メタデータをJSON文字列へ変換する。
     *
     * @param metadata メタデータ
     * @return JSON文字列
     */
    private String toJson(Map<String, Object> metadata) {
        if (metadata == null) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("metadata の JSON 変換に失敗しました。", e);
        }
    }
}