package dev.web.api.bm_w010;

import lombok.Data;

/**
 * TeamStats API Request
 */
@Data
public class TeamStatsRequest {

    /** 国 */
    private String country;

    /** リーグ */
    private String league;

    /** チーム（slug） */
    private String team;
}
