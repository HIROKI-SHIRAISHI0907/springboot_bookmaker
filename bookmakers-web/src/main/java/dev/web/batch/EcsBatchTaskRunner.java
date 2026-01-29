package dev.web.batch;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import dev.web.config.EcsJobPropertiesConfig;
import dev.web.config.EcsNetworkPropertiesConfig;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.AssignPublicIp;
import software.amazon.awssdk.services.ecs.model.AwsVpcConfiguration;
import software.amazon.awssdk.services.ecs.model.ContainerOverride;
import software.amazon.awssdk.services.ecs.model.KeyValuePair;
import software.amazon.awssdk.services.ecs.model.LaunchType;
import software.amazon.awssdk.services.ecs.model.NetworkConfiguration;
import software.amazon.awssdk.services.ecs.model.RunTaskRequest;
import software.amazon.awssdk.services.ecs.model.RunTaskResponse;
import software.amazon.awssdk.services.ecs.model.TaskOverride;

/**
 * バッチランナー起動サービス
 * <p>
 * launchType=FARGATE
 * networkConfiguration（subnets, securityGroups, assignPublicIp）
 * overrides で BATCH_CODE=B006 を環境変数で渡す
 * </p>
 * @author shiraishitoshio
 *
 */
@Service
public class EcsBatchTaskRunner {

    private final EcsClient ecs;
    private final EcsJobPropertiesConfig props;
    private final EcsNetworkPropertiesConfig net; // 下で定義

    public EcsBatchTaskRunner(EcsClient ecs, EcsJobPropertiesConfig props, EcsNetworkPropertiesConfig net) {
        this.ecs = ecs;
        this.props = props;
        this.net = net;
    }

    /**
     * バッチ実行
     * @param batchCode
     * @param extraEnv
     * @return
     */
    public String runBatch(String batchCode, Map<String, String> extraEnv) {
    	// バッチコードに紐づく環境yml情報を取得
    	EcsJobPropertiesConfig.JobConfig cfg = props.require(batchCode);

        // BATCH_CODE は必須
    	List<KeyValuePair> envs = new ArrayList<>();
    	envs.add(KeyValuePair.builder().name("BATCH_CODE").value(batchCode).build());
    	ContainerOverride containerOverride = ContainerOverride.builder()
    		    .name(cfg.getContainer())
    		    .environment(envs)
    		    .build();

    	// VPC設定オブジェクト
        AwsVpcConfiguration vpc = AwsVpcConfiguration.builder()
                .subnets(net.getSubnets())
                .securityGroups(net.getSecurityGroups())
                .assignPublicIp(net.isAssignPublicIp() ? AssignPublicIp.ENABLED : AssignPublicIp.DISABLED)
                .build();

        // yml情報を元にECSをFargate起動
        RunTaskResponse resp = ecs.runTask(RunTaskRequest.builder()
                .cluster(cfg.getCluster())
                .taskDefinition(cfg.getTaskDefinition()) // ARN推奨
                .launchType(LaunchType.FARGATE)
                .networkConfiguration(NetworkConfiguration.builder().awsvpcConfiguration(vpc).build())
                .overrides(TaskOverride.builder().containerOverrides(containerOverride).build())
                .count(1)
                .build());

        if (resp.failures() != null && !resp.failures().isEmpty()) {
            throw new IllegalStateException("RunTask failed: " + resp.failures());
        }
        if (resp.tasks() == null || resp.tasks().isEmpty()) {
            throw new IllegalStateException("RunTask returned no task.");
        }

        return resp.tasks().get(0).taskArn();
    }
}
