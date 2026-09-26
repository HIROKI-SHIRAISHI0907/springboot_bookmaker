package dev.web.api.bm_a013;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.stereotype.Service;

import dev.web.api.bm_a013.AwsReportDtos.EcsMonthly;
import dev.web.api.bm_a013.AwsReportDtos.LambdaMonthly;
import dev.web.api.bm_a013.AwsReportDtos.MonthTotal;
import dev.web.api.bm_a013.AwsReportDtos.MonthlyReport;
import dev.web.api.bm_a013.AwsReportDtos.NamedSeries;
import dev.web.api.bm_a013.AwsReportDtos.RdsDbMonthly;
import dev.web.api.bm_a013.AwsReportDtos.RdsMonthly;
import dev.web.api.bm_a013.AwsReportDtos.RdsSchemaMonthly;
import dev.web.api.bm_a013.AwsReportDtos.RdsTableMonthly;
import dev.web.config.AwsDashboardPropertiesConfig;
import dev.web.repository.bm.AwsDailyStatsRepository;
import dev.web.repository.bm.AwsDailyStatsRepository.MonthSum;
import dev.web.repository.bm.AwsDailyStatsRepository.Row;
import software.amazon.awssdk.services.sts.StsClient;

/**
 * aws_daily_stats から 1 か月分のレポートを組み立てる（PDF はブラウザ側で作る）。
 */
@Service
public class AwsMonthlyReportService {

	private final AwsDailyStatsRepository repository;
	private final AwsDashboardPropertiesConfig props;
	private final StsClient sts;
	private final ZoneId zone;

	public AwsMonthlyReportService(AwsDailyStatsRepository repository, AwsDashboardPropertiesConfig props,
			StsClient sts) {
		this.repository = repository;
		this.props = props;
		this.sts = sts;
		this.zone = ZoneId.of(props.getZoneId());
	}

	public MonthlyReport build(YearMonth ym) {
		LocalDate from = ym.atDay(1);
		LocalDate to = ym.atEndOfMonth();
		int n = ym.lengthOfMonth();

		List<String> days = new ArrayList<String>();
		for (int i = 0; i < n; i++) {
			days.add(from.plusDays(i).toString());
		}

		List<Row> rows = repository.find(from, to);

		return new MonthlyReport(ym.toString(), days, OffsetDateTime.now(zone).toString(), accountId(),
				props.getRegion(), buildEcs(ym, rows, n), buildLambda(rows, n), buildRds(rows, n));
	}

	// =====================================================================
	// ECS
	// =====================================================================

	private EcsMonthly buildEcs(YearMonth ym, List<Row> rows, int n) {
		long[] daily = new long[n];
		long[] failedDaily = new long[n];
		Set<Integer> dataDays = new HashSet<Integer>();
		Map<String, long[]> defDaily = new HashMap<String, long[]>();
		Map<String, Long> defFailed = new HashMap<String, Long>();

		for (Row r : rows) {
			int i = r.date.getDayOfMonth() - 1;
			if (AwsDailyStatsRepository.ECS_TOTAL.equals(r.category)) {
				daily[i] = r.value;
				failedDaily[i] = r.value2 == null ? 0 : r.value2.longValue();
				dataDays.add(Integer.valueOf(i));
			} else if (AwsDailyStatsRepository.ECS_TASKDEF.equals(r.category)) {
				long[] d = defDaily.get(r.key);
				if (d == null) {
					d = new long[n];
					defDaily.put(r.key, d);
				}
				d[i] = r.value;
				Long f = defFailed.get(r.key);
				defFailed.put(r.key, Long.valueOf((f == null ? 0 : f.longValue())
						+ (r.value2 == null ? 0 : r.value2.longValue())));
			}
		}

		List<NamedSeries> defs = new ArrayList<NamedSeries>();
		for (Map.Entry<String, long[]> e : defDaily.entrySet()) {
			Long f = defFailed.get(e.getKey());
			defs.add(new NamedSeries(e.getKey(), sum(e.getValue()), f == null ? 0 : f.longValue(), e.getValue()));
		}
		sortByTotalDesc(defs);

		// 直近 6 か月の月別合計（データが無い月は 0 件・0 日）
		List<MonthTotal> monthly = new ArrayList<MonthTotal>();
		Map<String, MonthSum> sums = new HashMap<String, MonthSum>();
		for (MonthSum s : repository.monthlySums(AwsDailyStatsRepository.ECS_TOTAL, ym.minusMonths(5).atDay(1),
				ym.atEndOfMonth())) {
			sums.put(s.month, s);
		}
		for (int k = 5; k >= 0; k--) {
			String key = ym.minusMonths(k).toString();
			MonthSum s = sums.get(key);
			monthly.add(new MonthTotal(key, s == null ? 0 : s.total, s == null ? 0 : s.days));
		}

		return new EcsMonthly(sum(daily), sum(failedDaily), dataDays.size(), daily, failedDaily, covered(dataDays, n),
				defs, monthly);
	}

	// =====================================================================
	// Lambda
	// =====================================================================

	private LambdaMonthly buildLambda(List<Row> rows, int n) {
		long[] daily = new long[n];
		Set<Integer> dataDays = new HashSet<Integer>();
		Map<String, long[]> fnDaily = new HashMap<String, long[]>();
		Map<String, Long> fnErrors = new HashMap<String, Long>();

		for (Row r : rows) {
			if (!AwsDailyStatsRepository.LAMBDA.equals(r.category)) {
				continue;
			}
			int i = r.date.getDayOfMonth() - 1;
			dataDays.add(Integer.valueOf(i));
			daily[i] += r.value;
			long[] d = fnDaily.get(r.key);
			if (d == null) {
				d = new long[n];
				fnDaily.put(r.key, d);
			}
			d[i] = r.value;
			Long e = fnErrors.get(r.key);
			fnErrors.put(r.key, Long.valueOf((e == null ? 0 : e.longValue())
					+ (r.value2 == null ? 0 : r.value2.longValue())));
		}

		List<NamedSeries> fns = new ArrayList<NamedSeries>();
		long totalErrors = 0;
		for (Map.Entry<String, long[]> e : fnDaily.entrySet()) {
			Long err = fnErrors.get(e.getKey());
			long errors = err == null ? 0 : err.longValue();
			totalErrors += errors;
			fns.add(new NamedSeries(e.getKey(), sum(e.getValue()), errors, e.getValue()));
		}
		sortByTotalDesc(fns);

		return new LambdaMonthly(sum(daily), totalErrors, dataDays.size(), daily, covered(dataDays, n), fns);
	}

	// =====================================================================
	// RDS
	// =====================================================================

	private RdsMonthly buildRds(List<Row> rows, int n) {
		// DB → スキーマ → テーブル → 日別件数
		Map<String, Map<String, Map<String, Long[]>>> tree = new TreeMap<String, Map<String, Map<String, Long[]>>>();
		Map<String, Boolean> estimated = new HashMap<String, Boolean>();
		Set<Integer> snapshotDays = new HashSet<Integer>();

		for (Row r : rows) {
			if (!AwsDailyStatsRepository.RDS_TABLE.equals(r.category)) {
				continue;
			}
			String[] p = r.key.split("\\|", 3);
			if (p.length < 3) {
				continue;
			}
			int i = r.date.getDayOfMonth() - 1;
			snapshotDays.add(Integer.valueOf(i));

			Map<String, Map<String, Long[]>> schemas = tree.get(p[0]);
			if (schemas == null) {
				schemas = new TreeMap<String, Map<String, Long[]>>();
				tree.put(p[0], schemas);
			}
			Map<String, Long[]> tables = schemas.get(p[1]);
			if (tables == null) {
				tables = new TreeMap<String, Long[]>();
				schemas.put(p[1], tables);
			}
			Long[] d = tables.get(p[2]);
			if (d == null) {
				d = new Long[n];
				tables.put(p[2], d);
			}
			d[i] = Long.valueOf(r.value);
			if (r.estimated) {
				estimated.put(r.key, Boolean.TRUE);
			}
		}

		List<RdsDbMonthly> dbs = new ArrayList<RdsDbMonthly>();
		for (Map.Entry<String, Map<String, Map<String, Long[]>>> db : tree.entrySet()) {
			List<RdsSchemaMonthly> schemaList = new ArrayList<RdsSchemaMonthly>();
			for (Map.Entry<String, Map<String, Long[]>> sc : db.getValue().entrySet()) {
				Long[] schemaDaily = new Long[n];
				List<RdsTableMonthly> tableList = new ArrayList<RdsTableMonthly>();

				for (Map.Entry<String, Long[]> t : sc.getValue().entrySet()) {
					Long[] d = t.getValue();
					Long max = null;
					for (int i = 0; i < n; i++) {
						if (d[i] != null) {
							schemaDaily[i] = Long.valueOf((schemaDaily[i] == null ? 0 : schemaDaily[i].longValue())
									+ d[i].longValue());
							if (max == null || d[i].longValue() > max.longValue()) {
								max = d[i];
							}
						}
					}
					Long first = firstNonNull(d);
					Long last = lastNonNull(d);
					String key = db.getKey() + "|" + sc.getKey() + "|" + t.getKey();
					tableList.add(new RdsTableMonthly(t.getKey(), d, first, last, delta(first, last), max,
							Boolean.TRUE.equals(estimated.get(key))));
				}

				Collections.sort(tableList, new Comparator<RdsTableMonthly>() {
					@Override
					public int compare(RdsTableMonthly a, RdsTableMonthly b) {
						long la = a.getLast() == null ? -1 : a.getLast().longValue();
						long lb = b.getLast() == null ? -1 : b.getLast().longValue();
						int c = Long.compare(lb, la);
						return c != 0 ? c : a.getTable().compareTo(b.getTable());
					}
				});

				Long first = firstNonNull(schemaDaily);
				Long last = lastNonNull(schemaDaily);
				schemaList.add(new RdsSchemaMonthly(sc.getKey(), schemaDaily, first, last, delta(first, last),
						tableList));
			}
			dbs.add(new RdsDbMonthly(db.getKey(), schemaList));
		}

		return new RdsMonthly(snapshotDays.size(), dbs);
	}

	// =====================================================================
	// helpers
	// =====================================================================

	private String accountId() {
		try {
			return sts.getCallerIdentity().account();
		} catch (Exception e) {
			return null;
		}
	}

	private static boolean[] covered(Set<Integer> days, int n) {
		boolean[] out = new boolean[n];
		for (Integer i : days) {
			out[i.intValue()] = true;
		}
		return out;
	}

	private static long sum(long[] a) {
		long s = 0;
		for (long v : a) {
			s += v;
		}
		return s;
	}

	private static Long firstNonNull(Long[] a) {
		for (Long v : a) {
			if (v != null) {
				return v;
			}
		}
		return null;
	}

	private static Long lastNonNull(Long[] a) {
		for (int i = a.length - 1; i >= 0; i--) {
			if (a[i] != null) {
				return a[i];
			}
		}
		return null;
	}

	private static Long delta(Long first, Long last) {
		return (first == null || last == null) ? null : Long.valueOf(last.longValue() - first.longValue());
	}

	private static void sortByTotalDesc(List<NamedSeries> list) {
		Collections.sort(list, new Comparator<NamedSeries>() {
			@Override
			public int compare(NamedSeries a, NamedSeries b) {
				int c = Long.compare(b.getTotal(), a.getTotal());
				return c != 0 ? c : a.getName().compareTo(b.getName());
			}
		});
	}
}
