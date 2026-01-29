package dev.web.api.bm_w021;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import dev.web.config.EcsJobPropertiesConfig;
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

/**
 * ECSタスク進捗取得サービス
 * - batchCode(B002など)でクラスター/タスク定義/コンテナ/ロググループを切り替える
 */
@Service
public class EcsTaskProgressService {

    /** 正規表現 */
    private static final Pattern PROGRESS_PATTERN =
            Pattern.compile("\\[PROGRESS\\].*?teams=(\\d+)/(\\d+)");

    /** エラーメッセージ */
    private static final String FALLBACK_MESSAGE =
            "進捗率を算出できませんでした。ECSタスクが止まっている可能性があります。";

    /** 設定関連 */
    private final EcsClient ecs;
    private final CloudWatchLogsClient logs;
    private final EcsJobPropertiesConfig props;

    public EcsTaskProgressService(EcsClient ecs, CloudWatchLogsClient logs, EcsJobPropertiesConfig props) {
        this.ecs = ecs;
        this.logs = logs;
        this.props = props;
    }

    /**
     * 実行中（RUNNING）の最新タスクの進捗率を取得する（taskId指定不要）
     * @param batchCode 例: B002
     */
    public EcsTaskProgressResponse getLatestProgress(String batchCode) {
    	EcsJobPropertiesConfig.JobConfig cfg = props.require(batchCode);

        ListTasksResponse list = ecs.listTasks(ListTasksRequest.builder()
                .cluster(cfg.getCluster())
                .desiredStatus(DesiredStatus.RUNNING)
                // family には「タスク定義のファミリー名」を入れる（例: team-member-batch）
                .family(cfg.getTaskDefinition())
                // 注意：ListTasks は新しい順とは限らないので、必要なら maxResults を増やしてDescribeで開始時刻比較
                .maxResults(10)
                .build());

        if (list.taskArns() == null || list.taskArns().isEmpty()) {
            EcsTaskProgressResponse res = new EcsTaskProgressResponse();
            res.setMessage("実行中のECSタスクが見つかりません。");
            return res;
        }

        // 一旦先頭を使う（確実に「最新」にしたいなら下の getLatestTaskArnByStartedAt を使う）
        String taskArn = getLatestTaskArnByStartedAt(cfg.getCluster(), list.taskArns()).orElse(list.taskArns().get(0));
        return getProgress(batchCode, taskArn);
    }

    /**
     * taskArn / taskId 指定で進捗取得
     * @param batchCode 例: B002
     * @param taskIdOrArn taskArn または taskId
     */
    public EcsTaskProgressResponse getProgress(String batchCode, String taskIdOrArn) {
    	EcsJobPropertiesConfig.JobConfig cfg = props.require(batchCode);

        EcsTaskProgressResponse res = new EcsTaskProgressResponse();
        res.setTaskId(taskIdOrArn);

        // 1) DescribeTasks
        DescribeTasksResponse dt = ecs.describeTasks(DescribeTasksRequest.builder()
                .cluster(cfg.getCluster())
                .tasks(taskIdOrArn)
                .include(TaskField.TAGS)
                .build());

        if (dt.tasks() == null || dt.tasks().isEmpty()) {
            res.setMessage(FALLBACK_MESSAGE);
            return res;
        }
        Task task = dt.tasks().get(0);

        // 2) taskId 抽出
        String taskArn = task.taskArn();
        String taskId = (taskArn != null && taskArn.contains("/"))
                ? taskArn.substring(taskArn.lastIndexOf('/') + 1)
                : null;

        if (taskId == null || taskId.isBlank()) {
            res.setMessage(FALLBACK_MESSAGE);
            return res;
        }

        // 3) awslogs-stream-prefix 取得
        String streamPrefix = resolveAwslogsStreamPrefix(task.taskDefinitionArn(), cfg.getContainer());
        if (streamPrefix == null || streamPrefix.isBlank()) {
            res.setMessage(FALLBACK_MESSAGE);
            return res;
        }

        // 例: ecs/team-member-scraper/<taskId>
        String logStreamName = streamPrefix + "/" + cfg.getContainer() + "/" + taskId;

        // 4) CloudWatch Logs
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

        // 5) 最新の [PROGRESS] を探す
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
        res.setPercent(round1(percent));
        return res;
    }

    /** taskArns の中から startedAt が最大のものを返す（より「最新」っぽい判定） */
    private Optional<String> getLatestTaskArnByStartedAt(String cluster, List<String> taskArns) {
        if (taskArns == null || taskArns.isEmpty()) return Optional.empty();

        DescribeTasksResponse dt = ecs.describeTasks(DescribeTasksRequest.builder()
                .cluster(cluster)
                .tasks(taskArns)
                .build());

        if (dt.tasks() == null || dt.tasks().isEmpty()) return Optional.empty();

        Task latest = null;
        for (Task t : dt.tasks()) {
            if (t.startedAt() == null) continue;
            if (latest == null || (latest.startedAt() != null && t.startedAt().isAfter(latest.startedAt()))) {
                latest = t;
            }
        }
        return latest == null ? Optional.empty() : Optional.ofNullable(latest.taskArn());
    }

    /** 最新の進捗ログ行を探す */
    private Optional<ProgressHit> findLatestProgress(List<OutputLogEvent> events) {
        for (int i = events.size() - 1; i >= 0; i--) {
            OutputLogEvent e = events.get(i);
            String msg = e.message();
            if (msg == null) continue;

            Matcher m = PROGRESS_PATTERN.matcher(msg);
            if (m.find()) {
                int done = Integer.parseInt(m.group(1));
                int total = Integer.parseInt(m.group(2));
                return Optional.of(new ProgressHit(done, total, msg.trim(), e.timestamp()));
            }
        }
        return Optional.empty();
    }

    /** task definition のコンテナ定義から awslogs-stream-prefix を取得 */
    private String resolveAwslogsStreamPrefix(String taskDefinitionArn, String containerName) {
        DescribeTaskDefinitionResponse td = ecs.describeTaskDefinition(
                DescribeTaskDefinitionRequest.builder()
                        .taskDefinition(taskDefinitionArn)
                        .build()
        );

        for (ContainerDefinition cd : td.taskDefinition().containerDefinitions()) {
            if (!containerName.equals(cd.name())) continue;
            if (cd.logConfiguration() == null || cd.logConfiguration().options() == null) return null;
            return cd.logConfiguration().options().get("awslogs-stream-prefix");
        }
        return null;
    }

    private static String formatEpochMillisJst(Long ms) {
        if (ms == null) return null;
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
