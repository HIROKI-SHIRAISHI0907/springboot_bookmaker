package dev.web.api.upcomingDetail.upcomingDetailDTO;

import lombok.Data;

/**
 * 1 チームの今季の統計（試合のリーグ・シーズン。そこに試合が無ければチームの最新のリーグ・シーズン）
 * @author shiraishitoshio
 */
@Data
public class TeamGoalStatsDTO {

	private String team;

	/** 集計に使ったシーズン・国・リーグ（データが無ければ null） */
	private String season;

	private String country;

	private String league;

	/** 試合のリーグと別のリーグで集計したとき true（カップ戦など） */
	private boolean otherLeague;

	/** 統計がまだ無いので、終了済の試合（static_data）から集計した（2026-11-28 追加） */
	private boolean fromFinished;

	/** 全試合 */
	private GoalStatsDTO all;

	/** ホーム時 */
	private GoalStatsDTO home;

	/** アウェー時 */
	private GoalStatsDTO away;
}
