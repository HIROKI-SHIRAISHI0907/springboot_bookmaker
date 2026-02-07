package dev.web.api.bm_w014;

import lombok.Data;

//返却DTO（フィルタ用）
@Data
public class ForceAdminResponse {

    /** レスポンスコード */
	private String responseCode; // "0"=成功, "9"=失敗 など運用に合わせて

	/** メッセージ */
    private String message;

}
