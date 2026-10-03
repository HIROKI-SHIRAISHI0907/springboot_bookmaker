package dev.web.api.bm_a099;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * まとめて更新のレスポンス（全部成功 = 200、失敗が混ざる = 207）
 * @author shiraishitoshio
 *
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnalyzeErrorBatchResponse {

	private String responseCode;

	private int total;

	private int success;

	private int failed;

	private List<ItemResult> results;

	/** 1件ごとの結果 */
	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class ItemResult {

		private String seq;

		private String responseCode;

		private String message;
	}
}
