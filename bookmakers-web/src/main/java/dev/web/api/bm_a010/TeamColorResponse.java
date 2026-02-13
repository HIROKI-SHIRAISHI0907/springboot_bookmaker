package dev.web.api.bm_a010;

import lombok.Data;

/**
 * country_league_season_masterAPIレスポンス
 *
 * @author shiraishitoshio
 */
@Data
public class TeamColorResponse {

	/** レスポンスコード */
	private String responseCode; // "0"=成功, "9"=失敗 など運用に合わせて

	/** メッセージ */
    private String message;

}
