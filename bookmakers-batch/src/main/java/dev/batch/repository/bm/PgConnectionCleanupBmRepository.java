package dev.batch.repository.bm;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PgConnectionCleanupBmRepository {

    @Select("""
            SELECT pid
            FROM pg_stat_activity
            WHERE pid <> pg_backend_pid()
              AND usename = #{userName}
              AND datname = #{dbName}
              AND application_name = #{applicationName}
              AND state = 'idle in transaction'
              AND now() - state_change > (#{olderThanMinutes} || ' minutes')::interval
            """)
    List<Integer> findIdleInTransactionPids(@Param("userName") String userName,
                                            @Param("dbName") String dbName,
                                            @Param("applicationName") String applicationName,
                                            @Param("olderThanMinutes") int olderThanMinutes);

    @Select("""
            SELECT pid
            FROM pg_stat_activity
            WHERE pid <> pg_backend_pid()
              AND usename = #{userName}
              AND datname = #{dbName}
              AND application_name = #{applicationName}
              AND state = 'active'
              AND now() - query_start > (#{olderThanMinutes} || ' minutes')::interval
            """)
    List<Integer> findLongRunningActivePids(@Param("userName") String userName,
                                            @Param("dbName") String dbName,
                                            @Param("applicationName") String applicationName,
                                            @Param("olderThanMinutes") int olderThanMinutes);

    @Select("""
            SELECT pg_terminate_backend(#{pid})
            """)
    boolean terminate(@Param("pid") int pid);
}
