package dev.web.api.bm_a034;

import java.util.List;

import lombok.Data;

/**
 * リーグ所属チームの付け替え（POST /v1/api/admin/league-team/move）
 * @author shiraishitoshio
 */
@Data
public class LeagueTeamMoveRequest {
	private String country;
	private String fromLeague;
	private String toLeague;
	/** 付け替えるチーム（country_league_master.id） */
	private List<Integer> teamIds;
}
