package dev.common.entity;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 実行履歴テーブルの1レコードを表す内部DTO。
 *
 * <p>
 * API実行・バッチ実行の開始/終了情報、および結果ステータスを保持する。
 * Repository層でDB入出力に使用する。
 * </p>
 */
@Data
public class ExecutionHistoryRecordEntity {

    /**
     * 実行ID。
     *
     * <p>
     * 1実行を一意に識別するUUID文字列。
     * </p>
     */
    private String executionId;

    /**
     * 実行種別。
     *
     * <p>
     * 例: API, BATCH
     * </p>
     */
    private String executionType;

    /**
     * 実行名。
     *
     * <p>
     * API名やバッチ名など、利用者が識別しやすい名称。
     * </p>
     */
    private String executionName;

    /**
     * 実行ステータス。
     *
     * <p>
     * 例: STARTED, SUCCESS, FAILED
     * </p>
     */
    private String status;

    /**
     * 起動トリガー種別。
     *
     * <p>
     * 例: HTTP, SCHEDULE, MANUAL
     * </p>
     */
    private String triggerType;

    /**
     * リクエストパス。
     *
     * <p>
     * API実行時のみ設定する。
     * </p>
     */
    private String requestPath;

    /**
     * HTTPメソッド。
     *
     * <p>
     * API実行時のみ設定する。
     * </p>
     */
    private String httpMethod;

    /**
     * 実行クラス名。
     */
    private String className;

    /**
     * 実行メソッド名。
     */
    private String methodName;

    /**
     * ジョブ名。
     *
     * <p>
     * Spring Batch利用時などに設定する。
     * </p>
     */
    private String jobName;

    /**
     * 実行要求ユーザー。
     *
     * <p>
     * ログインユーザーやシステムユーザー名を格納する。
     * </p>
     */
    private String requestedBy;

    /**
     * トレースID。
     *
     * <p>
     * ログ相関用のID。
     * </p>
     */
    private String traceId;

    /**
     * 実行開始日時。
     */
    private LocalDateTime startTime;

    /**
     * 実行終了日時。
     */
    private LocalDateTime endTime;

    /**
     * 実行時間(ms)。
     */
    private Long durationMs;

    /**
     * エラーメッセージ。
     *
     * <p>
     * 異常終了時のみ設定する。
     * </p>
     */
    private String errorMessage;
}
