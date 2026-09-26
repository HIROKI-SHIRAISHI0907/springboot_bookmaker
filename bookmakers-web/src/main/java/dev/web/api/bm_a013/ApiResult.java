package dev.web.api.bm_a013;

import java.time.OffsetDateTime;
import java.util.function.Supplier;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * 各 API の共通レスポンス。
 * 権限不足などで 1 サービスが失敗しても、画面側でそのタブだけエラー表示できるよう
 * 例外は HTTP 500 にせず ok=false + error で返す。
 */
@Slf4j
@Getter
@AllArgsConstructor
public class ApiResult<T> {

	private final boolean ok;
	private final T data;
	private final String error;
	private final String fetchedAt;

	public static <T> ApiResult<T> of(String name, Supplier<T> supplier) {
		String now = OffsetDateTime.now().toString();
		try {
			return new ApiResult<T>(true, supplier.get(), null, now);
		} catch (Exception e) {
			log.warn("[aws-dashboard] {} の取得に失敗: {}", name, e.getMessage(), e);
			return new ApiResult<T>(false, null, e.getClass().getSimpleName() + ": " + e.getMessage(), now);
		}
	}
}
