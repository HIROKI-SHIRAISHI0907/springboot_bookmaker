package dev.web.api.bm_u003;

import java.util.List;

import lombok.Data;

//返却DTO（フィルタ用）
@Data
public class FavoriteScopeResponse {

	/** お気に入り未登録 */
	private boolean allowAll;

	/** 許可国 */
    private List<CountryScope> allowedCountries;

    /** 許可国リーグ */
    private List<LeagueScope> allowedLeaguesByCountry;

    /** 許可国リーグチーム */
    private List<TeamScope> allowedTeamsByCountryLeague;

}
