package dev.web.api.bm_a002;

import lombok.Data;

/**
 * country_league_season_masterAPIリクエスト
 * @author shiraishitoshio
 *
 */
@Data
public class CountryLeagueSeasonRequest {

	/** 国 */
	private String country;

	/** リーグ */
	private String league;

	/** シーズン年 */
	private String seasonYear;

	/** パス */
	private String path;

}
