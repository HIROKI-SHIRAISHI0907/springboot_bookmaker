package dev.web.config;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@Configuration
public class DataSourceConfig {

    // =========================
    // bm (Primaryにしたいならここで@Primary)
    // ※今回は user を Primary にします（好みで変えてOK）
    // =========================
    @Bean(name = "bmDataSourceProperties")
    @ConfigurationProperties(prefix = "spring.datasource.bm")
    public DataSourceProperties bmDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "bmDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.bm.hikari")
    public HikariDataSource bmDataSource(
            @Qualifier("bmDataSourceProperties") DataSourceProperties props) {
        return createPool(props, "web-bm", 6);
    }

    @Bean(name = "bmJdbcTemplate")
    public NamedParameterJdbcTemplate bmJdbcTemplate(
            @Qualifier("bmDataSource") DataSource ds) {
        return new NamedParameterJdbcTemplate(ds);
    }

    // =========================
    // master (既存名: webMaster*)
    // =========================
    @Bean(name = "webMasterDataSourceProperties")
    @ConfigurationProperties(prefix = "spring.datasource.master")
    public DataSourceProperties webMasterDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "webMasterDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.master.hikari")
    public HikariDataSource webMasterDataSource(
            @Qualifier("webMasterDataSourceProperties") DataSourceProperties props) {
        return createPool(props, "web-master", 3);
    }

    @Bean(name = "webMasterJdbcTemplate")
    public NamedParameterJdbcTemplate webMasterJdbcTemplate(
            @Qualifier("webMasterDataSource") DataSource ds) {
        return new NamedParameterJdbcTemplate(ds);
    }

    // =========================
    // user (Primary)
    // =========================
    @Bean(name = "webUserDataSourceProperties")
    @Primary
    @ConfigurationProperties(prefix = "spring.datasource.user")
    public DataSourceProperties webUserDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "webUserDataSource")
    @Primary
    @ConfigurationProperties(prefix = "spring.datasource.user.hikari")
    public HikariDataSource webUserDataSource(
            @Qualifier("webUserDataSourceProperties") DataSourceProperties props) {
        return createPool(props, "web-user", 3);
    }

    @Bean(name = "webUserJdbcTemplate")
    @Primary
    public NamedParameterJdbcTemplate webUserJdbcTemplate(
            @Qualifier("webUserDataSource") DataSource ds) {
        return new NamedParameterJdbcTemplate(ds);
    }

    /**
     * 接続プールを作る（2026-10 追加）。
     * <p>以前は Hikari の既定（最大 10・常に 10 本つなぎっぱなし）だったため、アプリ 1 つで DB ごとに 10 本ずつ接続を持ち、
     * web・常駐バッチ・バッチのタスクを合わせると RDS の上限（max_connections=79）に達して
     * 「Failed to obtain JDBC Connection」になった。</p>
     * <ul>
     *   <li>最大 maxPoolSize 本・普段は 1 本だけ持つ（minimumIdle=1）。使わない接続は 1 分で閉じる。</li>
     *   <li>10 分で接続を作り直す（RDS 側で切られた接続を使い続けないように）。</li>
     *   <li>spring.datasource.&lt;名前&gt;.hikari.* （環境変数 SPRING_DATASOURCE_&lt;名前&gt;_HIKARI_MAXIMUM_POOL_SIZE など）で上書きできる。</li>
     * </ul>
     */
    private static HikariDataSource createPool(DataSourceProperties props, String poolName, int maxPoolSize) {
        HikariDataSource ds = props.initializeDataSourceBuilder().type(HikariDataSource.class).build();
        ds.setPoolName(poolName);
        ds.setMaximumPoolSize(maxPoolSize);
        ds.setMinimumIdle(1);
        ds.setIdleTimeout(60_000L);
        ds.setMaxLifetime(600_000L);
        return ds;
    }
}
