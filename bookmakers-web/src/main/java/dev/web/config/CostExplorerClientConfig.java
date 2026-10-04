package dev.web.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.costexplorer.CostExplorerClient;

/**
 * Cost Explorer はグローバルサービスで、エンドポイントは us-east-1 のみ。
 * （ECS / Lambda などの ap-northeast-1 とは別に作る）
 * 認証情報は既定のチェーン（ECS タスクロール / ローカルなら ~/.aws）を使う。
 */
@Configuration
public class CostExplorerClientConfig {

	@Bean(destroyMethod = "close")
	public CostExplorerClient costExplorerClient() {
		return CostExplorerClient.builder()
				.region(Region.US_EAST_1)
				.build();
	}
}