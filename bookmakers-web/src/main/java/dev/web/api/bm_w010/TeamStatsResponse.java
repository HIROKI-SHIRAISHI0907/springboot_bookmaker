// dev/web/api/bm_w010/TeamStatsResponse.java
package dev.web.api.bm_w010;

import lombok.Data;

/**
 * /api/stats/{country}/{league}/{team} のレスポンス
 *
 * {
 *   stats: { HOME: { scoreKey: { metric: csv or null } }, AWAY: ... },
 *   meta: { teamJa, situations, scores }
 * }
 */
@Data
public class TeamStatsResponse {

	/** 統計 */
    private RawStatsDTO stats;

    /** メタデータ */
    private TeamStatsMetaDTO meta;
}
