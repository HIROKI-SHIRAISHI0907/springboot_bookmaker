package dev.common.server;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * サーバー日付管理
 * @author shiraishitoshio
 *
 */
@Configuration
public class TimeConfig {

	/**
	 * クロックBean
	 * @return
	 */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone(); // サーバーのタイムゾーン・現在時刻
    }
}