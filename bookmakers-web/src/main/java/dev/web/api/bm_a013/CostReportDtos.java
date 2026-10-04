package dev.web.api.bm_a013;

import java.util.Collections;
import java.util.List;

/**
 * 月次レポートの「料金」部分（Cost Explorer）。
 * amount / total は USD、amountJpy / totalJpy はその月のレートで換算した円（整数に丸め）。
 */
public final class CostReportDtos {

	private CostReportDtos() {
	}

	/** 円換算に使ったレート */
	public static final class ExchangeRate {
		/** 1 USD あたりの円 */
		private final double rate;
		/** レートの日付（固定値・既定値のときは null） */
		private final String date;
		/** 出どころ（画面にそのまま出す） */
		private final String source;
		/** true = 取得に失敗して既定値を使った */
		private final boolean fallback;

		public ExchangeRate(double rate, String date, String source, boolean fallback) {
			this.rate = rate;
			this.date = date;
			this.source = source;
			this.fallback = fallback;
		}

		public double getRate() {
			return rate;
		}

		public String getDate() {
			return date;
		}

		public String getSource() {
			return source;
		}

		public boolean isFallback() {
			return fallback;
		}
	}

	/** サービス別 / 使用タイプ別の 1 行 */
	public static final class CostItem {
		private final String name;
		private final double amount;
		private final long amountJpy;
		/** 対象月の合計に対する割合（%） */
		private final double share;

		public CostItem(String name, double amount, long amountJpy, double share) {
			this.name = name;
			this.amount = amount;
			this.amountJpy = amountJpy;
			this.share = share;
		}

		public String getName() {
			return name;
		}

		public double getAmount() {
			return amount;
		}

		public long getAmountJpy() {
			return amountJpy;
		}

		public double getShare() {
			return share;
		}
	}

	/** 月別推移の 1 か月分（円はその月のレートで換算） */
	public static final class CostMonth {
		private final String month;
		private final double total;
		private final long totalJpy;
		/** true = まだ確定していない（当月 or 翌月初の締め前） */
		private final boolean estimated;
		private final ExchangeRate rate;

		public CostMonth(String month, double total, long totalJpy, boolean estimated, ExchangeRate rate) {
			this.month = month;
			this.total = total;
			this.totalJpy = totalJpy;
			this.estimated = estimated;
			this.rate = rate;
		}

		public String getMonth() {
			return month;
		}

		public double getTotal() {
			return total;
		}

		public long getTotalJpy() {
			return totalJpy;
		}

		public boolean isEstimated() {
			return estimated;
		}

		public ExchangeRate getRate() {
			return rate;
		}
	}

	public static final class CostMonthly {
		private final String unit;
		private final double total;
		private final long totalJpy;
		private final boolean estimated;
		/** 対象月の換算レート */
		private final ExchangeRate rate;
		private final List<CostItem> byService;
		private final List<CostItem> byUsageType;
		/** 対象月を含む直近 6 か月（古い順） */
		private final List<CostMonth> monthly;
		/** 取得できなかったときのメッセージ（正常時は null） */
		private final String error;
		private final String fetchedAt;

		public CostMonthly(String unit, double total, long totalJpy, boolean estimated, ExchangeRate rate,
				List<CostItem> byService, List<CostItem> byUsageType, List<CostMonth> monthly, String error,
				String fetchedAt) {
			this.unit = unit;
			this.total = total;
			this.totalJpy = totalJpy;
			this.estimated = estimated;
			this.rate = rate;
			this.byService = byService;
			this.byUsageType = byUsageType;
			this.monthly = monthly;
			this.error = error;
			this.fetchedAt = fetchedAt;
		}

		public static CostMonthly error(String message, String fetchedAt) {
			return new CostMonthly("USD", 0, 0, false, null, Collections.<CostItem>emptyList(),
					Collections.<CostItem>emptyList(), Collections.<CostMonth>emptyList(), message, fetchedAt);
		}

		public String getUnit() {
			return unit;
		}

		public double getTotal() {
			return total;
		}

		public long getTotalJpy() {
			return totalJpy;
		}

		public boolean isEstimated() {
			return estimated;
		}

		public ExchangeRate getRate() {
			return rate;
		}

		public List<CostItem> getByService() {
			return byService;
		}

		public List<CostItem> getByUsageType() {
			return byUsageType;
		}

		public List<CostMonth> getMonthly() {
			return monthly;
		}

		public String getError() {
			return error;
		}

		public String getFetchedAt() {
			return fetchedAt;
		}
	}
}