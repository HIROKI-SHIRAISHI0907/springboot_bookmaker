package dev.web.api.bm_a025;

import lombok.Data;

/**
 * static_dataAPIレスポンス
 *
 * @author shiraishitoshio
 */
@Data
public class RealTimeDataResponse {

	/** レスポンスコード */
	private String responseCode; // "0"=成功, "9"=失敗 など運用に合わせて

	/** メッセージ */
    private String message;

}
