package dev.web.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * AwsClientBean作成構成クラス
 * @author shiraishitoshio
 *
 */
@Configuration
public class AwsClientConfig {

	/**
	 * ECS
	 * @return
	 */
    @Bean
    public EcsClient ecsClient() {
        // ECS(Fargate)ならタスクロール/実行ロールの認証情報が自動で使われる
        // Region は環境変数 AWS_REGION があればそれを使う
        return EcsClient.builder()
                .region(Region.of(System.getenv().getOrDefault("AWS_REGION", "ap-northeast-1")))
                .build();
    }

    /**
     * CloudWatch
     * @return
     */
    @Bean
    public CloudWatchLogsClient cloudWatchLogsClient() {
        return CloudWatchLogsClient.builder()
                .region(Region.of(System.getenv().getOrDefault("AWS_REGION", "ap-northeast-1")))
                .build();
    }

    /**
     * S3
     * @return
     */
    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(Region.of(System.getenv().getOrDefault("AWS_REGION", "ap-northeast-1")))
                .build();
    }
}
