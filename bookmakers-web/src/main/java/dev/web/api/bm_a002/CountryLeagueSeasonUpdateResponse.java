package dev.web.api.bm_a002;

import lombok.Data;

/**
 * country_league_season_masterAPIレスポンス
 *
 * @author shiraishitoshio
 */
@Data
public class CountryLeagueSeasonUpdateResponse {

	/** レスポンスコード */
	private String responseCode; // "0"=成功, "9"=失敗 など運用に合わせて

	/** メッセージ */
    private String message;

}
