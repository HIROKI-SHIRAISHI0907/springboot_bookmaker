package dev.web.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

/**
 * ECSジョブ設定クラス
 * @author shiraishitoshio
 *
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ecs")
public class EcsJobPropertiesConfig {

    private DefaultConfig defaults = new DefaultConfig();
    private Map<String, JobConfig> jobs = new HashMap<>();

    @Data
    public static class DefaultConfig {
        private String region;
    }

    @Data
    public static class JobConfig {
    	/** クラスター */
        private String cluster;
        /** タスク定義 */
        private String taskDefinition;
        /** コンテナ */
        private String container;
        /** ロググループ */
        private String logGroup;
    }

    public JobConfig require(String batchCode) {
        JobConfig c = jobs.get(batchCode);
        if (c == null) {
            throw new IllegalArgumentException("ECS job config not found for batchCode=" + batchCode);
        }
        return c;
    }
}
