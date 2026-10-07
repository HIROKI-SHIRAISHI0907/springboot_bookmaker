package dev.web.api.dashboard.teamDTO;

import lombok.Data;

/**
 * ビュー dashboard_team_rate の1行（チーム × H/A の今季の平均得点・失点とリーグ平均）
 * @author shiraishitoshio
 *
 */
@Data
public class DashboardTeamRateRow {
	private String country;
	private String league;
	private String season;
	private String team;
	/** H / A */
	private String ha;
	private int matchCount;
	private double avgGoalsFor;
	private double avgGoalsAgainst;
	private double leagueAvgGoalsFor;
	private double leagueAvgGoalsAgainst;
}
