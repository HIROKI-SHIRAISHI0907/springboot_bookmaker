package dev.web.config;

import javax.sql.DataSource;

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
    public DataSource bmDataSource(
            @Qualifier("bmDataSourceProperties") DataSourceProperties props) {
        return props.initializeDataSourceBuilder().build();
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
    public DataSource webMasterDataSource(
            @Qualifier("webMasterDataSourceProperties") DataSourceProperties props) {
        return props.initializeDataSourceBuilder().build();
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
    public DataSource webUserDataSource(
            @Qualifier("webUserDataSourceProperties") DataSourceProperties props) {
        return props.initializeDataSourceBuilder().build();
    }

    @Bean(name = "webUserJdbcTemplate")
    @Primary
    public NamedParameterJdbcTemplate webUserJdbcTemplate(
            @Qualifier("webUserDataSource") DataSource ds) {
        return new NamedParameterJdbcTemplate(ds);
    }
}
