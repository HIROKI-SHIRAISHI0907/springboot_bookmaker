package dev.web.api.bm_w011;

import java.util.List;

import lombok.Data;

/**
 * fetchTeamsInLeague(country, league) 用
 * {
 *   country, league, teams: TeamItem[]
 * }
 */
@Data
public class TeamsInLeagueResponse {

	/** 国 */
    private String country;

    /** リーグ */
    private String league;

    /** チームデータ */
    private List<TeamItemDTO> teams;
}
