package dev.web.api.bm_u003;

import lombok.Data;

/**
 * FavoriteResponseAPIレスポンス
 *
 * @author shiraishitoshio
 */
@Data
public class FavoriteResponse {

	/** レスポンスコード */
	private String responseCode; // "0"=成功, "9"=失敗 など運用に合わせて

	/** メッセージ */
    private String message;

}
