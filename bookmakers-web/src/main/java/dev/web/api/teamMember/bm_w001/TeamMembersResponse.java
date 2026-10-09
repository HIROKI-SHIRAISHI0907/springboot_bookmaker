package dev.web.api.teamMember.bm_w001;

import java.util.ArrayList;
import java.util.List;

import dev.web.api.dashboard.teamMemberDTO.TeamMemberDTO;
import lombok.Data;

/**
 * GET /v1/api/team-results/members のレスポンス
 * @author shiraishitoshio
 */
@Data
public class TeamMembersResponse {

	private String country;

	private String league;

	private String team;

	/** 情報の最新日（yyyy-MM-dd。無ければ null） */
	private String latestInfoDate;

	/** 負傷者数 */
	private int injuredCount;

	/** GK → DF → MF → FW → その他、背番号順 */
	private List<TeamMemberDTO> members = new ArrayList<>();
}
