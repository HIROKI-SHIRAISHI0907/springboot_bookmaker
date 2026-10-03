package dev.web.api.bm_a099;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 一覧のレスポンス（OFFSET ページング）
 * @author shiraishitoshio
 *
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnalyzeErrorListResponse {

	/** 開始位置 */
	private int offset;

	/** 1ページの件数 */
	private int limit;

	/** 条件に合う全件数 */
	private int total;

	/** 一覧 */
	private List<AnalyzeErrorDTO> items;
}
