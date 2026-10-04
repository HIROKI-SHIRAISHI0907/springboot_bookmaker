package dev.web.api.bm_a013;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.web.api.bm_a013.CostReportDtos.ExchangeRate;
import dev.web.config.AwsDashboardPropertiesConfig;

/**
 * 料金の円換算に使う USD/JPY レート。
 *
 *   1. aws.dashboard.usd-jpy-rate が設定されていれば、その固定値を使う
 *   2. 未設定なら frankfurter.dev（ECB 参考レート・API キー不要）から取得する
 *        - 過去の月 : その月の月末時点のレート（土日なら直前の営業日）… 再起動まで保持
 *        - 当月     : 今日時点のレート … 6 時間で取り直す
 *   3. 取得できなければ aws.dashboard.usd-jpy-fallback-rate（既定 150）を使い、その旨を返す
 *
 * ※ AWS が実際に請求するときの円換算レートとは別物なので、カード明細とは数 % ずれる。
 */
@Service
public class ExchangeRateService {

	private static final Logger log = LoggerFactory.getLogger(ExchangeRateService.class);

	private static final String URL = "https://api.frankfurter.dev/v1/%s?base=USD&symbols=JPY";
	private static final String SOURCE_ECB = "ECB 参考レート（frankfurter.dev）";
	private static final long LATEST_TTL_MILLIS = 6L * 60 * 60 * 1000;
	/** 取得に失敗したときは 10 分間は取りに行かない（レポートが毎回タイムアウト待ちにならないように） */
	private static final long FAILURE_TTL_MILLIS = 10L * 60 * 1000;

	private final double fixedRate;
	private final double fallbackRate;
	private final ZoneId zone;
	private final RestTemplate http;
	private final ObjectMapper mapper = new ObjectMapper();
	private final Map<String, Cached> cache = new ConcurrentHashMap<String, Cached>();

	public ExchangeRateService(AwsDashboardPropertiesConfig props,
			@Value("${aws.dashboard.usd-jpy-rate:0}") double fixedRate,
			@Value("${aws.dashboard.usd-jpy-fallback-rate:150}") double fallbackRate) {
		this.fixedRate = fixedRate;
		this.fallbackRate = fallbackRate;
		this.zone = ZoneId.of(props.getZoneId());

		SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
		f.setConnectTimeout(3000);
		f.setReadTimeout(5000);
		this.http = new RestTemplate(f);
	}

	/** その月の月末時点（当月・未来の月なら今日時点）の 1 USD あたりの円 */
	public ExchangeRate usdJpy(YearMonth ym) {
		if (fixedRate > 0) {
			return new ExchangeRate(fixedRate, null, "設定値（aws.dashboard.usd-jpy-rate）", false);
		}

		LocalDate today = LocalDate.now(zone);
		LocalDate date = ym.atEndOfMonth();
		boolean latest = !date.isBefore(today);
		if (latest) {
			date = today;
		}

		String key = date.toString();
		long now = System.currentTimeMillis();
		Cached c = cache.get(key);
		if (c != null && (c.expiresAt < 0 || c.expiresAt > now)) {
			return c.value;
		}

		ExchangeRate rate;
		long expiresAt;
		try {
			String body = http.getForObject(String.format(URL, date), String.class);
			JsonNode root = mapper.readTree(body);
			double r = root.path("rates").path("JPY").asDouble(0);
			if (r <= 0) {
				throw new IllegalStateException("JPY のレートがありません: " + body);
			}
			// date は実際に使われた営業日（土日・祝日なら直前の営業日）
			rate = new ExchangeRate(r, root.path("date").asText(key), SOURCE_ECB, false);
			expiresAt = latest ? now + LATEST_TTL_MILLIS : -1;
		} catch (Exception e) {
			log.warn("[fx] USD/JPY {} の取得に失敗: {}", key, e.getMessage());
			rate = new ExchangeRate(fallbackRate, null, "既定値（為替レートを取得できませんでした）", true);
			expiresAt = now + FAILURE_TTL_MILLIS;
		}
		cache.put(key, new Cached(rate, expiresAt));
		return rate;
	}

	private static final class Cached {
		private final ExchangeRate value;
		/** -1 = 期限なし */
		private final long expiresAt;

		private Cached(ExchangeRate value, long expiresAt) {
			this.value = value;
			this.expiresAt = expiresAt;
		}
	}
}