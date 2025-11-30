// dev/web/api/bm_w010/TeamStatsMetaDTO.java
package dev.web.api.bm_w010;

import java.util.List;

import lombok.Data;

/**
 * スコアメタデータ
 */
@Data
public class TeamStatsMetaDTO {

	/** チーム */
    private String teamJa;

    /** 状況 */
    private List<String> situations;

    /** スコア */
    private List<String> scores;
}
