package dev.web.api.bm_w017;

import lombok.Data;

/**
 * country_league_masterAPIリクエスト
 *
 * @author shiraishitoshio
 */
@Data
public class CountryLeagueUpdateRequest {

	/** 国 */
	private String country;

	/** リーグ */
    private String league;

    /** チーム */
    private String team;

    /** リンク */
    private String link;

}
