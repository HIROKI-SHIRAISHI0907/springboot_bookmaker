package dev.web.api.bm_a019;

import lombok.Data;

/**
 * PostgreSQL接続統計取得用の内部DTO。
 *
 * <p>
 * RepositoryでSQL結果を受け取るためのDTOであり、
 * Serviceで API レスポンスDTOへ詰め替える前段データとして使用する。
 * </p>
 */
@Data
public class PostgresConnectionStatsDto {

    /**
     * 現在接続中のデータベース名。
     */
    private String currentDatabaseName;

    /**
     * PostgreSQLサーバ全体の最大接続数。
     */
    private int maxConnections;

    /**
     * スーパーユーザー用の予約接続数。
     */
    private int superuserReservedConnections;

    /**
     * 予約接続数。
     *
     * <p>
     * PostgreSQL 16以降の reserved_connections に対応する。
     * </p>
     */
    private Integer reservedConnections;

    /**
     * 現在存在する全バックエンドプロセス数。
     */
    private int totalBackendProcesses;

    /**
     * 現在の client backend 接続数。
     */
    private int currentClientConnections;

    /**
     * active 状態の client backend 接続数。
     */
    private int activeConnections;

    /**
     * idle 状態の client backend 接続数。
     */
    private int idleConnections;

    /**
     * idle in transaction 状態の client backend 接続数。
     */
    private int idleInTransactionConnections;

    /**
     * idle in transaction (aborted) 状態の client backend 接続数。
     */
    private int idleInTransactionAbortedConnections;

    /**
     * 待機イベントを持つ client backend 接続数。
     */
    private int waitingConnections;

    /**
     * 概算の利用可能接続数。
     */
    private int estimatedAvailableConnections;

    /**
     * 現在のデータベースに接続しているバックエンド数。
     *
     * <p>
     * pg_stat_database.numbackends に相当する。
     * </p>
     */
    private int numBackends;

    /**
     * 現在のデータベースに接続している client backend 数。
     */
    private int currentDbConnections;

    /**
     * 現在のデータベースにおける active 状態の接続数。
     */
    private int currentDbActiveConnections;

    /**
     * 現在のデータベースにおける idle 状態の接続数。
     */
    private int currentDbIdleConnections;

    /**
     * 現在のデータベースにおける idle in transaction 状態の接続数。
     */
    private int currentDbIdleInTransactionConnections;

    /**
     * 現在のデータベースにおける idle in transaction (aborted) 状態の接続数。
     */
    private int currentDbIdleInTransactionAbortedConnections;

    /**
     * 現在のデータベースにおける待機中接続数。
     */
    private int currentDbWaitingConnections;

    /**
     * 現在ユーザーの接続数。
     */
    private int currentUserConnections;
}
