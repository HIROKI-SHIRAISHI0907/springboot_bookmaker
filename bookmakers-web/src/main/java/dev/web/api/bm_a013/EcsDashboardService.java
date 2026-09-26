package dev.web.api.bm_a013;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.web.api.bm_a013.DashboardDtos.EcsCluster;
import dev.web.api.bm_a013.DashboardDtos.EcsRun;
import dev.web.api.bm_a013.DashboardDtos.EcsSummary;
import dev.web.api.bm_a013.DashboardDtos.EcsTaskDefCount;
import software.amazon.awssdk.services.cloudtrail.CloudTrailClient;
import software.amazon.awssdk.services.cloudtrail.model.Event;
import software.amazon.awssdk.services.cloudtrail.model.LookupAttribute;
import software.amazon.awssdk.services.cloudtrail.model.LookupAttributeKey;
import software.amazon.awssdk.services.cloudtrail.model.LookupEventsRequest;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.Cluster;
import software.amazon.awssdk.services.ecs.model.DescribeClustersRequest;

/**
 * ECS の「その日の実行回数」を集計する。
 *
 * ECS API は停止済みタスクを約1時間しか保持しないため、
 * 実行履歴は CloudTrail の RunTask イベント（90日保持）から数える。
 * ※ CloudTrail への反映は最大 15 分程度遅れる。
 */
@Service
public class EcsDashboardService {

	private final EcsClient ecs;
	private final CloudTrailClient cloudTrail;
	private final ObjectMapper mapper = new ObjectMapper();

	public EcsDashboardService(EcsClient ecs, CloudTrailClient cloudTrail) {
		this.ecs = ecs;
		this.cloudTrail = cloudTrail;
	}

	/** イベント時刻（集計用）と画面表示用 DTO の組 */
	private static final class TimedRun {
		private final Instant time;
		private final EcsRun run;

		private TimedRun(Instant time, EcsRun run) {
			this.time = time;
			this.run = run;
		}
	}

	public EcsSummary summary(DateRange range) {
		List<TimedRun> timed = fetchRunTaskEvents(range);

		List<EcsRun> runs = new ArrayList<EcsRun>();
		int[] hourly = new int[24];
		Map<String, int[]> byDef = new LinkedHashMap<String, int[]>(); // [runs, launched, failedRuns]
		int launched = 0;
		int failedRuns = 0;

		for (TimedRun tr : timed) {
			EcsRun r = tr.run;
			runs.add(r);
			hourly[range.hourOf(tr.time)]++;

			boolean failed = r.getErrorCode() != null || (r.getLaunchedTasks() == 0 && r.getFailures() > 0);
			launched += r.getLaunchedTasks();
			if (failed) {
				failedRuns++;
			}

			int[] c = byDef.get(r.getTaskDefinition());
			if (c == null) {
				c = new int[3];
				byDef.put(r.getTaskDefinition(), c);
			}
			c[0]++;
			c[1] += r.getLaunchedTasks();
			if (failed) {
				c[2]++;
			}
		}

		List<EcsTaskDefCount> defs = new ArrayList<EcsTaskDefCount>();
		for (Map.Entry<String, int[]> e : byDef.entrySet()) {
			int[] v = e.getValue();
			defs.add(new EcsTaskDefCount(e.getKey(), v[0], v[1], v[2]));
		}
		Collections.sort(defs, new Comparator<EcsTaskDefCount>() {
			@Override
			public int compare(EcsTaskDefCount a, EcsTaskDefCount b) {
				return Integer.compare(b.getRuns(), a.getRuns());
			}
		});

		return new EcsSummary(range.getDate().toString(), runs.size(), launched, failedRuns,
				hourly, defs, runs, fetchClusters());
	}

	private List<TimedRun> fetchRunTaskEvents(DateRange range) {
		LookupEventsRequest req = LookupEventsRequest.builder()
				.lookupAttributes(LookupAttribute.builder()
						.attributeKey(LookupAttributeKey.EVENT_NAME)
						.attributeValue("RunTask")
						.build())
				.startTime(range.getStart())
				.endTime(range.endOrNow())
				.maxResults(50)
				.build();

		List<TimedRun> out = new ArrayList<TimedRun>();
		for (Event ev : cloudTrail.lookupEventsPaginator(req).events()) {
			if (!"ecs.amazonaws.com".equals(ev.eventSource())) {
				continue;
			}
			out.add(new TimedRun(ev.eventTime(), toRun(ev, range)));
		}
		Collections.sort(out, new Comparator<TimedRun>() {
			@Override
			public int compare(TimedRun a, TimedRun b) {
				return a.time.compareTo(b.time);
			}
		});
		return out;
	}

	private EcsRun toRun(Event ev, DateRange range) {
		String cluster = "";
		String taskDef = "(unknown)";
		String startedBy = null;
		String invokedBy = null;
		String errorCode = null;
		int launched = 0;
		int failures = 0;

		try {
			JsonNode root = mapper.readTree(ev.cloudTrailEvent());
			JsonNode req = root.path("requestParameters");
			cluster = shortName(req.path("cluster").asText("default"));
			taskDef = taskFamily(req.path("taskDefinition").asText(""));
			startedBy = textOrNull(req.path("startedBy"));
			invokedBy = textOrNull(root.path("userIdentity").path("invokedBy"));
			if (invokedBy == null) {
				invokedBy = textOrNull(root.path("userIdentity").path("arn"));
			}
			errorCode = textOrNull(root.path("errorCode"));
			JsonNode res = root.path("responseElements");
			launched = res.path("tasks").isArray() ? res.path("tasks").size() : 0;
			failures = res.path("failures").isArray() ? res.path("failures").size() : 0;
		} catch (Exception ignore) {
			// 解析できないイベントは件数だけ数える
		}

		return new EcsRun(range.format(ev.eventTime()), cluster, taskDef, startedBy, invokedBy,
				launched, failures, errorCode);
	}

	private List<EcsCluster> fetchClusters() {
		List<String> arns = new ArrayList<String>();
		for (String arn : ecs.listClustersPaginator().clusterArns()) {
			arns.add(arn);
		}

		List<EcsCluster> out = new ArrayList<EcsCluster>();
		for (int i = 0; i < arns.size(); i += 100) {
			List<String> chunk = arns.subList(i, Math.min(i + 100, arns.size()));
			for (Cluster c : ecs.describeClusters(DescribeClustersRequest.builder().clusters(chunk).build())
					.clusters()) {
				out.add(new EcsCluster(c.clusterName(), c.status(),
						nz(c.runningTasksCount()), nz(c.pendingTasksCount()), nz(c.activeServicesCount())));
			}
		}
		Collections.sort(out, new Comparator<EcsCluster>() {
			@Override
			public int compare(EcsCluster a, EcsCluster b) {
				return a.getName().compareTo(b.getName());
			}
		});
		return out;
	}

	/** arn:aws:ecs:...:task-definition/family:3 → family */
	static String taskFamily(String td) {
		if (isBlank(td)) {
			return "(unknown)";
		}
		String key = "task-definition/";
		String s = td.contains(key) ? td.substring(td.indexOf(key) + key.length()) : td;
		int colon = s.lastIndexOf(':');
		return colon > 0 ? s.substring(0, colon) : s;
	}

	/** arn:aws:ecs:...:cluster/name → name */
	static String shortName(String arn) {
		if (arn == null) {
			return "";
		}
		int slash = arn.lastIndexOf('/');
		return slash >= 0 ? arn.substring(slash + 1) : arn;
	}

	private static String textOrNull(JsonNode n) {
		if (n == null || n.isMissingNode() || n.isNull()) {
			return null;
		}
		String s = n.asText();
		return isBlank(s) ? null : s;
	}

	private static boolean isBlank(String s) {
		return s == null || s.trim().isEmpty();
	}

	private static int nz(Integer v) {
		return v == null ? 0 : v.intValue();
	}
}
