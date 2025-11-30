package dev.web.api.bm_w011;

import lombok.Data;

/**
 * TeamDetailResponse.paths 用
 */
@Data
public class TeamPathsDTO {

	/** リーグページ */
    private String leaguePage; // "/<country>/<league>"

    /** APIパス */
    private String apiSelf;    // "/api/leagues/<country>/<league>/<english>"
}
