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
	@ConfigurationProperties(prefix = "spring.datasource.bm.hikari")
	public HikariDataSource bmDataSource(@Qualifier("bmDataSourceProperties") DataSourceProperties props) {
		return createPool(props, "batch-bm", 6);
	}

	@Bean(name = "transactionManager")   // ← デフォルト名にする
	@org.springframework.context.annotation.Primary
	public DataSourceTransactionManager transactionManager(@Qualifier("bmDataSource") DataSource ds) {
	    return new DataSourceTransactionManager(ds);
	}

	// 既存の bmTxManager を参照している箇所があるなら、互換のため alias も用意
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
