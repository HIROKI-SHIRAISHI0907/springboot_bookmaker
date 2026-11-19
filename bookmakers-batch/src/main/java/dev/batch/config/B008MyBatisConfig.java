package dev.batch.config;

import javax.sql.DataSource;

import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.annotation.MapperScans;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;

/**
 * B008（開催中データ登録。2026-11-20 bookmakers-app から移動）の Repository 用 MyBatis 設定
 *
 * <p>bookmakers-app では SqlSessionFactory に mapUnderscoreToCamelCase=true を付けていて、移した Repository の
 *    SELECT はそれを前提にしている（seq_key → seqKey など）。bookmakers-batch の既存の SqlSessionFactory には付いていないので、
 *    既存のバッチの動きを変えないよう、B008 の Repository（dev.batch.b008repository.*）だけ別の SqlSessionFactory で動かす。</p>
 * <p>DataSource は既存と同じ（bmDataSource / masterDataSource）なので、トランザクション（transactionManager / masterTxManager）も同じものに乗る。</p>
 */
@org.springframework.context.annotation.Configuration
@MapperScans({
		@MapperScan(basePackages = "dev.batch.b008repository.bm", sqlSessionTemplateRef = "b008BmSqlSessionTemplate"),
		@MapperScan(basePackages = "dev.batch.b008repository.master", sqlSessionTemplateRef = "b008MasterSqlSessionTemplate")
})
public class B008MyBatisConfig {

	@Bean(name = "b008BmSqlSessionFactory")
	public SqlSessionFactory b008BmSqlSessionFactory(@Qualifier("bmDataSource") DataSource ds) throws Exception {
		return camelCaseFactory(ds);
	}

	@Bean(name = "b008BmSqlSessionTemplate")
	public SqlSessionTemplate b008BmSqlSessionTemplate(@Qualifier("b008BmSqlSessionFactory") SqlSessionFactory f) {
		return new SqlSessionTemplate(f);
	}

	@Bean(name = "b008MasterSqlSessionFactory")
	public SqlSessionFactory b008MasterSqlSessionFactory(@Qualifier("masterDataSource") DataSource ds) throws Exception {
		return camelCaseFactory(ds);
	}

	@Bean(name = "b008MasterSqlSessionTemplate")
	public SqlSessionTemplate b008MasterSqlSessionTemplate(@Qualifier("b008MasterSqlSessionFactory") SqlSessionFactory f) {
		return new SqlSessionTemplate(f);
	}

	private static SqlSessionFactory camelCaseFactory(DataSource ds) throws Exception {
		SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
		factory.setDataSource(ds);
		Configuration config = new Configuration();
		config.setMapUnderscoreToCamelCase(true);
		factory.setConfiguration(config);
		return factory.getObject();
	}
}
