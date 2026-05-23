package dev.web.api.bm_a009;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class EcsScrapeTaskProgressRecordEntity {

    /** 進捗ID */
    private String progressId;

    /** バッチコード */
    private String batchCd;

    /** ECSタスクID */
    private String taskId;

    /** ECSタスクARN */
    private String taskArn;

    /** ステータス */
    private String status;

    /** メタデータ(JSON文字列) */
    private String metadata;

    /** エラーメッセージ */
    private String errorMessage;

    /** 開始日時 */
    private LocalDateTime startTime;

    /** 終了日時 */
    private LocalDateTime endTime;

}
