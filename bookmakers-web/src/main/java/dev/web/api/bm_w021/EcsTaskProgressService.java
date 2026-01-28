package dev.web.api.bm_w021;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

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
 * ECStask取得サービスクラス
 * @author shiraishitoshio
 *
 */
@Service
public class EcsTaskProgressService {

	// 固定値（あなたの環境）
	private static final String CLUSTER_NAME = "team-member-cluster";
	private static final String TASK_DEF_FAMILY_PREFIX = "team-member-batch"; // 例: "team-member-batch:12"
	private static final String CONTAINER_NAME = "team-member-scraper";
	private static final String LOG_GROUP_NAME = "/ecs/team-member-batch";

	/** 正規表現 */
	private static final Pattern PROGRESS_PATTERN = Pattern.compile("\\[PROGRESS\\].*?teams=(\\d+)/(\\d+)");

	/** エラーメッセージ */
	private static final String FALLBACK_MESSAGE = "進捗率を算出できませんでした。ECSタスクが止まっている可能性があります。";

	private final EcsClient ecs;
	private final CloudWatchLogsClient logs;

	public EcsTaskProgressService(EcsClient ecs, CloudWatchLogsClient logs) {
		this.ecs = ecs;
		this.logs = logs;
	}

	/**
	 * 実行中（RUNNING）の最新タスクの進捗率を取得する（taskId指定不要）。
	 * @return
	 */
	public EcsTaskProgressResponse getLatestProgress() {

	    ListTasksResponse list = ecs.listTasks(ListTasksRequest.builder()
	            .cluster(CLUSTER_NAME)
	            .desiredStatus(DesiredStatus.RUNNING)
	            .family(TASK_DEF_FAMILY_PREFIX)   // "team-member-batch"
	            .maxResults(1)
	            .build());

	    if (list.taskArns() == null || list.taskArns().isEmpty()) {
	        EcsTaskProgressResponse res = new EcsTaskProgressResponse();
	        res.setMessage("実行中のECSタスクが見つかりません。");
	        return res;
	    }

	    // taskArn を getProgress に渡せば、今の実装で taskId を動的抽出してログを追える
	    return getProgress(list.taskArns().get(0));
	}

	/**
	 * ログ取得メソッド
	 * @param taskIdOrArn
	 * @return
	 */
	public EcsTaskProgressResponse getProgress(String taskIdOrArn) {
		EcsTaskProgressResponse res = new EcsTaskProgressResponse();
		res.setTaskId(taskIdOrArn);

		// 1) DescribeTasks でタスクを取得
		DescribeTasksResponse dt = ecs.describeTasks(DescribeTasksRequest.builder()
				.cluster(CLUSTER_NAME)
				.tasks(taskIdOrArn)
				.include(TaskField.TAGS) // 任意
				.build());

		if (dt.tasks() == null || dt.tasks().isEmpty()) {
			res.setMessage(FALLBACK_MESSAGE);
			return res;
		}

		Task task = dt.tasks().get(0);

		// 2) CloudWatch Logs の logStream 名を組み立てる
		// taskArn: arn:aws:ecs:ap-northeast-1:...:task/team-member-cluster/<taskId>
		String taskArn = task.taskArn();
		String taskId = (taskArn != null && taskArn.contains("/"))
		        ? taskArn.substring(taskArn.lastIndexOf('/') + 1)
		        : null;

		if (taskId == null || taskId.isBlank()) {
		    res.setMessage(FALLBACK_MESSAGE);
		    return res;
		}

		// task definition の awslogs-stream-prefix を取得（あなたのログだと "ecs"）
		String streamPrefix = resolveAwslogsStreamPrefix(task.taskDefinitionArn(), CONTAINER_NAME);
		if (streamPrefix == null || streamPrefix.isBlank()) {
		    res.setMessage(FALLBACK_MESSAGE);
		    return res;
		}

		// 例: ecs/team-member-scraper/d9d3c6...
		String logStreamName = streamPrefix + "/" + CONTAINER_NAME + "/" + taskId;


		// 3) CloudWatch Logs から末尾付近のイベントを取得
		GetLogEventsResponse gl = logs.getLogEvents(GetLogEventsRequest.builder()
				.logGroupName(LOG_GROUP_NAME)
				.logStreamName(logStreamName)
				.startFromHead(false)
				.limit(300) // ノイズが多いなら増やす
				.build());

		List<OutputLogEvent> events = gl.events();
		if (events == null || events.isEmpty()) {
			res.setMessage(FALLBACK_MESSAGE);
			return res;
		}

		// 4) 最新から逆走して、最新の teams=X/Y を含む [PROGRESS] 行を探す
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

	/**
	 * 最新の進捗率を取得
	 * @param events
	 * @return
	 */
	private Optional<ProgressHit> findLatestProgress(List<OutputLogEvent> events) {
		for (int i = events.size() - 1; i >= 0; i--) {
			OutputLogEvent e = events.get(i);
			String msg = e.message();
			if (msg == null)
				continue;

			Matcher m = PROGRESS_PATTERN.matcher(msg);
			if (m.find()) {
				int done = Integer.parseInt(m.group(1));
				int total = Integer.parseInt(m.group(2));
				return Optional.of(new ProgressHit(done, total, msg.trim(), e.timestamp()));
			}
		}
		return Optional.empty();
	}

	/**
	 * コンテナ定義から対象コンテナを探す
	 * @param taskDefinitionArn
	 * @param containerName
	 * @return
	 */
	private String resolveAwslogsStreamPrefix(String taskDefinitionArn, String containerName) {
	    DescribeTaskDefinitionResponse td = ecs.describeTaskDefinition(
	            DescribeTaskDefinitionRequest.builder()
	                    .taskDefinition(taskDefinitionArn)
	                    .build()
	    );

	    // コンテナ定義から対象コンテナを探す
	    for (ContainerDefinition cd : td.taskDefinition().containerDefinitions()) {
	        if (!containerName.equals(cd.name())) continue;

	        if (cd.logConfiguration() == null || cd.logConfiguration().options() == null) return null;

	        // awslogs-stream-prefix を取得
	        return cd.logConfiguration().options().get("awslogs-stream-prefix");
	    }
	    return null;
	}

	private static String formatEpochMillisJst(Long ms) {
		if (ms == null)
			return null;
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
