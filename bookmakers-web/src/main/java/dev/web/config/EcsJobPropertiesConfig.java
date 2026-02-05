package dev.web.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

/**
 * ECSバッチジョブ設定クラス
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
        private String cluster;
        private String taskDefinition;
        private String container;
        private String logGroup;
    }

    @Data
    public static class JobConfig {
        private String cluster;
        private String taskDefinition;
        private String container;
        private String logGroup;
    }

    public JobConfig require(String batchCode) {
        JobConfig c = jobs.get(batchCode);
        if (c == null) {
            throw new IllegalArgumentException("ECS job config not found for batchCode=" + batchCode);
        }

        // defaults で補完
        JobConfig merged = new JobConfig();
        merged.setCluster(
            c.getCluster() != null ? c.getCluster() : defaults.getCluster()
        );
        merged.setTaskDefinition(
            c.getTaskDefinition() != null ? c.getTaskDefinition() : defaults.getTaskDefinition()
        );
        merged.setContainer(
            c.getContainer() != null ? c.getContainer() : defaults.getContainer()
        );
        merged.setLogGroup(
            c.getLogGroup() != null ? c.getLogGroup() : defaults.getLogGroup()
        );

        return merged;
    }
}
