package dev.web.api.team.bm_w001;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * チームの過去の結果（GET /v1/api/team-results）
 * @author shiraishitoshio
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TeamResultsResponse {

	private String country;

	private String league;

	private String team;

	/** 今季（surface_overview_match のシーズン。分からなければ null） */
	private String season;

	/** 直近のラウンドから古い順へ（items[0] が一番新しいラウンド） */
	private List<TeamResultItemDTO> items;

	private int wins;

	private int draws;

	private int losses;

	private int goalsFor;

	private int goalsAgainst;
}
