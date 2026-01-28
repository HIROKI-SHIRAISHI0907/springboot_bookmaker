package dev.web.batch;

import java.util.List;

import org.springframework.stereotype.Service;

import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.AssignPublicIp;
import software.amazon.awssdk.services.ecs.model.AwsVpcConfiguration;
import software.amazon.awssdk.services.ecs.model.ContainerOverride;
import software.amazon.awssdk.services.ecs.model.LaunchType;
import software.amazon.awssdk.services.ecs.model.NetworkConfiguration;
import software.amazon.awssdk.services.ecs.model.RunTaskRequest;
import software.amazon.awssdk.services.ecs.model.RunTaskResponse;
import software.amazon.awssdk.services.ecs.model.TaskOverride;

@Service
public class BatchRunnerService {

    // ★あなたの環境に合わせて固定（または application.yml に逃がす）
    private static final String CLUSTER_NAME = "team-member-cluster";

    // ★「bookmakers-batch のタスク定義ファミリー名」か「taskDefinitionArn」
    private static final String TASK_DEFINITION = "bookmakers-batch";

    // ★タスク定義で定義した container 名（重要）
    private static final String CONTAINER_NAME = "bookmakers-batch";

    // ★Fargate の awsvpc 設定（public subnet 前提）
    private static final String SUBNET_ID = "subnet-xxxxxxxx";
    private static final String SECURITY_GROUP_ID = "sg-xxxxxxxx";

    private final EcsClient ecs;

    public BatchRunnerService(EcsClient ecs) {
        this.ecs = ecs;
    }

    /**
     * b006 を起動して taskArn を返す
     */
    public String runB006() {
        // batch コンテナの起動コマンドを上書き（タスク定義の CMD と揃えてね）
        // ここはあなたの batch の起動方式に合わせて変えてOK
        // 例：--bm.job=B006 で実行するように batch.jar 側を実装する
        List<String> command = List.of(
                "java", "-jar", "/app/batch.jar",
                "--bm.job=B006"
        );

        RunTaskResponse res = ecs.runTask(RunTaskRequest.builder()
                .cluster(CLUSTER_NAME)
                .launchType(LaunchType.FARGATE)
                .taskDefinition(TASK_DEFINITION)
                .networkConfiguration(NetworkConfiguration.builder()
                        .awsvpcConfiguration(AwsVpcConfiguration.builder()
                                .subnets(SUBNET_ID)
                                .securityGroups(SECURITY_GROUP_ID)
                                .assignPublicIp(AssignPublicIp.ENABLED)
                                .build())
                        .build())
                .overrides(TaskOverride.builder()
                        .containerOverrides(ContainerOverride.builder()
                                .name(CONTAINER_NAME)
                                .command(command)
                                .build())
                        .build())
                .build());

        if (res.failures() != null && !res.failures().isEmpty()) {
            throw new IllegalStateException("RunTask failed: " + res.failures());
        }
        if (res.tasks() == null || res.tasks().isEmpty()) {
            throw new IllegalStateException("RunTask returned no tasks.");
        }
        return res.tasks().get(0).taskArn();
    }
}
