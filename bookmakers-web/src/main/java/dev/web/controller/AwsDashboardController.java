package dev.web.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_a013.ApiResult;
import dev.web.api.bm_a013.AwsDashboardFacade;
import dev.web.api.bm_a013.DashboardDtos.DynamoSummary;
import dev.web.api.bm_a013.DashboardDtos.Ec2Summary;
import dev.web.api.bm_a013.DashboardDtos.EcsSummary;
import dev.web.api.bm_a013.DashboardDtos.IamSummary;
import dev.web.api.bm_a013.DashboardDtos.LambdaSummary;
import dev.web.api.bm_a013.DashboardDtos.Overview;
import dev.web.api.bm_a013.DashboardDtos.RdsSummary;
import dev.web.api.bm_a013.DashboardDtos.RdsTables;
import dev.web.api.bm_a013.DashboardDtos.RecordSet;
import dev.web.api.bm_a013.DashboardDtos.Route53Summary;
import dev.web.api.bm_a013.DashboardDtos.S3Summary;

/**
 * AWS ダッシュボード API
 *
 * GET /api/aws/overview?date=2026-09-24
 * GET /api/aws/ecs?date=2026-09-24
 * GET /api/aws/s3
 * GET /api/aws/rds
 * GET /api/aws/rds/tables?db=soccer_bm
 * GET /api/aws/iam
 * GET /api/aws/lambda?date=2026-09-24
 * GET /api/aws/dynamodb
 * GET /api/aws/ec2
 * GET /api/aws/route53
 * GET /api/aws/route53/zones/{zoneId}/records
 *
 * 共通: refresh=true でキャッシュを無視して再取得
 */
@RestController
@RequestMapping("/api/aws")
public class AwsDashboardController {

	private final AwsDashboardFacade facade;

	public AwsDashboardController(AwsDashboardFacade facade) {
		this.facade = facade;
	}

	@GetMapping("/overview")
	public ApiResult<Overview> overview(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate date,
			@RequestParam(defaultValue = "false") final boolean refresh) {
		return ApiResult.of("overview", new Supplier<Overview>() {
			@Override
			public Overview get() {
				return facade.overview(date, refresh);
			}
		});
	}

	@GetMapping("/ecs")
	public ApiResult<EcsSummary> ecs(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate date,
			@RequestParam(defaultValue = "false") final boolean refresh) {
		return ApiResult.of("ecs", new Supplier<EcsSummary>() {
			@Override
			public EcsSummary get() {
				return facade.ecs(date, refresh);
			}
		});
	}

	@GetMapping("/s3")
	public ApiResult<S3Summary> s3(@RequestParam(defaultValue = "false") final boolean refresh) {
		return ApiResult.of("s3", new Supplier<S3Summary>() {
			@Override
			public S3Summary get() {
				return facade.s3(refresh);
			}
		});
	}

	@GetMapping("/rds")
	public ApiResult<RdsSummary> rds(@RequestParam(defaultValue = "false") final boolean refresh) {
		return ApiResult.of("rds", new Supplier<RdsSummary>() {
			@Override
			public RdsSummary get() {
				return facade.rds(refresh);
			}
		});
	}

	/** 指定 DB（db= に RdsSummary.databases[].key を渡す）の全スキーマのテーブル件数 */
	@GetMapping("/rds/tables")
	public ApiResult<RdsTables> rdsTables(@RequestParam("db") final String db,
			@RequestParam(defaultValue = "false") final boolean refresh) {
		return ApiResult.of("rds-tables", new Supplier<RdsTables>() {
			@Override
			public RdsTables get() {
				return facade.rdsTables(db, refresh);
			}
		});
	}

	@GetMapping("/iam")
	public ApiResult<IamSummary> iam(@RequestParam(defaultValue = "false") final boolean refresh) {
		return ApiResult.of("iam", new Supplier<IamSummary>() {
			@Override
			public IamSummary get() {
				return facade.iam(refresh);
			}
		});
	}

	@GetMapping("/lambda")
	public ApiResult<LambdaSummary> lambda(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate date,
			@RequestParam(defaultValue = "false") final boolean refresh) {
		return ApiResult.of("lambda", new Supplier<LambdaSummary>() {
			@Override
			public LambdaSummary get() {
				return facade.lambda(date, refresh);
			}
		});
	}

	@GetMapping("/dynamodb")
	public ApiResult<DynamoSummary> dynamodb(@RequestParam(defaultValue = "false") final boolean refresh) {
		return ApiResult.of("dynamodb", new Supplier<DynamoSummary>() {
			@Override
			public DynamoSummary get() {
				return facade.dynamo(refresh);
			}
		});
	}

	@GetMapping("/ec2")
	public ApiResult<Ec2Summary> ec2(@RequestParam(defaultValue = "false") final boolean refresh) {
		return ApiResult.of("ec2", new Supplier<Ec2Summary>() {
			@Override
			public Ec2Summary get() {
				return facade.ec2(refresh);
			}
		});
	}

	@GetMapping("/route53")
	public ApiResult<Route53Summary> route53(@RequestParam(defaultValue = "false") final boolean refresh) {
		return ApiResult.of("route53", new Supplier<Route53Summary>() {
			@Override
			public Route53Summary get() {
				return facade.route53(refresh);
			}
		});
	}

	@GetMapping("/route53/zones/{zoneId}/records")
	public ApiResult<List<RecordSet>> route53Records(@PathVariable final String zoneId,
			@RequestParam(defaultValue = "false") final boolean refresh) {
		return ApiResult.of("route53-records", new Supplier<List<RecordSet>>() {
			@Override
			public List<RecordSet> get() {
				return facade.route53Records(zoneId, refresh);
			}
		});
	}
}
