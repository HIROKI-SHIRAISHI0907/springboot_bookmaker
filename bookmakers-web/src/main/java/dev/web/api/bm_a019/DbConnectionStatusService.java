package dev.web.api.bm_a019;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

import javax.sql.DataSource;

import org.springframework.stereotype.Service;

import com.zaxxer.hikari.HikariConfigMXBean;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;

import dev.web.repository.bm.DbConnectionStatusRepository;

@Service
public class DbConnectionStatusService {

    private final HikariDataSource hikariDataSource;
    private final DbConnectionStatusRepository dbConnectionStatusRepository;

    public DbConnectionStatusService(
            DataSource dataSource,
            DbConnectionStatusRepository dbConnectionStatusRepository) {
        this.hikariDataSource = resolveHikariDataSource(dataSource);
        this.dbConnectionStatusRepository = dbConnectionStatusRepository;
    }

    public DbConnectionStatusResponse getStatus() {
        HikariPoolMXBean poolMxBean = hikariDataSource.getHikariPoolMXBean();
        HikariConfigMXBean configMxBean = hikariDataSource.getHikariConfigMXBean();

        if (poolMxBean == null) {
            throw new IllegalStateException("HikariPoolMXBean を取得できません。");
        }
        if (configMxBean == null) {
            throw new IllegalStateException("HikariConfigMXBean を取得できません。");
        }

        PostgresConnectionStatsDto pg = dbConnectionStatusRepository.findPostgresConnectionStats();

        DbConnectionStatusResponse response = new DbConnectionStatusResponse();
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
