package dev.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * CSVスレッド管理クラス
 * @author shiraishitoshio
 *
 */
@Configuration
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
