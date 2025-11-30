package dev.web.api.bm_w011;

import lombok.Data;

/**
 * リーグ情報データ
 */
@Data
public class LeagueInfoDTO {

	/** 名前 */
    private String name;

    /** チーム件数 */
    private int teamCount;

    /** パス */
    private String path; // "/<country>/<league>"
}
