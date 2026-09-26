package dev.web.api.bm_a013;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import dev.web.api.bm_a013.DashboardDtos.LambdaFunction;
import dev.web.api.bm_a013.DashboardDtos.LambdaSummary;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.Dimension;
import software.amazon.awssdk.services.cloudwatch.model.GetMetricDataRequest;
import software.amazon.awssdk.services.cloudwatch.model.Metric;
import software.amazon.awssdk.services.cloudwatch.model.MetricDataQuery;
import software.amazon.awssdk.services.cloudwatch.model.MetricDataResult;
import software.amazon.awssdk.services.cloudwatch.model.MetricStat;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.FunctionConfiguration;

/**
 * Lambda 関数一覧 ＋ その日の実行回数/エラー数（CloudWatch メトリクス）
 */
@Service
public class LambdaDashboardService {

	/** GetMetricData 1回あたりの最大クエリ数は 500（1関数 2クエリ） */
	private static final int FUNCTIONS_PER_CALL = 250;

	private final LambdaClient lambda;
	private final CloudWatchClient cloudWatch;

	public LambdaDashboardService(LambdaClient lambda, CloudWatchClient cloudWatch) {
		this.lambda = lambda;
		this.cloudWatch = cloudWatch;
	}

	public LambdaSummary summary(DateRange range) {
		List<FunctionConfiguration> fns = new ArrayList<FunctionConfiguration>();
		for (FunctionConfiguration f : lambda.listFunctionsPaginator().functions()) {
			fns.add(f);
		}

		Map<String, Long> inv = new HashMap<String, Long>();
		Map<String, Long> err = new HashMap<String, Long>();
		fetchMetrics(fns, range, inv, err);

		List<LambdaFunction> out = new ArrayList<LambdaFunction>();
		long totalInv = 0;
		long totalErr = 0;
		for (int i = 0; i < fns.size(); i++) {
			FunctionConfiguration f = fns.get(i);
			long in = nz(inv.get("i" + i));
			long er = nz(err.get("e" + i));
			totalInv += in;
			totalErr += er;
			out.add(new LambdaFunction(
					f.functionName(),
					f.runtimeAsString(),
					f.memorySize(),
					f.timeout(),
					f.codeSize(),
					f.lastModified(),
					f.handler(),
					in,
					er));
		}

		// 実行回数の多い順 → 同数なら関数名順
		Collections.sort(out, new Comparator<LambdaFunction>() {
			@Override
			public int compare(LambdaFunction a, LambdaFunction b) {
				int c = Long.compare(b.getInvocations(), a.getInvocations());
				return c != 0 ? c : a.getName().compareTo(b.getName());
			}
		});

		return new LambdaSummary(range.getDate().toString(), out.size(), totalInv, totalErr, out);
	}

	/**
	 * CloudWatch から Invocations / Errors を取得する。
	 * クエリID は "i{index}" / "e{index}"（先頭は英小文字である必要がある）。
	 */
	private void fetchMetrics(List<FunctionConfiguration> fns, DateRange range,
			Map<String, Long> inv, Map<String, Long> err) {
		if (fns.isEmpty() || !range.getStart().isBefore(range.endOrNow())) {
			return;
		}
		for (int from = 0; from < fns.size(); from += FUNCTIONS_PER_CALL) {
			int to = Math.min(from + FUNCTIONS_PER_CALL, fns.size());
			List<MetricDataQuery> queries = new ArrayList<MetricDataQuery>();
			for (int i = from; i < to; i++) {
				String name = fns.get(i).functionName();
				queries.add(query("i" + i, "Invocations", name));
				queries.add(query("e" + i, "Errors", name));
			}

			GetMetricDataRequest req = GetMetricDataRequest.builder()
					.metricDataQueries(queries)
					.startTime(range.getStart())
					.endTime(range.endOrNow())
					.build();

			// ページングで同じIDが複数回返ることがあるので加算する
			for (MetricDataResult r : cloudWatch.getMetricDataPaginator(req).metricDataResults()) {
				long sum = 0;
				for (Double v : r.values()) {
					sum += v == null ? 0 : v.longValue();
				}
				Map<String, Long> target = r.id().startsWith("i") ? inv : err;
				target.put(r.id(), nz(target.get(r.id())) + sum);
			}
		}
	}

	/** 1時間単位の Sum を取り、合計して「その日」の値にする */
	private static MetricDataQuery query(String id, String metricName, String functionName) {
		return MetricDataQuery.builder()
				.id(id)
				.returnData(true)
				.metricStat(MetricStat.builder()
						.metric(Metric.builder()
								.namespace("AWS/Lambda")
								.metricName(metricName)
								.dimensions(Dimension.builder().name("FunctionName").value(functionName).build())
								.build())
						.period(3600)
						.stat("Sum")
						.build())
				.build();
	}

	private static long nz(Long v) {
		return v == null ? 0L : v.longValue();
	}
}