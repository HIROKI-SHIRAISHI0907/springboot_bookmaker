package dev.web.api.bm_w011;

import java.util.List;

import lombok.Data;

/**
 * fetchLeaguesGrouped() 用
 * {
 *   country: string;
 *   leagues: { name, team_count, path }[]
 * }
 */
@Data
public class LeagueGroupedResponse {

	/** 国 */
    private String country;

    /** リーグデータ */
    private List<LeagueInfoDTO> leagues;
}
