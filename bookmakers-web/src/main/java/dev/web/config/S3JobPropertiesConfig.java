package dev.web.config;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * S3データ設定クラス
 * @author shiraishitoshio
 *
 */
@Component
@ConfigurationProperties(prefix = "s3-job")
public class S3JobPropertiesConfig {

    private Map<String, JobConfig> jobs;

    public JobConfig require(String code) {
        JobConfig cfg = jobs == null ? null : jobs.get(code);
        if (cfg == null) throw new IllegalArgumentException("Unknown batchCode: " + code);
        return cfg;
    }

    public Map<String, JobConfig> getJobs() { return jobs; }
    public void setJobs(Map<String, JobConfig> jobs) { this.jobs = jobs; }

    @Data
    public static class JobConfig {
        private String bucket;
        private String prefix;      // 例: "output/B002/"
        private boolean recursive = true; // falseなら直下のみ
    }
}
