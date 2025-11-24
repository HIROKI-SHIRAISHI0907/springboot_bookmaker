package dev.mng.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * CSVスレッド管理クラス
 * @author shiraishitoshio
 *
 */
@Configuration
//★ この DB を使う Mapper のパッケージを指定
//例: StatSizeFinalizeMasterRepository などを dev.mng.domain.repository.user 配下に移動しておく
@MapperScan(basePackages = "dev.mng.domain.repository.user", sqlSessionFactoryRef = "userSqlSessionFactory")
public class CsvQueueConfig {

	// @Configuration
	@Bean
	public ThreadPoolTaskExecutor csvTaskExecutor() {
		ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
		ex.setCorePoolSize(8);
		ex.setMaxPoolSize(8);
		ex.setQueueCapacity(200);
		ex.setThreadNamePrefix("csv-");
		ex.setKeepAliveSeconds(30);
		ex.initialize();
		return ex;
	}

}
