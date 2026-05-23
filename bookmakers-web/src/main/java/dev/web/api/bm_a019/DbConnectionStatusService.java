package dev.web.api.bm_a019;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import com.zaxxer.hikari.HikariConfigMXBean;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;

import dev.web.repository.bm.DbConnectionStatusRepository;

@Service
public class DbConnectionStatusService {

    private final HikariDataSource bmDataSource;
    private final HikariDataSource masterDataSource;
    private final HikariDataSource userDataSource;

    private final NamedParameterJdbcTemplate bmJdbcTemplate;
    private final NamedParameterJdbcTemplate masterJdbcTemplate;
    private final NamedParameterJdbcTemplate userJdbcTemplate;

    private final DbConnectionStatusRepository dbConnectionStatusRepository;

    public DbConnectionStatusService(
            @Qualifier("bmDataSource") DataSource bmDataSource,
            @Qualifier("webMasterDataSource") DataSource masterDataSource,
            @Qualifier("webUserDataSource") DataSource userDataSource,
            @Qualifier("bmJdbcTemplate") NamedParameterJdbcTemplate bmJdbcTemplate,
            @Qualifier("webMasterJdbcTemplate") NamedParameterJdbcTemplate masterJdbcTemplate,
            @Qualifier("webUserJdbcTemplate") NamedParameterJdbcTemplate userJdbcTemplate,
            DbConnectionStatusRepository dbConnectionStatusRepository) {

        this.bmDataSource = resolveHikariDataSource(bmDataSource);
        this.masterDataSource = resolveHikariDataSource(masterDataSource);
        this.userDataSource = resolveHikariDataSource(userDataSource);
        this.bmJdbcTemplate = bmJdbcTemplate;
        this.masterJdbcTemplate = masterJdbcTemplate;
        this.userJdbcTemplate = userJdbcTemplate;
        this.dbConnectionStatusRepository = dbConnectionStatusRepository;
    }

    public DbConnectionStatusListResponse getAllStatus() {
        List<DbConnectionStatusResponse> items = List.of(
                buildStatus("bm", "soccer_bm", bmDataSource, bmJdbcTemplate),
                buildStatus("master", "soccer_bm_master", masterDataSource, masterJdbcTemplate),
                buildStatus("user", "soccer_bm_user", userDataSource, userJdbcTemplate)
        );

        DbConnectionStatusListResponse response = new DbConnectionStatusListResponse();
        response.setCount(items.size());
        response.setItems(items);
        return response;
    }

    private DbConnectionStatusResponse buildStatus(
            String dataSourceKey,
            String displayName,
            HikariDataSource hikariDataSource,
            NamedParameterJdbcTemplate jdbcTemplate) {

        initializePoolIfNeeded(hikariDataSource);

        HikariPoolMXBean poolMxBean = hikariDataSource.getHikariPoolMXBean();
        HikariConfigMXBean configMxBean = hikariDataSource.getHikariConfigMXBean();

        if (poolMxBean == null) {
            throw new IllegalStateException("HikariPoolMXBean を取得できません。 dataSourceKey=" + dataSourceKey);
        }
        if (configMxBean == null) {
            throw new IllegalStateException("HikariConfigMXBean を取得できません。 dataSourceKey=" + dataSourceKey);
        }

        PostgresConnectionStatsDto pg = dbConnectionStatusRepository.findPostgresConnectionStats(jdbcTemplate);

        DbConnectionStatusResponse response = new DbConnectionStatusResponse();
        response.setDataSourceKey(dataSourceKey);
        response.setDisplayName(displayName);
        response.setMeasuredAt(OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        response.setDatabaseName(pg.getCurrentDatabaseName());

        DbConnectionStatusResponse.PoolRuntimeMetrics poolRuntime = new DbConnectionStatusResponse.PoolRuntimeMetrics();
        poolRuntime.setPoolName(hikariDataSource.getPoolName());
        poolRuntime.setTotalConnections(poolMxBean.getTotalConnections());
        poolRuntime.setActiveConnections(poolMxBean.getActiveConnections());
        poolRuntime.setIdleConnections(poolMxBean.getIdleConnections());
        poolRuntime.setThreadsAwaitingConnection(poolMxBean.getThreadsAwaitingConnection());
        poolRuntime.setImmediatelyAvailableConnections(poolMxBean.getIdleConnections());
        poolRuntime.setRemainingPoolCapacity(
                Math.max(configMxBean.getMaximumPoolSize() - poolMxBean.getActiveConnections(), 0)
        );
        response.setPoolRuntime(poolRuntime);

        DbConnectionStatusResponse.PoolConfigMetrics poolConfig = new DbConnectionStatusResponse.PoolConfigMetrics();
        poolConfig.setMaximumPoolSize(configMxBean.getMaximumPoolSize());
        poolConfig.setMinimumIdle(configMxBean.getMinimumIdle());
        poolConfig.setConnectionTimeoutMs(configMxBean.getConnectionTimeout());
        poolConfig.setValidationTimeoutMs(configMxBean.getValidationTimeout());
        poolConfig.setIdleTimeoutMs(configMxBean.getIdleTimeout());
        poolConfig.setMaxLifetimeMs(configMxBean.getMaxLifetime());
        poolConfig.setLeakDetectionThresholdMs(configMxBean.getLeakDetectionThreshold());
        poolConfig.setAutoCommit(hikariDataSource.isAutoCommit());
        poolConfig.setConnectionTestQuery(hikariDataSource.getConnectionTestQuery());
        response.setPoolConfig(poolConfig);

        DbConnectionStatusResponse.PostgresServerMetrics postgresServer =
                new DbConnectionStatusResponse.PostgresServerMetrics();
        postgresServer.setMaxConnections(pg.getMaxConnections());
        postgresServer.setSuperuserReservedConnections(pg.getSuperuserReservedConnections());
        postgresServer.setReservedConnections(pg.getReservedConnections());
        postgresServer.setTotalBackendProcesses(pg.getTotalBackendProcesses());
        postgresServer.setCurrentClientConnections(pg.getCurrentClientConnections());
        postgresServer.setActiveConnections(pg.getActiveConnections());
        postgresServer.setIdleConnections(pg.getIdleConnections());
        postgresServer.setIdleInTransactionConnections(pg.getIdleInTransactionConnections());
        postgresServer.setIdleInTransactionAbortedConnections(pg.getIdleInTransactionAbortedConnections());
        postgresServer.setWaitingConnections(pg.getWaitingConnections());
        postgresServer.setEstimatedAvailableConnections(pg.getEstimatedAvailableConnections());
        response.setPostgresServer(postgresServer);

        DbConnectionStatusResponse.CurrentDatabaseMetrics currentDatabase =
                new DbConnectionStatusResponse.CurrentDatabaseMetrics();
        currentDatabase.setNumBackends(pg.getNumBackends());
        currentDatabase.setCurrentClientConnections(pg.getCurrentDbConnections());
        currentDatabase.setActiveConnections(pg.getCurrentDbActiveConnections());
        currentDatabase.setIdleConnections(pg.getCurrentDbIdleConnections());
        currentDatabase.setIdleInTransactionConnections(pg.getCurrentDbIdleInTransactionConnections());
        currentDatabase.setIdleInTransactionAbortedConnections(pg.getCurrentDbIdleInTransactionAbortedConnections());
        currentDatabase.setWaitingConnections(pg.getCurrentDbWaitingConnections());
        currentDatabase.setCurrentUserConnections(pg.getCurrentUserConnections());
        response.setCurrentDatabase(currentDatabase);

        return response;
    }

    private void initializePoolIfNeeded(HikariDataSource hikariDataSource) {
        if (hikariDataSource.getHikariPoolMXBean() != null) {
            return;
        }

        try (Connection ignored = hikariDataSource.getConnection()) {
            // 初回接続でプールを初期化
        } catch (SQLException e) {
            throw new IllegalStateException("Hikariプール初期化用の接続取得に失敗しました。", e);
        }
    }

    private HikariDataSource resolveHikariDataSource(DataSource dataSource) {
        if (dataSource instanceof HikariDataSource) {
            return (HikariDataSource) dataSource;
        }

        try {
            if (dataSource.isWrapperFor(HikariDataSource.class)) {
                return dataSource.unwrap(HikariDataSource.class);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("HikariDataSource の取得に失敗しました。", e);
        }

        throw new IllegalStateException("DataSource が HikariDataSource ではありません。");
    }
}
