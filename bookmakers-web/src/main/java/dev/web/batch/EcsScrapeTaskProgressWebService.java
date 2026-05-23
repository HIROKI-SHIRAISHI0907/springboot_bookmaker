package dev.web.batch;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.web.api.bm_a009.EcsScrapeTaskProgressRecordEntity;
import dev.web.repository.bm.EcsScrapeTaskProgressWebRepository;

@Service
public class EcsScrapeTaskProgressWebService {

    private static final String SYSTEM_ID = "SYSTEM";

    private final EcsScrapeTaskProgressWebRepository repository;
    private final ObjectMapper objectMapper;

    public EcsScrapeTaskProgressWebService(
            EcsScrapeTaskProgressWebRepository repository,
            ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * 開始レコードを登録する。
     *
     * @param batchCd バッチコード
     * @param status ステータス
     * @param metadata メタデータ
     * @return progressId
     */
    public String insertStarted(String batchCd, String status, Map<String, Object> metadata) {
        LocalDateTime now = LocalDateTime.now();
        String progressId = UUID.randomUUID().toString();

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
     * @param taskId タスクID
     * @param taskArn タスクARN
     * @param status ステータス
     * @param metadata メタデータ
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
     * @param progressId 進捗ID
     * @param status ステータス
     * @param metadata メタデータ
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
