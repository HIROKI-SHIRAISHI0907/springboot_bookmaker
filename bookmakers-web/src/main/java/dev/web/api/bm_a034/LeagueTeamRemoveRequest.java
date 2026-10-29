package dev.web.api.bm_a034;

import java.util.List;

import lombok.Data;

/**
 * チームをリーグから外す（POST /v1/api/admin/league-team/remove。論理削除: del_flg = '1'）
 * @author shiraishitoshio
 */
@Data
public class LeagueTeamRemoveRequest {
	private String country;
	private String league;
	/** 外すチーム（country_league_master.id） */
	private List<Integer> teamIds;
}
