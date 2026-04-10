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
	private Map<String, JobConfig> job = new HashMap<>();

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
		JobConfig c = job.get(batchCode);
		// defaults で補完
		JobConfig merged = new JobConfig();
		merged.setCluster((c != null &&
 				c.getCluster() != null) ? c.getCluster() : defaults.getCluster());
		merged.setTaskDefinition((c != null &&
				c.getTaskDefinition() != null) ? c.getTaskDefinition() : defaults.getTaskDefinition());
		merged.setContainer((c != null &&
				c.getContainer() != null) ? c.getContainer() : defaults.getContainer());
		merged.setLogGroup((c != null &&
				c.getLogGroup() != null) ? c.getLogGroup() : defaults.getLogGroup());
		return merged;
	}
}
