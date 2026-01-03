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

    // bm 用
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.bm")
    public DataSourceProperties bmDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    public DataSource bmDataSource() {
        return bmDataSourceProperties().initializeDataSourceBuilder().build();
    }

    @Bean
    public NamedParameterJdbcTemplate bmJdbcTemplate(
            @Qualifier("bmDataSource") DataSource dataSource) {
        return new NamedParameterJdbcTemplate(dataSource);
    }

    // master 用
    @Bean(name = "webMasterDataSourceProperties")
    @ConfigurationProperties("spring.datasource.master")
    public DataSourceProperties masterDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "webMasterDataSource")
    public DataSource webMasterDataSource(
            @Qualifier("webMasterDataSourceProperties") DataSourceProperties props) {
        return props.initializeDataSourceBuilder().build();
    }

    @Bean(name = "webMasterJdbcTemplate")
    public NamedParameterJdbcTemplate webMasterJdbcTemplate(
            @Qualifier("webMasterDataSource") DataSource dataSource) {
        return new NamedParameterJdbcTemplate(dataSource);
    }
}
