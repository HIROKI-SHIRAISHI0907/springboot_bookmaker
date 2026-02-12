package dev.web.api.bm_a003;

import lombok.Data;

/**
 * country_league_masterAPIリクエスト
 *
 * @author shiraishitoshio
 */
@Data
public class CountryLeagueRequest {

	/** ID */
	private String id;

	/** 国 */
	private String country;

	/** リーグ */
    private String league;

    /** チーム */
    private String team;

    /** リンク */
    private String link;

}
