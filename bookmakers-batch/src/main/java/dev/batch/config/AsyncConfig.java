package dev.batch.config;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 非同期設定用Config
 * @author shiraishitoshio
 *
 */
@Configuration
@EnableAsync
public class AsyncConfig {

	/**
     * 実行履歴更新用の非同期Executor。
     *
     * @return Executor
     */
    @Bean(name = "executionHistoryExecutor")
    public Executor executionHistoryExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("execution-history-");
        executor.initialize();
        return executor;
    }

}
