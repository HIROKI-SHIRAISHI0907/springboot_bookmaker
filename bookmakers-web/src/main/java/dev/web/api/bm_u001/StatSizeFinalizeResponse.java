package dev.web.api.bm_u001;

import lombok.Data;

/**
 * StatSizeFinalizeResponseレスポンス
 *
 * @author shiraishitoshio
 */
@Data
public class StatSizeFinalizeResponse {

	/** レスポンスコード */
	private String responseCode; // "0"=成功, "9"=失敗 など運用に合わせて

	/** メッセージ */
    private String message;

}
