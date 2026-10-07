package dev.web.api.dashboard.bm_w005;

import dev.web.api.dashboard.bm_w002.DashboardForecastDTO;
import dev.web.api.dashboard.teamDTO.DashboardTeamDTO;
import lombok.Data;

/**
 * これからの1試合
 * @author shiraishitoshio
 *
 */
@Data
public class DashboardUpcomingMatchDTO {

	/** キックオフ（ISO-8601） */
	private String kickoff;

	private String country;

	private String league;

	/** 「国 / リーグ」 */
	private String leagueLabel;

	/** 「ラウンド N」（無ければ null） */
	private String roundLabel;

	private DashboardTeamDTO home;

	private DashboardTeamDTO away;

	private DashboardForecastDTO forecast;
}
