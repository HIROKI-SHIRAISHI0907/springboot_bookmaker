package dev.web.api.bm_a003;

import lombok.Data;

/**
 * country_league_masterAPIレスポンス
 *
 * @author shiraishitoshio
 */
@Data
public class CountryLeagueResponse {

	/** レスポンスコード */
	private String responseCode; // "0"=成功, "9"=失敗 など運用に合わせて

	/** メッセージ */
    private String message;

}
