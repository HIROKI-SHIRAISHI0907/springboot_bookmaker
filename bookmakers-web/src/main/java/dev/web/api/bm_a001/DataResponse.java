package dev.web.api.bm_a001;

import lombok.Data;

/**
 * DataResponse
 *
 * @author shiraishitoshio
 */
@Data
public class DataResponse {

	/** レスポンスコード */
	private String responseCode; // "0"=成功, "9"=失敗 など運用に合わせて

	/** メッセージ */
    private String message;

}
