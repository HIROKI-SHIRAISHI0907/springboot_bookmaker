package dev.web.api.bm_a005;

import lombok.Data;

//返却DTO（フィルタ用）
@Data
public class AllLeagueResponse {

    /** レスポンスコード */
	private String responseCode; // "0"=成功, "9"=失敗 など運用に合わせて

	/** メッセージ */
    private String message;

}
