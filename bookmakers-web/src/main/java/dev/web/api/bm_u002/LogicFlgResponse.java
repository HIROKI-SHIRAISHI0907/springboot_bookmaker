package dev.web.api.bm_u002;

import lombok.Data;

/**
 * LogicFlgResponseAPIレスポンス
 *
 * @author shiraishitoshio
 */
@Data
public class LogicFlgResponse {

	/** レスポンスコード */
	private String responseCode; // "0"=成功, "9"=失敗 など運用に合わせて

	/** メッセージ */
    private String message;

}
