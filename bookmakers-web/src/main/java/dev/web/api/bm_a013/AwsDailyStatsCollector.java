package dev.web.api.bm_a013;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import dev.web.api.bm_a013.AwsReportDtos.CategoryCoverage;
import dev.web.api.bm_a013.AwsReportDtos.StatsStatus;
import dev.web.api.bm_a013.DashboardDtos.EcsSummary;
import dev.web.api.bm_a013.DashboardDtos.EcsTaskDefCount;
import dev.web.api.bm_a013.DashboardDtos.LambdaFunction;
import dev.web.api.bm_a013.DashboardDtos.LambdaSummary;
import dev.web.api.bm_a013.DashboardDtos.RdsDatabaseRef;
import dev.web.api.bm_a013.DashboardDtos.RdsTables;
import dev.web.api.bm_a013.DashboardDtos.TableCount;
import dev.web.config.AwsDashboardPropertiesConfig;
import dev.web.repository.bm.AwsDailyStatsRepository;

/**
 * 月次レポート用の日次スナップショットを集計して aws_daily_stats に保存する。
 *
 * 毎日 00:30（JST）に「前日分」を集計する。
 *   - ECS    : CloudTrail の RunTask（前日 0:00〜24:00）… CloudTrail は 90 日分まで遡れる
 *   - Lambda : CloudWatch の Invocations / Errors（前日分）… 約 15 か月分まで遡れる
 *   - RDS    : 全 DB・全スキーマのテーブル件数（その時点の件数を前日の値として記録）
 *              … 過去には遡れないので、記録を始めた日からのデータになる
 *
 * 追加の AWS リソースは作らない。費用は CloudWatch GetMetricData の従量分（関数数×2 メトリクス/日）程度。
 * 同じ日を何度集計しても結果は同じ（削除→登録）なので、ECS タスクが複数台でも問題ない。
 */
@Service
public class AwsDailyStatsCollector implements DisposableBean {

	private static final Logger log = LoggerFactory.getLogger(AwsDailyStatsCollector.class);

	/** CloudTrail の LookupEvents で遡れる日数 */
	private static final int CLOUDTRAIL_MAX_DAYS = 89;

	private final EcsDashboardService ecsService;
	private final LambdaDashboardService lambdaService;
	private final RdsDashboardService rdsService;
	private final AwsDailyStatsRepository repository;
	private final AwsDashboardPropertiesConfig props;
	private final ZoneId zone;

	private final ExecutorService worker = Executors.newSingleThreadExecutor();
	private final AtomicBoolean running = new AtomicBoolean(false);
	private volatile String lastRunAt;
	private volatile String lastMessage = "まだ実行されていません";

	public AwsDailyStatsCollector(EcsDashboardService ecsService, LambdaDashboardService lambdaService,
			RdsDashboardService rdsService, AwsDailyStatsRepository repository, AwsDashboardPropertiesConfig props) {
		this.ecsService = ecsService;
		this.lambdaService = lambdaService;
		this.rdsService = rdsService;
		this.repository = repository;
		this.props = props;
		this.zone = ZoneId.of(props.getZoneId());
	}

	@Override
	public void destroy() {
		worker.shutdownNow();
	}

	// =====================================================================
	// 定期実行
	// =====================================================================

	/** 毎日 00:30（JST）に前日分を集計し、直近の欠けている日も埋める */
	@Scheduled(cron = "0 30 0 * * *", zone = "Asia/Tokyo")
	public void daily() {
		if (!props.isStatsEnabled()) {
			return;
		}
		if (!running.compareAndSet(false, true)) {
			log.info("[aws-stats] 別の集計が実行中のためスキップ");
			return;
		}
		try {
			LocalDate yesterday = LocalDate.now(zone).minusDays(1);
			StringBuilder msg = new StringBuilder();
			msg.append(collectEcsSafely(yesterday));
			msg.append(" / ").append(collectLambdaSafely(yesterday));
			msg.append(" / ").append(collectRdsSafely(yesterday));

			// 直近 N 日で欠けている日（アプリが止まっていた日など）を埋める
			int filled = fillMissing(yesterday.minusDays(Math.max(0, props.getStatsBackfillDays())),
					yesterday.minusDays(1), false);
			if (filled > 0) {
				msg.append(" / 欠けていた ").append(filled).append(" 日分を補完");
			}
			finish("日次集計 " + yesterday + ": " + msg);
		} finally {
			running.set(false);
		}
	}

	// =====================================================================
	// 手動実行（画面から）
	// =====================================================================

	/**
	 * 過去分の取り込み（バックグラウンド実行）。
	 * ECS は CloudTrail の保持期間（約 90 日）より前は取得できないのでスキップする。
	 */
	public String startBackfill(final LocalDate from, final LocalDate to, final boolean force) {
		if (!running.compareAndSet(false, true)) {
			return "別の集計が実行中です。終わってからもう一度実行してください。";
		}
		worker.submit(new Runnable() {
			@Override
			public void run() {
				try {
					lastMessage = "過去分を取り込み中: " + from + " 〜 " + to;
					int n = fillMissing(from, to, force);
					finish("過去分の取り込み完了: " + from + " 〜 " + to + "（" + n + " 日分）");
				} catch (Exception e) {
					finish("過去分の取り込みに失敗: " + e.getClass().getSimpleName() + ": " + e.getMessage());
					log.warn("[aws-stats] backfill failed", e);
				} finally {
					running.set(false);
				}
			}
		});
		return "取り込みを開始しました（" + from + " 〜 " + to + "）。数分かかることがあります。";
	}

	/** 今日の RDS 件数を今すぐ記録する（記録を今日から始めたいとき） */
	public String snapshotRdsNow() {
		LocalDate today = LocalDate.now(zone);
		String m = collectRdsSafely(today);
		finish("RDS 件数を記録: " + today + " " + m);
		return m;
	}

	public StatsStatus status() {
		List<CategoryCoverage> cov = new ArrayList<CategoryCoverage>();
		try {
			for (Map.Entry<String, Object[]> e : repository.coverage().entrySet()) {
				Object[] v = e.getValue();
				cov.add(new CategoryCoverage(e.getKey(), String.valueOf(v[0]), String.valueOf(v[1]),
						((Integer) v[2]).intValue()));
			}
		} catch (Exception e) {
			return new StatsStatus(props.isStatsEnabled(), running.get(), lastRunAt,
					"集計テーブルを読めません: " + e.getMessage(), cov);
		}
		return new StatsStatus(props.isStatsEnabled(), running.get(), lastRunAt, lastMessage, cov);
	}

	// =====================================================================
	// 集計本体
	// =====================================================================

	/** from〜to の ECS / Lambda を、データが無い日（force なら全日）だけ集計する */
	private int fillMissing(LocalDate from, LocalDate to, boolean force) {
		if (to.isBefore(from)) {
			return 0;
		}
		LocalDate ecsLimit = LocalDate.now(zone).minusDays(CLOUDTRAIL_MAX_DAYS);
		Set<LocalDate> ecsDone = repository.datesWith(AwsDailyStatsRepository.ECS_TOTAL, from, to);
		Set<LocalDate> lambdaDone = repository.datesWith(AwsDailyStatsRepository.LAMBDA, from, to);

		int n = 0;
		for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
			boolean did = false;
			if ((force || !ecsDone.contains(d)) && !d.isBefore(ecsLimit)) {
				collectEcsSafely(d);
				did = true;
				sleep(500); // CloudTrail LookupEvents の呼び出し制限（2回/秒）対策
			}
			if (force || !lambdaDone.contains(d)) {
				collectLambdaSafely(d);
				did = true;
			}
			if (did) {
				n++;
				lastMessage = "取り込み中: " + d + "（" + n + " 日目）";
			}
		}
		return n;
	}

	private String collectEcsSafely(LocalDate d) {
		try {
			EcsSummary s = ecsService.summary(DateRange.of(d, zone));
			List<Row> total = new ArrayList<Row>();
			total.add(new Row(d, AwsDailyStatsRepository.ECS_TOTAL, "", s.getRunCount(),
					Long.valueOf(s.getFailedRunCount()), false));
			repository.replaceDay(d, AwsDailyStatsRepository.ECS_TOTAL, total);

			List<Row> defs = new ArrayList<Row>();
			for (EcsTaskDefCount c : s.getByTaskDefinition()) {
				defs.add(new Row(d, AwsDailyStatsRepository.ECS_TASKDEF, c.getTaskDefinition(), c.getRuns(),
						Long.valueOf(c.getFailedRuns()), false));
			}
			repository.replaceDay(d, AwsDailyStatsRepository.ECS_TASKDEF, defs);
			return "ECS " + s.getRunCount() + " 回";
		} catch (Exception e) {
			log.warn("[aws-stats] ECS {} failed: {}", d, e.getMessage());
			return "ECS 失敗(" + e.getClass().getSimpleName() + ")";
		}
	}

	private String collectLambdaSafely(LocalDate d) {
		try {
			LambdaSummary s = lambdaService.summary(DateRange.of(d, zone));
			List<Row> rows = new ArrayList<Row>();
			for (LambdaFunction f : s.getFunctions()) {
				rows.add(new Row(d, AwsDailyStatsRepository.LAMBDA, f.getName(), f.getInvocations(),
						Long.valueOf(f.getErrors()), false));
			}
			repository.replaceDay(d, AwsDailyStatsRepository.LAMBDA, rows);
			return "Lambda " + s.getTotalInvocations() + " 回";
		} catch (Exception e) {
			log.warn("[aws-stats] Lambda {} failed: {}", d, e.getMessage());
			return "Lambda 失敗(" + e.getClass().getSimpleName() + ")";
		}
	}

	private String collectRdsSafely(LocalDate d) {
		try {
			List<Row> rows = new ArrayList<Row>();
			int tables = 0;
			for (RdsDatabaseRef db : rdsService.databases()) {
				if (db.getError() != null) {
					continue;
				}
				RdsTables t = rdsService.tables(db.getKey());
				for (TableCount c : t.getTables()) {
					rows.add(new Row(d, AwsDailyStatsRepository.RDS_TABLE,
							t.getDatabase() + "|" + c.getSchema() + "|" + c.getTable(), c.getRows(), null,
							c.isEstimated()));
					tables++;
				}
			}
			repository.replaceDay(d, AwsDailyStatsRepository.RDS_TABLE, rows);
			return "RDS " + tables + " テーブル";
		} catch (Exception e) {
			log.warn("[aws-stats] RDS {} failed: {}", d, e.getMessage());
			return "RDS 失敗(" + e.getClass().getSimpleName() + ")";
		}
	}

	private void finish(String message) {
		lastMessage = message;
		lastRunAt = OffsetDateTime.now(zone).toString();
		log.info("[aws-stats] {}", message);
	}

	private static void sleep(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
