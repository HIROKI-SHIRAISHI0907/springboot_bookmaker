package dev.web.api.dashboard.bm_w004;

import lombok.Data;

/**
 * 数字カードのレスポンス（GET /v1/api/dashboard/summary）
 * @author shiraishitoshio
 *
 */
@Data
public class DashboardSummaryResponse {

	/** ライブ中の試合数 */
	private int liveCount;

	/** ライブ中のリーグ数 */
	private int liveLeagueCount;

	/** ライブのうちゴールが多い見込みの試合数 */
	private int highGoalLiveCount;

	/** 今日（JST）のゴール数（ライブ＋終了） */
	private int goalsToday;

	/** 今日の1試合平均ゴール数（試合が無ければ null） */
	private Double avgGoalsToday;

	/** 今日このあとキックオフする試合数 */
	private int upcomingTodayCount;

	/** 次の試合（無ければ null） */
	private String nextKickoff;

	private String nextHomeTeam;

	private String nextAwayTeam;

	/** 今日終了した試合数 */
	private int finishedCount;

	/** 番狂わせ（順位が 5 以上下のチームが勝った試合） */
	private int upsetCount;

	/** 引き分け */
	private int drawCount;
}
