package dev.web.api.bm_a099;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新系のレスポンス（responseCode は HTTP ステータスと同じ文字列: 200 / 400 / 404 / 500）
 * @author shiraishitoshio
 *
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnalyzeErrorResponse {

	private String responseCode;

	private String message;

	/** 更新後の行（成功時） */
	private AnalyzeErrorDTO item;
}
