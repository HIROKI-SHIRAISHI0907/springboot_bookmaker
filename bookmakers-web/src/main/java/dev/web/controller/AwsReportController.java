package dev.web.controller;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.function.Supplier;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_a013.ApiResult;
import dev.web.api.bm_a013.AwsDailyStatsCollector;
import dev.web.api.bm_a013.AwsMonthlyReportService;
import dev.web.api.bm_a013.AwsReportDtos.MonthlyReport;
import dev.web.api.bm_a013.AwsReportDtos.StatsStatus;
import dev.web.config.AwsDashboardPropertiesConfig;

/**
 * AWS 月次レポート（PDF 用データ）と日次スナップショットの管理 API
 *
 * GET  /api/aws/report/monthly?month=2026-09   … 1 か月分のレポートデータ
 * GET  /api/aws/stats/status                   … 日次集計の状況（どの期間のデータがあるか）
 * POST /api/aws/stats/backfill?from=&to=       … 過去分の取り込み（ECS は 90 日前まで）
 * POST /api/aws/stats/snapshot                 … 今日の RDS 件数を今すぐ記録
 */
@RestController
@RequestMapping("/api/aws")
public class AwsReportController {

	private final AwsMonthlyReportService reportService;
	private final AwsDailyStatsCollector collector;
	private final ZoneId zone;

	public AwsReportController(AwsMonthlyReportService reportService, AwsDailyStatsCollector collector,
			AwsDashboardPropertiesConfig props) {
		this.reportService = reportService;
		this.collector = collector;
		this.zone = ZoneId.of(props.getZoneId());
	}

	@GetMapping("/report/monthly")
	public ApiResult<MonthlyReport> monthly(@RequestParam(value = "month", required = false) final String month) {
		return ApiResult.of("report-monthly", new Supplier<MonthlyReport>() {
			@Override
			public MonthlyReport get() {
				YearMonth ym = (month == null || month.trim().isEmpty()) ? YearMonth.now(zone) : YearMonth.parse(month);
				return reportService.build(ym);
			}
		});
	}

	@GetMapping("/stats/status")
	public ApiResult<StatsStatus> status() {
		return ApiResult.of("stats-status", new Supplier<StatsStatus>() {
			@Override
			public StatsStatus get() {
				return collector.status();
			}
		});
	}

	/** 過去分の取り込み。省略時は「90 日前 〜 昨日」 */
	@PostMapping("/stats/backfill")
	public ApiResult<String> backfill(
			@RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate from,
			@RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate to,
			@RequestParam(value = "force", defaultValue = "false") final boolean force) {
		return ApiResult.of("stats-backfill", new Supplier<String>() {
			@Override
			public String get() {
				LocalDate today = LocalDate.now(zone);
				LocalDate f = from == null ? today.minusDays(89) : from;
				LocalDate t = to == null ? today.minusDays(1) : to;
				return collector.startBackfill(f, t, force);
			}
		});
	}

	@PostMapping("/stats/snapshot")
	public ApiResult<String> snapshot() {
		return ApiResult.of("stats-snapshot", new Supplier<String>() {
			@Override
			public String get() {
				return collector.snapshotRdsNow();
			}
		});
	}
}
