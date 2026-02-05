package dev.web.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

/**
 * ECSスクレイピング設定クラス
 * @author shiraishitoshio
 *
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "scraper")
public class EcsScrapePropertiesConfig {

    private DefaultConfig defaults = new DefaultConfig();
    private Map<String, ScrapeConfig> scrape = new HashMap<>();

    @Data
    public static class DefaultConfig {
        private String region;
    }

    @Data
    public static class ScrapeConfig {
    	/** クラスター */
        private String cluster;
        /** タスク定義 */
        private String taskDefinition;
        /** コンテナ */
        private String container;
        /** ロググループ */
        private String logGroup;
    }

    public ScrapeConfig require(String batchCode) {
    	ScrapeConfig c = scrape.get(batchCode);
        if (c == null) {
            throw new IllegalArgumentException("ECS scraper config not found for batchCode=" + batchCode);
        }
        return c;
    }
}
