package dev.web.controller;

import java.time.LocalDate;
import java.util.List;

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
public class AdminAwsDashboardController {

	private final AwsDashboardFacade facade;

	public AdminAwsDashboardController(AwsDashboardFacade facade) {
		this.facade = facade;
	}

	@GetMapping("/overview")
	public ApiResult<Overview> overview(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@RequestParam(defaultValue = "false") boolean refresh) {
		return ApiResult.of("overview", () -> facade.overview(date, refresh));
	}

	@GetMapping("/ecs")
	public ApiResult<EcsSummary> ecs(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@RequestParam(defaultValue = "false") boolean refresh) {
		return ApiResult.of("ecs", () -> facade.ecs(date, refresh));
	}

	@GetMapping("/s3")
	public ApiResult<S3Summary> s3(@RequestParam(defaultValue = "false") boolean refresh) {
		return ApiResult.of("s3", () -> facade.s3(refresh));
	}

	@GetMapping("/rds")
	public ApiResult<RdsSummary> rds(@RequestParam(defaultValue = "false") boolean refresh) {
		return ApiResult.of("rds", () -> facade.rds(refresh));
	}

	@GetMapping("/iam")
	public ApiResult<IamSummary> iam(@RequestParam(defaultValue = "false") boolean refresh) {
		return ApiResult.of("iam", () -> facade.iam(refresh));
	}

	@GetMapping("/lambda")
	public ApiResult<LambdaSummary> lambda(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@RequestParam(defaultValue = "false") boolean refresh) {
		return ApiResult.of("lambda", () -> facade.lambda(date, refresh));
	}

	@GetMapping("/dynamodb")
	public ApiResult<DynamoSummary> dynamodb(@RequestParam(defaultValue = "false") boolean refresh) {
		return ApiResult.of("dynamodb", () -> facade.dynamo(refresh));
	}

	@GetMapping("/ec2")
	public ApiResult<Ec2Summary> ec2(@RequestParam(defaultValue = "false") boolean refresh) {
		return ApiResult.of("ec2", () -> facade.ec2(refresh));
	}

	@GetMapping("/route53")
	public ApiResult<Route53Summary> route53(@RequestParam(defaultValue = "false") boolean refresh) {
		return ApiResult.of("route53", () -> facade.route53(refresh));
	}

	@GetMapping("/route53/zones/{zoneId}/records")
	public ApiResult<List<RecordSet>> route53Records(@PathVariable String zoneId,
			@RequestParam(defaultValue = "false") boolean refresh) {
		return ApiResult.of("route53-records", () -> facade.route53Records(zoneId, refresh));
	}
}
