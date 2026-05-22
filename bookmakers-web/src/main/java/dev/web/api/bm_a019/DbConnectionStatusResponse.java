package dev.web.api.bm_a019;

import lombok.Data;

/**
 * DBコネクション監視APIのレスポンスDTO。
 *
 * <p>
 * HikariCPのランタイム監視値、HikariCPの設定値、
 * PostgreSQLサーバ全体の接続統計、現在接続中データベース単位の接続統計を返却する。
 * </p>
 */
@Data
public class DbConnectionStatusResponse {

    /**
     * 計測日時。
     *
     * <p>
     * ISO-8601形式の日時文字列を想定する。
     * </p>
     */
    private String measuredAt;

    /**
     * 現在接続中のデータベース名。
     */
    private String databaseName;

    /**
     * HikariCPのランタイム監視値。
     */
    private PoolRuntimeMetrics poolRuntime;

    /**
     * HikariCPの設定値。
     */
    private PoolConfigMetrics poolConfig;

    /**
     * PostgreSQLサーバ全体の接続統計。
     */
    private PostgresServerMetrics postgresServer;

    /**
     * 現在接続中のデータベース単位の接続統計。
     */
    private CurrentDatabaseMetrics currentDatabase;

    /**
     * HikariCPプールのランタイム監視情報DTO。
     */
    @Data
    public static class PoolRuntimeMetrics {

        /**
         * HikariCPのプール名。
         */
        private String poolName;

        /**
         * 現在プール内に存在する総接続数。
         *
         * <p>
         * 使用中・アイドルを含むプール内の総コネクション数。
         * </p>
         */
        private int totalConnections;

        /**
         * 現在使用中の接続数。
         *
         * <p>
         * アプリケーションに貸し出し中のコネクション数。
         * </p>
         */
        private int activeConnections;

        /**
         * 現在アイドル状態の接続数。
         *
         * <p>
         * すぐに払い出し可能なコネクション数。
         * </p>
         */
        private int idleConnections;

        /**
         * 接続取得待ちのスレッド数。
         */
        private int threadsAwaitingConnection;

        /**
         * 即時利用可能な接続数。
         *
         * <p>
         * HikariCPの idleConnections と同義。
         * </p>
         */
        private int immediatelyAvailableConnections;

        /**
         * プール上限までの理論上の残り余力。
         *
         * <p>
         * maximumPoolSize - activeConnections で算出する。
         * </p>
         */
        private int remainingPoolCapacity;
    }

    /**
     * HikariCPプールの設定値DTO。
     */
    @Data
    public static class PoolConfigMetrics {

        /**
         * プール最大接続数。
         */
        private int maximumPoolSize;

        /**
         * 最小アイドル接続数。
         */
        private int minimumIdle;

        /**
         * 接続取得タイムアウト(ms)。
         */
        private long connectionTimeoutMs;

        /**
         * バリデーションタイムアウト(ms)。
         */
        private long validationTimeoutMs;

        /**
         * アイドルタイムアウト(ms)。
         */
        private long idleTimeoutMs;

        /**
         * 接続最大生存時間(ms)。
         */
        private long maxLifetimeMs;

        /**
         * リーク検知閾値(ms)。
         *
         * <p>
         * 0 の場合はリーク検知無効。
         * </p>
         */
        private long leakDetectionThresholdMs;

        /**
         * 自動コミット設定。
         */
        private boolean autoCommit;

        /**
         * 接続テストクエリ。
         *
         * <p>
         * 未設定の場合は null のことがある。
         * </p>
         */
        private String connectionTestQuery;
    }

    /**
     * PostgreSQLサーバ全体の接続統計DTO。
     */
    @Data
    public static class PostgresServerMetrics {

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
         * 未対応環境では 0 または null 相当値になる。
         * </p>
         */
        private Integer reservedConnections;

        /**
         * 現在存在する全バックエンドプロセス数。
         *
         * <p>
         * client backend 以外の内部バックエンドも含む。
         * </p>
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
         *
         * <p>
         * max_connections - superuser_reserved_connections - reserved_connections
         * - currentClientConnections で算出した概算値。
         * </p>
         */
        private int estimatedAvailableConnections;
    }

    /**
     * 現在接続中のデータベース単位の接続統計DTO。
     */
    @Data
    public static class CurrentDatabaseMetrics {

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
        private int currentClientConnections;

        /**
         * 現在のデータベースにおける active 状態の接続数。
         */
        private int activeConnections;

        /**
         * 現在のデータベースにおける idle 状態の接続数。
         */
        private int idleConnections;

        /**
         * 現在のデータベースにおける idle in transaction 状態の接続数。
         */
        private int idleInTransactionConnections;

        /**
         * 現在のデータベースにおける idle in transaction (aborted) 状態の接続数。
         */
        private int idleInTransactionAbortedConnections;

        /**
         * 現在のデータベースにおける待機中接続数。
         */
        private int waitingConnections;

        /**
         * 現在ユーザーの接続数。
         */
        private int currentUserConnections;
    }
}
