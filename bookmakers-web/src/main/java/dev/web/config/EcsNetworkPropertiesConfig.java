package dev.web.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

@Data
@Configuration
@ConfigurationProperties(prefix = "ecs.network")
public class EcsNetworkPropertiesConfig {

	/** サブネット */
    private List<String> subnets;

    /** セキュリティグループ */
    private List<String> securityGroups;

    /** サブネット割り当てIP */
    private boolean assignPublicIp = true; // あなたは public subnet 運用なので true 推奨

}
