package dev.web.api.bm_a013;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.logging.log4j.util.Supplier;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;

import dev.web.api.bm_a013.DashboardDtos.DynamoSummary;
import dev.web.api.bm_a013.DashboardDtos.Ec2Summary;
import dev.web.api.bm_a013.DashboardDtos.EcsSummary;
import dev.web.api.bm_a013.DashboardDtos.IamSummary;
import dev.web.api.bm_a013.DashboardDtos.LambdaSummary;
import dev.web.api.bm_a013.DashboardDtos.Overview;
import dev.web.api.bm_a013.DashboardDtos.OverviewItem;
import dev.web.api.bm_a013.DashboardDtos.RdsSummary;
import dev.web.api.bm_a013.DashboardDtos.RecordSet;
import dev.web.api.bm_a013.DashboardDtos.Route53Summary;
import dev.web.api.bm_a013.DashboardDtos.S3Summary;
import dev.web.config.AwsDashboardPropertiesConfig;
import software.amazon.awssdk.services.sts.StsClient;

/**
 * 各サービスをキャッシュ付きでまとめる窓口。
 * 概要タブは全サービスを並列に取得し、その結果はタブ表示でも再利用される。
 */
@Service
public class AwsDashboardFacade implements DisposableBean {

	private final EcsDashboardService ecsService;
	private final S3DashboardService s3Service;
	private final RdsDashboardService rdsService;
	private final IamDashboardService iamService;
	private final LambdaDashboardService lambdaService;
	private final DynamoDbDashboardService dynamoService;
	private final Ec2DashboardService ec2Service;
	private final Route53DashboardService route53Service;
	private final StsClient sts;
	private final TtlCache cache;
	private final AwsDashboardPropertiesConfig props;
	private final ZoneId zone;

	private final ExecutorService pool = Executors.newFixedThreadPool(8);

	public AwsDashboardFacade(EcsDashboardService ecsService, S3DashboardService s3Service,
			RdsDashboardService rdsService, IamDashboardService iamService, LambdaDashboardService lambdaService,
			DynamoDbDashboardService dynamoService, Ec2DashboardService ec2Service,
			Route53DashboardService route53Service, StsClient sts, TtlCache cache,
			AwsDashboardPropertiesConfig props) {
		this.ecsService = ecsService;
		this.s3Service = s3Service;
		this.rdsService = rdsService;
		this.iamService = iamService;
		this.lambdaService = lambdaService;
		this.dynamoService = dynamoService;
		this.ec2Service = ec2Service;
		this.route53Service = route53Service;
		this.sts = sts;
		this.cache = cache;
		this.props = props;
		this.zone = ZoneId.of(props.getZoneId());
	}

	/** アプリ終了時にスレッドプールを止める（@PreDestroy の代わり） */
	@Override
	public void destroy() {
		pool.shutdownNow();
	}

	public LocalDate today() {
		return LocalDate.now(zone);
	}

	public DateRange range(LocalDate date) {
		return DateRange.of(date == null ? today() : date, zone);
	}

	// ============ 各タブ ============

	public EcsSummary ecs(LocalDate date, boolean refresh) {
		final DateRange r = range(date);
		return cache.get("ecs:" + r.getDate(), refresh, new Supplier<EcsSummary>() {
			@Override
			public EcsSummary get() {
				return ecsService.summary(r);
			}
		});
	}

	public S3Summary s3(boolean refresh) {
		final DateRange r = range(null);
		return cache.get("s3", refresh, new Supplier<S3Summary>() {
			@Override
			public S3Summary get() {
				return s3Service.summary(r);
			}
		});
	}

	public RdsSummary rds(boolean refresh) {
		return cache.get("rds", refresh, new Supplier<RdsSummary>() {
			@Override
			public RdsSummary get() {
				return rdsService.summary();
			}
		});
	}

	public IamSummary iam(boolean refresh) {
		final DateRange r = range(null);
		return cache.get("iam", refresh, new Supplier<IamSummary>() {
			@Override
			public IamSummary get() {
				return iamService.summary(r);
			}
		});
	}

	public LambdaSummary lambda(LocalDate date, boolean refresh) {
		final DateRange r = range(date);
		return cache.get("lambda:" + r.getDate(), refresh, new Supplier<LambdaSummary>() {
			@Override
			public LambdaSummary get() {
				return lambdaService.summary(r);
			}
		});
	}

	public DynamoSummary dynamo(boolean refresh) {
		final DateRange r = range(null);
		return cache.get("dynamo", refresh, new Supplier<DynamoSummary>() {
			@Override
			public DynamoSummary get() {
				return dynamoService.summary(r);
			}
		});
	}

	public Ec2Summary ec2(boolean refresh) {
		final DateRange r = range(null);
		return cache.get("ec2", refresh, new Supplier<Ec2Summary>() {
			@Override
			public Ec2Summary get() {
				return ec2Service.summary(r);
			}
		});
	}

	public Route53Summary route53(boolean refresh) {
		return cache.get("route53", refresh, new Supplier<Route53Summary>() {
			@Override
			public Route53Summary get() {
				return route53Service.summary();
			}
		});
	}

	public List<RecordSet> route53Records(final String zoneId, boolean refresh) {
		return cache.get("route53:" + zoneId, refresh, new Supplier<List<RecordSet>>() {
			@Override
			public List<RecordSet> get() {
				return route53Service.records(zoneId);
			}
		});
	}

	// ============ 概要 ============

	public Overview overview(LocalDate date, final boolean refresh) {
		final LocalDate d = range(date).getDate();

		List<String> names = new ArrayList<String>();
		List<Future<Map<String, Object>>> futures = new ArrayList<Future<Map<String, Object>>>();

		submit(names, futures, "ECS", () -> {
			EcsSummary s = ecs(d, refresh);
			return metrics("実行回数", s.getRunCount(), "起動タスク数", s.getLaunchedTaskCount(),
					"失敗", s.getFailedRunCount(), "クラスター", s.getClusters().size());
		});
		submit(names, futures, "S3", () -> {
			S3Summary s = s3(refresh);
			return metrics("バケット数", s.getBucketCount(), "リージョン数", s.getByRegion().size());
		});
		submit(names, futures, "RDS", () -> {
			RdsSummary s = rds(refresh);
			return metrics("インスタンス", s.getInstanceCount(), "テーブル数", s.getTableCount(),
					"総レコード数", s.getTotalRows());
		});
		submit(names, futures, "IAM", () -> {
			Map<String, Integer> a = iam(refresh).getAccountSummary();
			return metrics("ユーザー", nz(a.get("Users")), "ロール", nz(a.get("Roles")),
					"ポリシー", nz(a.get("Policies")), "グループ", nz(a.get("Groups")));
		});
		submit(names, futures, "Lambda", () -> {
			LambdaSummary s = lambda(d, refresh);
			return metrics("関数数", s.getFunctionCount(), "実行回数", s.getTotalInvocations(),
					"エラー", s.getTotalErrors());
		});
		submit(names, futures, "DynamoDB", () -> {
			DynamoSummary s = dynamo(refresh);
			return metrics("テーブル数", s.getTableCount(), "アイテム数(概算)", s.getTotalItems());
		});
		submit(names, futures, "EC2", () -> {
			Ec2Summary s = ec2(refresh);
			return metrics("インスタンス", s.getInstanceCount(), "稼働中", nz(s.getByState().get("running")),
					"停止中", nz(s.getByState().get("stopped")));
		});
		submit(names, futures, "Route53", () -> {
			Route53Summary s = route53(refresh);
			return metrics("ホストゾーン", s.getZoneCount(), "レコード数", s.getTotalRecords());
		});

		String account = cache.<String>get("sts", refresh, () -> fetchAccountId());

		// 各サービスの結果を待つ。失敗したサービスはエラーカードにする
		List<OverviewItem> items = new ArrayList<OverviewItem>();
		for (int i = 0; i < futures.size(); i++) {
			try {
				items.add(new OverviewItem(names.get(i), true, futures.get(i).get(), null));
			} catch (Exception e) {
				Throwable cause = e.getCause() != null ? e.getCause() : e;
				items.add(new OverviewItem(names.get(i), false, Collections.<String, Object> emptyMap(),
						cause.getClass().getSimpleName() + ": " + cause.getMessage()));
			}
		}
		return new Overview(d.toString(), account, props.getRegion(), items);
	}

	/** AWS アカウントID（取れなければ null） */
	private String fetchAccountId() {
		try {
			return sts.getCallerIdentity().account();
		} catch (Exception e) {
			return null;
		}
	}

	private void submit(List<String> names, List<Future<Map<String, Object>>> futures, String name,
			Callable<Map<String, Object>> task) {
		names.add(name);
		futures.add(pool.submit(task));
	}

	private static Map<String, Object> metrics(Object... kv) {
		Map<String, Object> m = new LinkedHashMap<String, Object>();
		for (int i = 0; i + 1 < kv.length; i += 2) {
			m.put(String.valueOf(kv[i]), kv[i + 1]);
		}
		return m;
	}

	private static int nz(Integer v) {
		return v == null ? 0 : v.intValue();
	}
}