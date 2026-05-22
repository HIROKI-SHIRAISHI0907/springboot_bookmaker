package dev.web.repository.bm;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.web.api.bm_a019.PostgresConnectionStatsDto;

@Repository
public class DbConnectionStatusRepository {

    private final NamedParameterJdbcTemplate bmJdbcTemplate;

    public DbConnectionStatusRepository(NamedParameterJdbcTemplate bmJdbcTemplate) {
        this.bmJdbcTemplate = bmJdbcTemplate;
    }

    public PostgresConnectionStatsDto findPostgresConnectionStats() {
        String sql = """
            WITH settings AS (
                SELECT
                    current_setting('max_connections')::int AS max_connections,
                    current_setting('superuser_reserved_connections')::int AS superuser_reserved_connections,
                    COALESCE(current_setting('reserved_connections', true), '0')::int AS reserved_connections,
                    current_database() AS current_database_name,
                    current_user AS current_user_name
            ),
            activity AS (
                SELECT
                    backend_type,
                    datname,
                    usename,
                    state,
                    wait_event_type
                FROM pg_stat_activity
            ),
            server_stats AS (
                SELECT
                    COUNT(*) AS total_backend_processes,
                    COUNT(*) FILTER (WHERE backend_type = 'client backend') AS current_client_connections,
                    COUNT(*) FILTER (WHERE backend_type = 'client backend' AND state = 'active') AS active_connections,
                    COUNT(*) FILTER (WHERE backend_type = 'client backend' AND state = 'idle') AS idle_connections,
                    COUNT(*) FILTER (WHERE backend_type = 'client backend' AND state = 'idle in transaction') AS idle_in_transaction_connections,
                    COUNT(*) FILTER (WHERE backend_type = 'client backend' AND state = 'idle in transaction (aborted)') AS idle_in_transaction_aborted_connections,
                    COUNT(*) FILTER (WHERE backend_type = 'client backend' AND wait_event_type IS NOT NULL) AS waiting_connections
                FROM activity
            ),
            db_stats AS (
                SELECT
                    COUNT(*) FILTER (WHERE backend_type = 'client backend' AND datname = current_database()) AS current_db_connections,
                    COUNT(*) FILTER (WHERE backend_type = 'client backend' AND datname = current_database() AND state = 'active') AS current_db_active_connections,
                    COUNT(*) FILTER (WHERE backend_type = 'client backend' AND datname = current_database() AND state = 'idle') AS current_db_idle_connections,
                    COUNT(*) FILTER (WHERE backend_type = 'client backend' AND datname = current_database() AND state = 'idle in transaction') AS current_db_idle_in_transaction_connections,
                    COUNT(*) FILTER (WHERE backend_type = 'client backend' AND datname = current_database() AND state = 'idle in transaction (aborted)') AS current_db_idle_in_transaction_aborted_connections,
                    COUNT(*) FILTER (WHERE backend_type = 'client backend' AND datname = current_database() AND wait_event_type IS NOT NULL) AS current_db_waiting_connections,
                    COUNT(*) FILTER (WHERE backend_type = 'client backend' AND usename = current_user) AS current_user_connections
                FROM activity
            ),
            pgdb AS (
                SELECT
                    COALESCE(MAX(numbackends) FILTER (WHERE datname = current_database()), 0) AS num_backends
                FROM pg_stat_database
            )
            SELECT
                s.current_database_name AS currentDatabaseName,
                s.max_connections AS maxConnections,
                s.superuser_reserved_connections AS superuserReservedConnections,
                s.reserved_connections AS reservedConnections,

                ss.total_backend_processes AS totalBackendProcesses,
                ss.current_client_connections AS currentClientConnections,
                ss.active_connections AS activeConnections,
                ss.idle_connections AS idleConnections,
                ss.idle_in_transaction_connections AS idleInTransactionConnections,
                ss.idle_in_transaction_aborted_connections AS idleInTransactionAbortedConnections,
                ss.waiting_connections AS waitingConnections,

                GREATEST(
                    s.max_connections
                    - s.superuser_reserved_connections
                    - s.reserved_connections
                    - ss.current_client_connections,
                    0
                ) AS estimatedAvailableConnections,

                pgdb.num_backends AS numBackends,
                ds.current_db_connections AS currentDbConnections,
                ds.current_db_active_connections AS currentDbActiveConnections,
                ds.current_db_idle_connections AS currentDbIdleConnections,
                ds.current_db_idle_in_transaction_connections AS currentDbIdleInTransactionConnections,
                ds.current_db_idle_in_transaction_aborted_connections AS currentDbIdleInTransactionAbortedConnections,
                ds.current_db_waiting_connections AS currentDbWaitingConnections,
                ds.current_user_connections AS currentUserConnections
            FROM settings s
            CROSS JOIN server_stats ss
            CROSS JOIN db_stats ds
            CROSS JOIN pgdb
        """;

        return bmJdbcTemplate.queryForObject(
            sql,
            new MapSqlParameterSource(),
            (rs, rowNum) -> {
                PostgresConnectionStatsDto dto = new PostgresConnectionStatsDto();
                dto.setCurrentDatabaseName(rs.getString("currentDatabaseName"));
                dto.setMaxConnections(rs.getInt("maxConnections"));
                dto.setSuperuserReservedConnections(rs.getInt("superuserReservedConnections"));
                dto.setReservedConnections((Integer) rs.getObject("reservedConnections"));
                dto.setTotalBackendProcesses(rs.getInt("totalBackendProcesses"));
                dto.setCurrentClientConnections(rs.getInt("currentClientConnections"));
                dto.setActiveConnections(rs.getInt("activeConnections"));
                dto.setIdleConnections(rs.getInt("idleConnections"));
                dto.setIdleInTransactionConnections(rs.getInt("idleInTransactionConnections"));
                dto.setIdleInTransactionAbortedConnections(rs.getInt("idleInTransactionAbortedConnections"));
                dto.setWaitingConnections(rs.getInt("waitingConnections"));
                dto.setEstimatedAvailableConnections(rs.getInt("estimatedAvailableConnections"));
                dto.setNumBackends(rs.getInt("numBackends"));
                dto.setCurrentDbConnections(rs.getInt("currentDbConnections"));
                dto.setCurrentDbActiveConnections(rs.getInt("currentDbActiveConnections"));
                dto.setCurrentDbIdleConnections(rs.getInt("currentDbIdleConnections"));
                dto.setCurrentDbIdleInTransactionConnections(rs.getInt("currentDbIdleInTransactionConnections"));
                dto.setCurrentDbIdleInTransactionAbortedConnections(rs.getInt("currentDbIdleInTransactionAbortedConnections"));
                dto.setCurrentDbWaitingConnections(rs.getInt("currentDbWaitingConnections"));
                dto.setCurrentUserConnections(rs.getInt("currentUserConnections"));
                return dto;
            }
        );
    }
}
