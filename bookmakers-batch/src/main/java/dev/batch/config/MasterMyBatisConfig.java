package dev.batch.config;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;

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
		"dev.batch.repository.master"
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
	@ConfigurationProperties(prefix = "spring.datasource.master.hikari")
	public HikariDataSource masterDataSource(
			@Qualifier("masterDataSourceProperties") DataSourceProperties props) {
		// url/username/password/driver-class-name を確実に反映（プールの大きさは createPool で絞る）
		return createPool(props, "batch-master", 3);
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
