// dev/web/api/bm_w011/LeagueFlatItemResponse.java
package dev.web.api.bm_w011;

import lombok.Data;

/**
 * GET /api/leagues (フラット一覧用)
 */
@Data
public class LeagueFlatItemResponse {

	/** 国 */
    private String country;

    /** リーグ */
    private String league;

    /** チーム件数 */
    private int teamCount;

    /** 国,リーグパス */
    private String path; // "/<country>/<league>"
}
