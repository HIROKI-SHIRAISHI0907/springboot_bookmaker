package dev.web.batch;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import dev.web.config.EcsNetworkPropertiesConfig;
import dev.web.config.EcsScrapePropertiesConfig;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.AssignPublicIp;
import software.amazon.awssdk.services.ecs.model.AwsVpcConfiguration;
import software.amazon.awssdk.services.ecs.model.ContainerOverride;
import software.amazon.awssdk.services.ecs.model.DesiredStatus;
import software.amazon.awssdk.services.ecs.model.KeyValuePair;
import software.amazon.awssdk.services.ecs.model.LaunchType;
import software.amazon.awssdk.services.ecs.model.ListTasksRequest;
import software.amazon.awssdk.services.ecs.model.NetworkConfiguration;
import software.amazon.awssdk.services.ecs.model.RunTaskRequest;
import software.amazon.awssdk.services.ecs.model.RunTaskResponse;
import software.amazon.awssdk.services.ecs.model.Tag;
import software.amazon.awssdk.services.ecs.model.TaskOverride;

/**
 * スクレイピングタスク起動サービス（ECS RunTask / Fargate）
 *
 * - ecs.scraper.{batchCode} の cluster/taskDefinition/container を参照
 * - ecs.network の subnets/securityGroups/assignPublicIp を参照
 *
 * @author shiraishitoshio
 */
@Service
public class EcsScrapeTaskRunner {

    private final EcsClient ecs;
    private final EcsScrapePropertiesConfig props;
    private final EcsNetworkPropertiesConfig net;
    private final EcsScrapeTaskProgressWebService progressService;

    public EcsScrapeTaskRunner(
            EcsClient ecs,
            EcsScrapePropertiesConfig props,
            EcsNetworkPropertiesConfig net,
            EcsScrapeTaskProgressWebService progressService) {
        this.ecs = ecs;
        this.props = props;
        this.net = net;
        this.progressService = progressService;
    }

    /**
     * スクレイピング実行
     *
     * @param batchCode 例: B002
     * @param extraEnv 追加env（任意）
     * @param preventDuplicateRunning true の場合、同familyのRUNNINGがあれば起動しない
     * @return 起動した taskArn
     */
    public String runScrape(String batchCode, Map<String, String> extraEnv, boolean preventDuplicateRunning) {
        String progressId = null;

        try {
            EcsScrapePropertiesConfig.ScrapeConfig cfg = props.require(batchCode);

            Map<String, Object> requestedMetadata = new LinkedHashMap<>();
            requestedMetadata.put("batchCode", batchCode);
            requestedMetadata.put("preventDuplicateRunning", preventDuplicateRunning);
            requestedMetadata.put("extraEnv", extraEnv);
            requestedMetadata.put("cluster", cfg.getCluster());
            requestedMetadata.put("taskDefinition", cfg.getTaskDefinition());
            requestedMetadata.put("container", cfg.getContainer());

            // 開始登録
            progressId = progressService.insertStarted(batchCode, "REQUESTED", requestedMetadata);

            if (preventDuplicateRunning) {
                var running = ecs.listTasks(ListTasksRequest.builder()
                        .cluster(cfg.getCluster())
                        .family(cfg.getTaskDefinition())
                        .desiredStatus(DesiredStatus.RUNNING)
                        .maxResults(1)
                        .build());

                if (running.taskArns() != null && !running.taskArns().isEmpty()) {
                    Map<String, Object> skippedMetadata = new LinkedHashMap<>(requestedMetadata);
                    skippedMetadata.put("runningTaskArns", running.taskArns());

                    progressService.updateFinished(
                            progressId,
                            "SKIPPED",
                            skippedMetadata,
                            "Already RUNNING task exists for " + batchCode);

                    throw new IllegalStateException("Already RUNNING task exists for " + batchCode);
                }
            }

            List<KeyValuePair> envs = new ArrayList<>();
            envs.add(KeyValuePair.builder().name("BM_JOB").value(batchCode).build());

            if (extraEnv != null && !extraEnv.isEmpty()) {
                for (var e : extraEnv.entrySet()) {
                    String k = e.getKey();
                    String v = e.getValue();
                    if (k == null || k.isBlank()) continue;
                    if (v == null || v.isBlank()) continue;
                    if ("BM_JOB".equals(k)) continue;
                    envs.add(KeyValuePair.builder().name(k).value(v).build());
                }
            }

            ContainerOverride containerOverride = ContainerOverride.builder()
                    .name(cfg.getContainer())
                    .environment(envs)
                    .build();

            List<String> subnets = net.getSubnets() == null ? List.of()
                    : net.getSubnets().stream()
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());

            List<String> sgs = net.getSecurityGroups() == null ? List.of()
                    : net.getSecurityGroups().stream()
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());

            if (sgs.isEmpty()) {
                throw new IllegalStateException("ECS securityGroups are empty. Check EcsNetworkPropertiesConfig.");
            }
            if (subnets.isEmpty()) {
                throw new IllegalStateException("ECS subnets are empty. Check EcsNetworkPropertiesConfig.");
            }

            AwsVpcConfiguration vpc = AwsVpcConfiguration.builder()
                    .subnets(subnets)
                    .securityGroups(sgs)
                    .assignPublicIp(net.isAssignPublicIp() ? AssignPublicIp.ENABLED : AssignPublicIp.DISABLED)
                    .build();

            RunTaskRequest req = RunTaskRequest.builder()
                    .cluster(cfg.getCluster())
                    .taskDefinition(cfg.getTaskDefinition())
                    .launchType(LaunchType.FARGATE)
                    .networkConfiguration(NetworkConfiguration.builder().awsvpcConfiguration(vpc).build())
                    .overrides(TaskOverride.builder().containerOverrides(containerOverride).build())
                    .tags(
                            Tag.builder().key("batchCode").value(batchCode).build(),
                            Tag.builder().key("trigger").value("admin-manual").build()
                    )
                    .count(1)
                    .build();

            RunTaskResponse resp = ecs.runTask(req);

            if (resp.failures() != null && !resp.failures().isEmpty()) {
                Map<String, Object> failedMetadata = new LinkedHashMap<>(requestedMetadata);
                failedMetadata.put("failures", resp.failures());

                progressService.updateFinished(
                        progressId,
                        "FAILED",
                        failedMetadata,
                        "RunTask failed: " + resp.failures());

                throw new IllegalStateException("RunTask failed: " + resp.failures());
            }

            if (resp.tasks() == null || resp.tasks().isEmpty()) {
                progressService.updateFinished(
                        progressId,
                        "FAILED",
                        requestedMetadata,
                        "RunTask returned no task.");

                throw new IllegalStateException("RunTask returned no task.");
            }

            String taskArn = resp.tasks().get(0).taskArn();
            String taskId = extractTaskId(taskArn);

            Map<String, Object> runningMetadata = new LinkedHashMap<>(requestedMetadata);
            runningMetadata.put("taskArn", taskArn);
            runningMetadata.put("taskId", taskId);
            runningMetadata.put("launchType", "FARGATE");
            runningMetadata.put("subnets", subnets);
            runningMetadata.put("securityGroups", sgs);

            progressService.updateTaskInfo(
                    progressId,
                    taskId,
                    taskArn,
                    "RUNNING",
                    runningMetadata);

            return taskArn;

        } catch (Exception e) {
            if (progressId != null) {
                progressService.updateFinished(
                        progressId,
                        "FAILED",
                        null,
                        e.getMessage());
            }
            throw e;
        }
    }

    /**
     * taskArn から taskId を抽出する。
     *
     * @param taskArn ECS taskArn
     * @return taskId
     */
    private String extractTaskId(String taskArn) {
        if (taskArn == null || taskArn.isBlank()) {
            return null;
        }
        int idx = taskArn.lastIndexOf('/');
        return idx >= 0 ? taskArn.substring(idx + 1) : taskArn;
    }
}
