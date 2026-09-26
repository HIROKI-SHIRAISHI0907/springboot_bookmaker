package dev.web.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudtrail.CloudTrailClient;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.rds.RdsClient;
import software.amazon.awssdk.services.route53.Route53Client;
import software.amazon.awssdk.services.sts.StsClient;

/**
 * AwsClientBean作成構成クラス
 *
 * ※ S3Client は dev.common.config.S3ClientConfig で定義済みのため、ここでは定義しない。
 *
 * @author shiraishitoshio
 */
@Configuration
@EnableConfigurationProperties(AwsDashboardPropertiesConfig.class)
public class AwsClientConfig {

	private final Region region;

	public AwsClientConfig(AwsDashboardPropertiesConfig props) {
		this.region = Region.of(props.getRegion());
	}

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
	 * CloudWatch Logs
	 * @return
	 */
	@Bean
	public CloudWatchLogsClient cloudWatchLogsClient() {
		return CloudWatchLogsClient.builder()
				.region(Region.of(System.getenv().getOrDefault("AWS_REGION", "ap-northeast-1")))
				.build();
	}

	@Bean(destroyMethod = "close")
	public CloudTrailClient cloudTrailClient() {
		return CloudTrailClient.builder().region(region).build();
	}

	@Bean(destroyMethod = "close")
	public CloudWatchClient cloudWatchClient() {
		return CloudWatchClient.builder().region(region).build();
	}

	@Bean(destroyMethod = "close")
	public RdsClient rdsClient() {
		return RdsClient.builder().region(region).build();
	}

	/** IAM はグローバルサービス */
	@Bean(destroyMethod = "close")
	public IamClient iamClient() {
		return IamClient.builder().region(Region.AWS_GLOBAL).build();
	}

	@Bean(destroyMethod = "close")
	public LambdaClient lambdaClient() {
		return LambdaClient.builder().region(region).build();
	}

	@Bean(destroyMethod = "close")
	public DynamoDbClient dynamoDbClient() {
		return DynamoDbClient.builder().region(region).build();
	}

	@Bean(destroyMethod = "close")
	public Ec2Client ec2Client() {
		return Ec2Client.builder().region(region).build();
	}

	/** Route53 はグローバルサービス */
	@Bean(destroyMethod = "close")
	public Route53Client route53Client() {
		return Route53Client.builder().region(Region.AWS_GLOBAL).build();
	}

	@Bean(destroyMethod = "close")
	public StsClient stsClient() {
		return StsClient.builder().region(region).build();
	}
}