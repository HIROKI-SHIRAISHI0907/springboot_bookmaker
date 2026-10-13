package dev.web.api.team.bm_w003;

import dev.web.api.matchDetail.matchVenueDTO.MatchVenueDTO;
import lombok.Data;

/**
 * GET /v1/api/team-venue のレスポンス
 */
@Data
public class TeamVenueResponse {

	private String team;

	private boolean found;

	/** ホームスタジアム（team_location_master に無ければ null） */
	private MatchVenueDTO venue;
}
