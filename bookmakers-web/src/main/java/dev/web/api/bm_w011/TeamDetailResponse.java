package dev.web.api.bm_w011;

import lombok.Data;

/**
 * fetchTeamDetail(country, league, teamEnglish) 用
 */
@Data
public class TeamDetailResponse {

	/** id */
    private Integer id;

    /** 国 */
    private String country;

    /** リーグ */
    private String league;

    /** 名前 */
    private String name;

    /** 英語名 */
    private String english;

    /** ハッシュ */
    private String hash;

    /** リンク */
    private String link;

    /** パス */
    private TeamPathsDTO paths;
}
