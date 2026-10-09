package dev.web.api.team.bm_w001;

import lombok.Data;

/**
 * チームの過去の1試合（Repository の読み取り結果）
 * <ul>
 *   <li>OVERVIEW: surface_overview_match（opponent・ha・goalsFor/Against・result がチーム視点で入っている）</li>
 *   <li>STATIC: static_data の終了済の行（ホーム・アウェーのまま。Service でチーム視点に直す）</li>
 * </ul>
 * @author shiraishitoshio
 */
@Data
public class TeamResultRow {

	/** OVERVIEW / STATIC */
	private String source;

	private int roundNo;

	// OVERVIEW
	private String opponent;
	private String ha;
	private Integer goalsFor;
	private Integer goalsAgainst;
	private String result;
	private boolean pk;
	private Integer pkGoalsFor;
	private Integer pkGoalsAgainst;

	// STATIC
	private String homeTeamName;
	private String awayTeamName;
	private String homeScore;
	private String awayScore;

	/** 試合時刻（JST の文字列） */
	private String matchTime;
}
