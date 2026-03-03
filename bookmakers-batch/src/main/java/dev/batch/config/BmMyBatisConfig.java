package dev.batch.config;

import javax.sql.DataSource;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

/**
 * MyBatis設定クラス（bm専用）
 * - DataSourceProperties 経由で spring.datasource.bm.* を確実に反映
 * - MapperScan で bmSqlSessionTemplate を強制使用
 */
@Configuration
@MapperScan(basePackages = "dev.batch.repository.bm", sqlSessionTemplateRef = "bmSqlSessionTemplate")
public class BmMyBatisConfig {
	@Bean(name = "bmDataSourceProperties")
	@ConfigurationProperties(prefix = "spring.datasource.bm")
	public DataSourceProperties bmDataSourceProperties() {
		return new DataSourceProperties();
	}

	@Bean(name = "bmDataSource")
	public DataSource bmDataSource(@Qualifier("bmDataSourceProperties") DataSourceProperties props) {
		return props.initializeDataSourceBuilder().build();
	}

	@Bean(name = "bmTxManager")
	public DataSourceTransactionManager bmTxManager(@Qualifier("bmDataSource") DataSource ds) {
		return new DataSourceTransactionManager(ds);
	}

	@Bean(name = "bmSqlSessionFactory")
	public SqlSessionFactory bmSqlSessionFactory(@Qualifier("bmDataSource") DataSource ds) throws Exception {
		SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
		factory.setDataSource(ds);
		return factory.getObject();
	}

	@Bean(name = "bmSqlSessionTemplate")
	public SqlSessionTemplate bmSqlSessionTemplate(@Qualifier("bmSqlSessionFactory") SqlSessionFactory f) {
		return new SqlSessionTemplate(f);
	}
}
