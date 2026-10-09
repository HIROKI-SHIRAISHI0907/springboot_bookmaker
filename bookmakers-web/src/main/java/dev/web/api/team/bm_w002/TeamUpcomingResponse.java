package dev.web.api.team.bm_w002;

import java.util.ArrayList;
import java.util.List;

import dev.web.api.dashboard.teamDTO.TeamUpcomingItemDTO;
import lombok.Data;

/**
 * GET /v1/api/team-upcoming のレスポンス
 * @author shiraishitoshio
 */
@Data
public class TeamUpcomingResponse {

	private String country;

	private String league;

	private String team;

	private boolean loggedIn;

	/** 何日先まで探したか */
	private int days;

	/** キックオフ順（items[0] が次節） */
	private List<TeamUpcomingItemDTO> items = new ArrayList<>();
}
