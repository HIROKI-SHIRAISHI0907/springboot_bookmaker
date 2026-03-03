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
 * MyBatis設定クラス（master専用）
 * - DataSourceProperties 経由で spring.datasource.master.* を確実に反映
 * - MapperScan で masterSqlSessionTemplate を強制使用
 */
@Configuration
@MapperScan(basePackages = {
		"dev.batch.repository.master",
		"dev.batch.repository.bm"
}, sqlSessionTemplateRef = "masterSqlSessionTemplate")
public class MasterMyBatisConfig {

	// =========================================================
	// DataSourceProperties（master）
	// application-*.yml:
	// spring.datasource.master.url / username / password / driver-class-name
	// =========================================================

	@Bean(name = "masterDataSourceProperties")
	@ConfigurationProperties(prefix = "spring.datasource.master")
	public DataSourceProperties masterDataSourceProperties() {
		return new DataSourceProperties();
	}

	// =========================================================
	// DataSource（master）
	// =========================================================

	@Bean(name = "masterDataSource")
	public DataSource masterDataSource(
			@Qualifier("masterDataSourceProperties") DataSourceProperties props) {
		// url/username/password/driver-class-name を確実に反映
		return props.initializeDataSourceBuilder().build();
	}

	// =========================================================
	// TransactionManager（master）
	// =========================================================

	@Bean(name = "masterTxManager")
	public DataSourceTransactionManager masterTxManager(
			@Qualifier("masterDataSource") DataSource ds) {
		return new DataSourceTransactionManager(ds);
	}

	// =========================================================
	// MyBatis: SqlSessionFactory（master）
	// =========================================================

	@Bean(name = "masterSqlSessionFactory")
	public SqlSessionFactory masterSqlSessionFactory(
			@Qualifier("masterDataSource") DataSource ds) throws Exception {
		SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
		factory.setDataSource(ds);
		return factory.getObject();
	}

	// =========================================================
	// MyBatis: SqlSessionTemplate（master）
	// =========================================================

	@Bean(name = "masterSqlSessionTemplate")
	public SqlSessionTemplate masterSqlSessionTemplate(
			@Qualifier("masterSqlSessionFactory") SqlSessionFactory factory) {
		return new SqlSessionTemplate(factory);
	}

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

	@Bean(name = "bmTxManager")
	public DataSourceTransactionManager bmTxManager(
			@Qualifier("bmDataSource") DataSource ds) {
		return new DataSourceTransactionManager(ds);
	}

	@Bean(name = "bmSqlSessionFactory")
	public SqlSessionFactory bmSqlSessionFactory(
			@Qualifier("bmDataSource") DataSource ds) throws Exception {
		SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
		factory.setDataSource(ds);
		return factory.getObject();
	}

	@Bean(name = "bmSqlSessionTemplate")
	public SqlSessionTemplate bmSqlSessionTemplate(
			@Qualifier("bmSqlSessionFactory") SqlSessionFactory factory) {
		return new SqlSessionTemplate(factory);
	}
}
