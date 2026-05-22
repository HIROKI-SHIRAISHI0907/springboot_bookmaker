package dev.web.config;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 非同期有効化クラス
 * @author shiraishitoshio
 *
 */
@Configuration
@EnableAsync
public class AsyncWebConfig {

	@Bean(name = "executionHistoryExecutor")
    public Executor executionHistoryExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("execution-history-");
        executor.initialize();
        return executor;
    }

}
