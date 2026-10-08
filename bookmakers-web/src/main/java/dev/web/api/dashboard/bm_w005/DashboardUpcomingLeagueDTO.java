package dev.web.api.dashboard.bm_w005;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * これからの試合（リーグごと）
 * @author shiraishitoshio
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardUpcomingLeagueDTO {

	/** 「国 / リーグ」 */
	private String leagueLabel;

	private String country;

	private String league;

	/** このリーグの試合数（期間内の全部。matches は先頭の数件だけのことがある） */
	private int totalCount;

	/** 試合（キックオフ順） */
	private List<DashboardUpcomingMatchDTO> matches;
}
