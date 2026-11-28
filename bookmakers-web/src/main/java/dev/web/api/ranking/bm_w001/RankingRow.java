package dev.web.api.ranking.bm_w001;

import lombok.Data;

/**
 * ランキング用の 1 試合（チーム視点）。結果の列は surface_overview_match（または終了済の試合から作ったデータ）、
 * スタッツの列は match_team_snapshot_last（試合の最後の時点）。取れない値は null。
 * @author shiraishitoshio
 */
@Data
public class RankingRow {
	private String team;
	private int roundNo;

	// ---- 結果 ----
	private Integer goalsFor;
	private Integer goalsAgainst;
	private String result;
	private Integer points;
	private Integer goalsFor1st;
	private Integer goalsFor2nd;
	private Integer goalsAgainst1st;
	private Integer goalsAgainst2nd;
	private String firstGoal;
	private boolean flowKnown;
	private boolean everTrailed;

	// ---- スタッツ（自チーム） ----
	private Double possession;
	private Double xg;
	private Integer shots;
	private Integer shotsOnTarget;
	private Integer bigChances;
	private Integer corners;
	private Integer passSuccess;
	private Integer passTry;
	private Integer boxTouches;
	private Integer tackleSuccess;
	private Integer tackleTry;
	private Integer interceptions;
	private Integer clears;
	private Integer fouls;
	private Integer yellowCards;
	private Integer keeperSaves;
	private Integer offsides;

	// ---- スタッツ（相手） ----
	private Double xgAgainst;
	private Integer shotsAgainst;
	private Integer shotsOnTargetAgainst;
}
