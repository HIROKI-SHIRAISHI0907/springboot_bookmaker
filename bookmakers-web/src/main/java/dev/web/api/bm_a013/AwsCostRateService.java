package dev.web.api.bm_a013;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import dev.web.api.bm_a013.CostReportDtos.CostItem;
import dev.web.api.bm_a013.CostReportDtos.CostMonth;
import dev.web.api.bm_a013.CostReportDtos.CostMonthly;
import dev.web.api.bm_a013.CostReportDtos.ExchangeRate;
import dev.web.config.AwsDashboardPropertiesConfig;
import software.amazon.awssdk.services.costexplorer.CostExplorerClient;
import software.amazon.awssdk.services.costexplorer.model.CostExplorerException;
import software.amazon.awssdk.services.costexplorer.model.DateInterval;
import software.amazon.awssdk.services.costexplorer.model.GetCostAndUsageRequest;
import software.amazon.awssdk.services.costexplorer.model.GetCostAndUsageResponse;
import software.amazon.awssdk.services.costexplorer.model.Granularity;
import software.amazon.awssdk.services.costexplorer.model.Group;
import software.amazon.awssdk.services.costexplorer.model.GroupDefinition;
import software.amazon.awssdk.services.costexplorer.model.GroupDefinitionType;
import software.amazon.awssdk.services.costexplorer.model.MetricValue;
import software.amazon.awssdk.services.costexplorer.model.ResultByTime;

/**
 * Cost Explorer（GetCostAndUsage）から月別の料金（USD）を取得する。
 *
 *   - サービス別   : 対象月を含む直近 6 か月 … 月別推移 ＋ 対象月のサービス内訳（API 1 回）
 *   - 使用タイプ別 : 対象月のみ … 「何に」料金がかかっているかの詳細（API 1 回）
 *
 * GetCostAndUsage は 1 リクエスト 0.01 USD かかるので、結果をメモリにキャッシュする。
 *   - 確定済みの月だけの結果（Estimated=false） … 再起動まで保持
 *   - 確定前の月を含む結果                     … 6 時間で取り直す（Cost Explorer の更新は 1 日数回）
 *
 * 円換算は ExchangeRateService のレート（各月の月末時点。当月は今日時点）で行う。
 *
 * Cost Explorer の日付は UTC。End は「その日を含まない」。
 * 必要な IAM 権限: ce:GetCostAndUsage（Resource "*"）
 */
@Service
public class AwsCostRateService {

	private static final Logger log = LoggerFactory.getLogger(AwsCostRateService.class);

	private static final String METRIC = "UnblendedCost";
	private static final int TREND_MONTHS = 6;
	private static final int USAGE_TYPE_TOP = 15;
	/** これ未満（0.01 USD 未満に丸まる）のサービス・使用タイプは表示しない */
	private static final double MIN_AMOUNT = 0.005;
	private static final long ESTIMATED_TTL_MILLIS = 6L * 60 * 60 * 1000;

	private final CostExplorerClient ce;
	private final ExchangeRateService fx;
	private final ZoneId zone;
	private final boolean enabled;
	private final Map<String, Cached> cache = new ConcurrentHashMap<String, Cached>();

	public AwsCostRateService(CostExplorerClient ce, ExchangeRateService fx, AwsDashboardPropertiesConfig props,
			@Value("${aws.dashboard.cost-enabled:true}") boolean enabled) {
		this.ce = ce;
		this.fx = fx;
		this.zone = ZoneId.of(props.getZoneId());
		this.enabled = enabled;
	}

	public CostMonthly build(YearMonth ym) {
		String fetchedAt = OffsetDateTime.now(zone).toString();
		if (!enabled) {
			return CostMonthly.error("料金の取得は無効になっています（aws.dashboard.cost-enabled=false）", fetchedAt);
		}
		try {
			LocalDate endLimit = LocalDate.now(ZoneOffset.UTC).plusDays(1);
			LocalDate monthEnd = ym.plusMonths(1).atDay(1);

			Fetched trend = fetch("SERVICE", ym.minusMonths(TREND_MONTHS - 1).atDay(1), monthEnd, endLimit);
			Fetched usage = fetch("USAGE_TYPE", ym.atDay(1), monthEnd, endLimit);

			List<CostMonth> monthly = new ArrayList<CostMonth>();
			for (int k = TREND_MONTHS - 1; k >= 0; k--) {
				YearMonth mym = ym.minusMonths(k);
				String m = mym.toString();
				double usd = sum(trend.month(m));
				ExchangeRate r = fx.usdJpy(mym);
				monthly.add(new CostMonth(m, round(usd), toJpy(usd, r), trend.estimatedMonths.contains(m), r));
			}

			String key = ym.toString();
			ExchangeRate rate = fx.usdJpy(ym);
			Map<String, Double> services = trend.month(key);
			Map<String, Double> usageTypes = usage.month(key);
			double total = sum(services);

			return new CostMonthly("USD", round(total), toJpy(total, rate), trend.estimatedMonths.contains(key), rate,
					items(services, 0, rate), items(usageTypes, USAGE_TYPE_TOP, rate), monthly, null, fetchedAt);

		} catch (CostExplorerException e) {
			log.warn("[aws-cost] {} failed: {}", ym, e.getMessage());
			return CostMonthly.error(message(e), fetchedAt);
		} catch (Exception e) {
			log.warn("[aws-cost] {} failed", ym, e);
			return CostMonthly.error("料金を取得できませんでした: " + e.getClass().getSimpleName() + ": " + e.getMessage(),
					fetchedAt);
		}
	}

	// =====================================================================
	// Cost Explorer 呼び出し（キャッシュ付き）
	// =====================================================================

	private Fetched fetch(String dimension, LocalDate start, LocalDate end, LocalDate endLimit) {
		if (end.isAfter(endLimit)) {
			end = endLimit; // 未来日は指定しない
		}
		if (!start.isBefore(end)) {
			return new Fetched(); // 対象月がまだ始まっていない
		}

		String key = dimension + "|" + start + "|" + end;
		long now = System.currentTimeMillis();
		Cached c = cache.get(key);
		if (c != null && (c.expiresAt < 0 || c.expiresAt > now)) {
			return c.value;
		}

		Fetched f = new Fetched();
		String token = null;
		do {
			GetCostAndUsageResponse res = ce.getCostAndUsage(GetCostAndUsageRequest.builder()
					.timePeriod(DateInterval.builder().start(start.toString()).end(end.toString()).build())
					.granularity(Granularity.MONTHLY)
					.metrics(METRIC)
					.groupBy(GroupDefinition.builder().type(GroupDefinitionType.DIMENSION).key(dimension).build())
					.nextPageToken(token)
					.build());

			for (ResultByTime r : res.resultsByTime()) {
				String month = r.timePeriod().start().substring(0, 7); // 2026-09-01 → 2026-09
				Map<String, Double> m = f.monthForWrite(month);
				if (Boolean.TRUE.equals(r.estimated())) {
					f.estimatedMonths.add(month);
				}
				for (Group g : r.groups()) {
					MetricValue v = g.metrics().get(METRIC);
					if (v == null || v.amount() == null) {
						continue;
					}
					String name = g.keys().isEmpty() ? "(unknown)" : g.keys().get(0);
					double amount = new BigDecimal(v.amount()).doubleValue();
					Double prev = m.get(name);
					m.put(name, Double.valueOf((prev == null ? 0 : prev.doubleValue()) + amount));
				}
			}
			token = res.nextPageToken();
		} while (token != null && !token.isEmpty());

		cache.put(key, new Cached(f, f.estimatedMonths.isEmpty() ? -1 : now + ESTIMATED_TTL_MILLIS));
		log.info("[aws-cost] GetCostAndUsage {} {}〜{}", dimension, start, end);
		return f;
	}

	private static String message(CostExplorerException e) {
		String code = e.awsErrorDetails() == null ? null : e.awsErrorDetails().errorCode();
		if ("AccessDeniedException".equals(code)) {
			return "Cost Explorer の権限がありません（IAM ロールに ce:GetCostAndUsage を追加してください）";
		}
		if ("DataUnavailableException".equals(code)) {
			return "Cost Explorer のデータがまだありません（コンソールで有効化してから 24 時間ほどかかります）";
		}
		return "料金を取得できませんでした: " + (code == null ? e.getMessage() : code);
	}

	// =====================================================================
	// helpers
	// =====================================================================

	/** 金額の大きい順。top > 0 なら上位 top 件 ＋「その他」にまとめる */
	private static List<CostItem> items(Map<String, Double> src, int top, ExchangeRate rate) {
		double total = sum(src);
		List<Map.Entry<String, Double>> entries = new ArrayList<Map.Entry<String, Double>>();
		for (Map.Entry<String, Double> e : src.entrySet()) {
			if (Math.abs(e.getValue().doubleValue()) >= MIN_AMOUNT) {
				entries.add(e);
			}
		}
		Collections.sort(entries, new Comparator<Map.Entry<String, Double>>() {
			@Override
			public int compare(Map.Entry<String, Double> a, Map.Entry<String, Double> b) {
				int c = Double.compare(b.getValue().doubleValue(), a.getValue().doubleValue());
				return c != 0 ? c : a.getKey().compareTo(b.getKey());
			}
		});

		List<CostItem> out = new ArrayList<CostItem>();
		double others = 0;
		int otherCount = 0;
		for (int i = 0; i < entries.size(); i++) {
			double amount = entries.get(i).getValue().doubleValue();
			if (top > 0 && i >= top) {
				others += amount;
				otherCount++;
			} else {
				out.add(new CostItem(entries.get(i).getKey(), round(amount), toJpy(amount, rate), share(amount, total)));
			}
		}
		if (otherCount > 0) {
			out.add(new CostItem("その他（" + otherCount + " 件）", round(others), toJpy(others, rate),
					share(others, total)));
		}
		return out;
	}

	private static double sum(Map<String, Double> m) {
		double s = 0;
		for (Double v : m.values()) {
			s += v.doubleValue();
		}
		return s;
	}

	private static double share(double amount, double total) {
		return total > 0 ? Math.round(amount / total * 1000) / 10.0 : 0; // %（小数 1 桁）
	}

	/** 丸める前の USD × レート → 円（整数） */
	private static long toJpy(double usd, ExchangeRate rate) {
		return Math.round(usd * rate.getRate());
	}

	private static double round(double v) {
		return Math.round(v * 100) / 100.0; // USD 小数 2 桁
	}

	/** 月 → (サービス名 or 使用タイプ → 金額) */
	private static final class Fetched {
		private final Map<String, Map<String, Double>> byMonth = new HashMap<String, Map<String, Double>>();
		private final Set<String> estimatedMonths = new HashSet<String>();

		private Map<String, Double> monthForWrite(String month) {
			Map<String, Double> m = byMonth.get(month);
			if (m == null) {
				m = new HashMap<String, Double>();
				byMonth.put(month, m);
			}
			return m;
		}

		private Map<String, Double> month(String month) {
			Map<String, Double> m = byMonth.get(month);
			return m == null ? Collections.<String, Double>emptyMap() : m;
		}
	}

	private static final class Cached {
		private final Fetched value;
		/** -1 = 期限なし */
		private final long expiresAt;

		private Cached(Fetched value, long expiresAt) {
			this.value = value;
			this.expiresAt = expiresAt;
		}
	}
}