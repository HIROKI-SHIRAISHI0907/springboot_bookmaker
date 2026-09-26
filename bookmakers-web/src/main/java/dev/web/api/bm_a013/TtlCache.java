package dev.web.api.bm_a013;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import dev.web.config.AwsDashboardPropertiesConfig;

/**
 * AWS API の呼びすぎを防ぐための簡易 TTL キャッシュ。
 * refresh=true のときはキャッシュを無視して取り直す。
 */
@Component
public class TtlCache {

	private static final class Entry {
		private final Object value;
		private final long expiresAt;

		private Entry(Object value, long expiresAt) {
			this.value = value;
			this.expiresAt = expiresAt;
		}
	}

	private final Map<String, Entry> store = new ConcurrentHashMap<String, Entry>();
	private final long ttlMillis;

	public TtlCache(AwsDashboardPropertiesConfig props) {
		this.ttlMillis = Math.max(0, props.getCacheTtlSeconds()) * 1000L;
	}

	@SuppressWarnings("unchecked")
	public <T> T get(String key, boolean refresh, Supplier<T> loader) {
		long now = System.currentTimeMillis();
		if (!refresh) {
			Entry e = store.get(key);
			if (e != null && e.expiresAt > now) {
				return (T) e.value;
			}
		}
		T value = loader.get();
		if (ttlMillis > 0) {
			store.put(key, new Entry(value, now + ttlMillis));
		}
		return value;
	}
}
