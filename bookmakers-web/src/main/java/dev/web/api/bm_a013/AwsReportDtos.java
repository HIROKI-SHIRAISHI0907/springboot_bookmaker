package dev.web.api.bm_a013;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 月次レポート（PDF 出力用）の DTO 群
 */
public final class AwsReportDtos {

	private AwsReportDtos() {
	}

	/** 名前付きの日別系列（タスク定義別・関数別など） */
	@Getter
	@AllArgsConstructor
	public static class NamedSeries {
		private final String name;
		/** 月合計 */
		private final long total;
		/** 月合計（失敗 / エラー） */
		private final long secondaryTotal;
		/** 日別（その月の日数ぶん。データが無い日は 0） */
		private final long[] daily;
	}

	/** 月別の合計 */
	@Getter
	@AllArgsConstructor
	public static class MonthTotal {
		/** YYYY-MM */
		private final String month;
		private final long total;
		/** データがあった日数 */
		private final int dataDays;
	}

	// ===================== ECS =====================

	@Getter
	@AllArgsConstructor
	public static class EcsMonthly {
		private final long total;
		private final long failed;
		/** データがある日数（0 ならまだ集計されていない） */
		private final int dataDays;
		/** 日別の実行回数 */
		private final long[] daily;
		/** 日別の失敗回数 */
		private final long[] failedDaily;
		/** 日別: 集計済みか（false の日は「0 回」ではなく「未集計」） */
		private final boolean[] covered;
		private final List<NamedSeries> taskDefinitions;
		/** 直近 6 か月の月別合計（対象月を含む） */
		private final List<MonthTotal> monthlyTotals;
	}

	// ===================== Lambda =====================

	@Getter
	@AllArgsConstructor
	public static class LambdaMonthly {
		private final long totalInvocations;
		private final long totalErrors;
		private final int dataDays;
		private final long[] daily;
		/** 日別: 集計済みか */
		private final boolean[] covered;
		private final List<NamedSeries> functions;
	}

	// ===================== RDS =====================

	@Getter
	@AllArgsConstructor
	public static class RdsTableMonthly {
		private final String table;
		/** 日別の件数（スナップショットが無い日は null） */
		private final Long[] daily;
		/** 月内で最初に記録された件数 */
		private final Long first;
		/** 月内で最後に記録された件数 */
		private final Long last;
		/** last - first */
		private final Long delta;
		private final Long max;
		/** 推定値を含む */
		private final boolean estimated;
	}

	@Getter
	@AllArgsConstructor
	public static class RdsSchemaMonthly {
		private final String schema;
		/** 日別のスキーマ合計件数（スナップショットが無い日は null） */
		private final Long[] daily;
		private final Long first;
		private final Long last;
		private final Long delta;
		private final List<RdsTableMonthly> tables;
	}

	@Getter
	@AllArgsConstructor
	public static class RdsDbMonthly {
		private final String database;
		private final List<RdsSchemaMonthly> schemas;
	}

	@Getter
	@AllArgsConstructor
	public static class RdsMonthly {
		/** スナップショットがある日数 */
		private final int snapshotDays;
		private final List<RdsDbMonthly> databases;
	}

	// ===================== レポート全体 =====================

	@Getter
	@AllArgsConstructor
	public static class MonthlyReport {
		/** YYYY-MM */
		private final String month;
		/** その月の日付（YYYY-MM-DD） */
		private final List<String> days;
		private final String generatedAt;
		private final String accountId;
		private final String region;
		private final EcsMonthly ecs;
		private final LambdaMonthly lambda;
		private final RdsMonthly rds;
	}

	// ===================== 集計状況 =====================

	@Getter
	@AllArgsConstructor
	public static class CategoryCoverage {
		private final String category;
		private final String from;
		private final String to;
		private final int days;
	}

	@Getter
	@AllArgsConstructor
	public static class StatsStatus {
		private final boolean enabled;
		private final boolean running;
		private final String lastRunAt;
		private final String lastMessage;
		private final List<CategoryCoverage> coverage;
	}
}
