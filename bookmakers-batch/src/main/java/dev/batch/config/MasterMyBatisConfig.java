package dev.batch.config;

import javax.sql.DataSource;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

/**
 * MyBatis設定クラス
 * @author shiraishitoshio
 *
 */
@Configuration
public class MasterMyBatisConfig {

	@Bean(name = "masterSqlSessionFactory")
	public SqlSessionFactory masterSqlSessionFactory(
	        @Qualifier("masterDataSource") DataSource ds) throws Exception {
	    SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
	    factory.setDataSource(ds);
	    return factory.getObject();
	}

    @Bean(name = "masterSqlSessionTemplate")
    public SqlSessionTemplate masterSqlSessionTemplate(
            @Qualifier("masterSqlSessionFactory") SqlSessionFactory factory) {
        return new SqlSessionTemplate(factory);
    }

    @Bean(name = "masterTxManager")
	public DataSourceTransactionManager masterTxManager(
	        @Qualifier("masterDataSource") DataSource ds) {
	    return new DataSourceTransactionManager(ds);
	}

    @Bean(name = "masterDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.master")
    public DataSource masterDataSource() {
        return DataSourceBuilder.create().build();
    }

}
