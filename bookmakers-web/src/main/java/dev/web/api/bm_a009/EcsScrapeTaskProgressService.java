package dev.web.api.bm_a009;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import dev.web.config.EcsScrapePropertiesConfig;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.GetLogEventsRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.GetLogEventsResponse;
import software.amazon.awssdk.services.cloudwatchlogs.model.OutputLogEvent;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.ContainerDefinition;
import software.amazon.awssdk.services.ecs.model.DescribeTaskDefinitionRequest;
import software.amazon.awssdk.services.ecs.model.DescribeTaskDefinitionResponse;
import software.amazon.awssdk.services.ecs.model.DescribeTasksRequest;
import software.amazon.awssdk.services.ecs.model.DescribeTasksResponse;
import software.amazon.awssdk.services.ecs.model.DesiredStatus;
import software.amazon.awssdk.services.ecs.model.ListTasksRequest;
import software.amazon.awssdk.services.ecs.model.ListTasksResponse;
import software.amazon.awssdk.services.ecs.model.Task;
import software.amazon.awssdk.services.ecs.model.TaskField;

@Service
public class EcsScrapeTaskProgressService {

    /**
     * 例:
     * [PROGRESS] teams=3/20
     * [PROGRESS] teams = ３ / ２０
     */
    private static final Pattern PROGRESS_PATTERN =
            Pattern.compile("\\[PROGRESS\\].*?teams\\s*=\\s*([0-9０-９]+)\\s*/\\s*([0-9０-９]+)");

    private static final String FALLBACK_MESSAGE =
            "進捗率を算出できませんでした。ECSスクレイピング用タスクが止まっている可能性があります。";

    private final EcsClient ecs;
    private final CloudWatchLogsClient logs;
    private final EcsScrapePropertiesConfig props;

    public EcsScrapeTaskProgressService(EcsClient ecs, CloudWatchLogsClient logs, EcsScrapePropertiesConfig props) {
        this.ecs = ecs;
        this.logs = logs;
        this.props = props;
    }

    /**
     * batchCode を B002 形式に寄せる
     * 受け入れ例:
     * - B002
     * - b002
     * - 002
     * - 2
     */
    public String normalizeBatchCode(String batchCode) {
        if (batchCode == null || batchCode.isBlank()) {
            throw new IllegalArgumentException("batchCode is blank");
        }

        String s = batchCode.trim().toUpperCase(Locale.ROOT);

        if (s.matches("^B\\d+$")) {
            int num = Integer.parseInt(s.substring(1));
            return String.format("B%03d", num);
        }

        if (s.matches("^\\d+$")) {
            int num = Integer.parseInt(s);
            return String.format("B%03d", num);
        }

        return s;
    }

    /**
     * 実行中の最新タスク進捗
     */
    public EcsScrapeTaskProgressResponse getLatestProgress(String batchCode) {
        String normalizedBatchCode = normalizeBatchCode(batchCode);
        EcsScrapePropertiesConfig.ScrapeConfig cfg = props.require(normalizedBatchCode);

        String family = extractFamilyName(cfg.getTaskDefinition());

        ListTasksResponse list = ecs.listTasks(ListTasksRequest.builder()
                .cluster(cfg.getCluster())
                .desiredStatus(DesiredStatus.RUNNING)
                .family(family)
                .maxResults(10)
                .build());

        if (list.taskArns() == null || list.taskArns().isEmpty()) {
            EcsScrapeTaskProgressResponse res = new EcsScrapeTaskProgressResponse();
            res.setStatus("NOT_FOUND");
            res.setMessage("実行中のECSタスクが見つかりません。");
            return res;
        }

        String taskArn = getLatestTaskArnByStartedAt(cfg.getCluster(), list.taskArns())
                .orElse(list.taskArns().get(0));

        EcsScrapeTaskProgressResponse res = getProgress(normalizedBatchCode, taskArn);
        if (res.getStatus() == null) {
            res.setStatus("RUNNING");
        }
        return res;
    }

    /**
     * taskArn / taskId 指定で進捗取得
     */
    public EcsScrapeTaskProgressResponse getProgress(String batchCode, String taskIdOrArn) {
        String normalizedBatchCode = normalizeBatchCode(batchCode);
        EcsScrapePropertiesConfig.ScrapeConfig cfg = props.require(normalizedBatchCode);

        EcsScrapeTaskProgressResponse res = new EcsScrapeTaskProgressResponse();
        res.setTaskId(taskIdOrArn);

        DescribeTasksResponse dt = ecs.describeTasks(DescribeTasksRequest.builder()
                .cluster(cfg.getCluster())
                .tasks(taskIdOrArn)
                .include(TaskField.TAGS)
                .build());

        if (dt.tasks() == null || dt.tasks().isEmpty()) {
            res.setStatus("NOT_FOUND");
            res.setMessage(FALLBACK_MESSAGE);
            return res;
        }

        Task task = dt.tasks().get(0);
        res.setStatus(task.lastStatus());

        Integer exitCode = extractExitCode(task);
        if (exitCode != null) {
            res.setExitCd(exitCode);
        }

        if (!"RUNNING".equals(task.lastStatus())) {
            res.setMessage("タスクは RUNNING ではありません: " + task.lastStatus());
            return res;
        }

        String taskArn = task.taskArn();
        String taskId = extractTaskId(taskArn);

        if (taskId == null || taskId.isBlank()) {
            res.setMessage(FALLBACK_MESSAGE);
            return res;
        }

        String streamPrefix = resolveAwslogsStreamPrefix(task.taskDefinitionArn(), cfg.getContainer());
        if (streamPrefix == null || streamPrefix.isBlank()) {
            res.setMessage(FALLBACK_MESSAGE);
            return res;
        }

        String logStreamName = streamPrefix + "/" + cfg.getContainer() + "/" + taskId;

        GetLogEventsResponse gl = logs.getLogEvents(GetLogEventsRequest.builder()
                .logGroupName(cfg.getLogGroup())
                .logStreamName(logStreamName)
                .startFromHead(false)
                .limit(300)
                .build());

        List<OutputLogEvent> events = gl.events();
        if (events == null || events.isEmpty()) {
            res.setMessage(FALLBACK_MESSAGE);
            return res;
        }

        Optional<ProgressHit> hit = findLatestProgress(events);
        if (hit.isEmpty()) {
            res.setMessage(FALLBACK_MESSAGE);
            return res;
        }

        ProgressHit h = hit.get();
        res.setTeamsDone(h.done);
        res.setTeamsTotal(h.total);
        res.setLogLine(h.line);
        res.setLogTime(formatEpochMillisJst(h.timestamp));

        if (h.total <= 0) {
            res.setMessage(FALLBACK_MESSAGE);
            return res;
        }

        double percent = (h.done * 100.0) / h.total;
        res.setStatus("RUNNING");
        res.setPercent(round1(percent));
        return res;
    }

    private Optional<String> getLatestTaskArnByStartedAt(String cluster, List<String> taskArns) {
        if (taskArns == null || taskArns.isEmpty()) {
            return Optional.empty();
        }

        DescribeTasksResponse dt = ecs.describeTasks(DescribeTasksRequest.builder()
                .cluster(cluster)
                .tasks(taskArns)
                .build());

        if (dt.tasks() == null || dt.tasks().isEmpty()) {
            return Optional.empty();
        }

        Task latest = null;
        for (Task t : dt.tasks()) {
            if (t.startedAt() == null) {
                continue;
            }
            if (latest == null || latest.startedAt() == null || t.startedAt().isAfter(latest.startedAt())) {
                latest = t;
            }
        }

        return latest == null ? Optional.empty() : Optional.ofNullable(latest.taskArn());
    }

    private Optional<ProgressHit> findLatestProgress(List<OutputLogEvent> events) {
        for (int i = events.size() - 1; i >= 0; i--) {
            OutputLogEvent e = events.get(i);
            String msg = e.message();
            if (msg == null || msg.isBlank()) {
                continue;
            }

            Matcher m = PROGRESS_PATTERN.matcher(msg);
            if (m.find()) {
                Integer done = safeParseInt(m.group(1));
                Integer total = safeParseInt(m.group(2));

                if (done != null && total != null) {
                    return Optional.of(new ProgressHit(done, total, msg.trim(), e.timestamp()));
                }
            }
        }
        return Optional.empty();
    }

    private String resolveAwslogsStreamPrefix(String taskDefinitionArn, String containerName) {
        DescribeTaskDefinitionResponse td = ecs.describeTaskDefinition(
                DescribeTaskDefinitionRequest.builder()
                        .taskDefinition(taskDefinitionArn)
                        .build()
        );

        if (td.taskDefinition() == null || td.taskDefinition().containerDefinitions() == null) {
            return null;
        }

        for (ContainerDefinition cd : td.taskDefinition().containerDefinitions()) {
            if (!containerName.equals(cd.name())) {
                continue;
            }
            if (cd.logConfiguration() == null || cd.logConfiguration().options() == null) {
                return null;
            }
            return cd.logConfiguration().options().get("awslogs-stream-prefix");
        }
        return null;
    }

    private Integer extractExitCode(Task task) {
        if (task == null || task.containers() == null || task.containers().isEmpty()) {
            return null;
        }
        return task.containers().get(0).exitCode();
    }

    private String extractTaskId(String taskArn) {
        if (taskArn == null || taskArn.isBlank()) {
            return null;
        }
        int idx = taskArn.lastIndexOf('/');
        return idx >= 0 ? taskArn.substring(idx + 1) : taskArn;
    }

    /**
     * taskDefinition から family 名を取り出す
     * 例:
     * - team-member-batch
     * - team-member-batch:12
     * - arn:aws:ecs:ap-northeast-1:xxx:task-definition/team-member-batch:12
     */
    private String extractFamilyName(String taskDefinition) {
        if (taskDefinition == null || taskDefinition.isBlank()) {
            return taskDefinition;
        }

        String s = taskDefinition.trim();

        int slash = s.lastIndexOf('/');
        if (slash >= 0) {
            s = s.substring(slash + 1);
        }

        int colon = s.indexOf(':');
        if (colon >= 0) {
            s = s.substring(0, colon);
        }

        return s;
    }

    private Integer safeParseInt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = toHalfWidthDigits(value).replaceAll("[^0-9\\-]", "");
        if (normalized.isBlank() || "-".equals(normalized)) {
            return null;
        }

        try {
            return Integer.parseInt(normalized);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String toHalfWidthDigits(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c >= '０' && c <= '９') {
                sb.append((char) (c - '０' + '0'));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String formatEpochMillisJst(Long ms) {
        if (ms == null) {
            return null;
        }
        return Instant.ofEpochMilli(ms)
                .atZone(ZoneId.of("Asia/Tokyo"))
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static class ProgressHit {
        final int done;
        final int total;
        final String line;
        final long timestamp;

        ProgressHit(int done, int total, String line, long timestamp) {
            this.done = done;
            this.total = total;
            this.line = line;
            this.timestamp = timestamp;
        }
    }
}
