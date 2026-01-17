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
    // user (PRIMARY)
    // =========================
    @Bean(name = "userDataSourceProperties")
    @Primary
    @ConfigurationProperties(prefix = "spring.datasource.user")
    public DataSourceProperties userDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "userDataSource")
    @Primary
    public DataSource userDataSource(
            @Qualifier("userDataSourceProperties") DataSourceProperties props) {
        return props.initializeDataSourceBuilder().build();
    }

    @Bean(name = "userJdbcTemplate")
    @Primary
    public NamedParameterJdbcTemplate userJdbcTemplate(
            @Qualifier("userDataSource") DataSource ds) {
        return new NamedParameterJdbcTemplate(ds);
    }

    // =========================
    // bm
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
    // master
    // =========================
    @Bean(name = "masterDataSourceProperties")
    @ConfigurationProperties(prefix = "spring.datasource.master")
    public DataSourceProperties masterDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "masterDataSource")
    public DataSource masterDataSource(
            @Qualifier("masterDataSourceProperties") DataSourceProperties props) {
        return props.initializeDataSourceBuilder().build();
    }

    @Bean(name = "masterJdbcTemplate")
    public NamedParameterJdbcTemplate masterJdbcTemplate(
            @Qualifier("masterDataSource") DataSource ds) {
        return new NamedParameterJdbcTemplate(ds);
    }
}
