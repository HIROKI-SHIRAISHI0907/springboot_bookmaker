package dev.web.api.headToHead.headToHeadDTO;

import lombok.Data;

/**
 * 対戦成績の 1 試合（head_to_head_match の 1 行。team 視点）
 * @author shiraishitoshio
 */
@Data
public class HeadToHeadMatchDTO {

	/** シーズン */
	private String season;

	/** 国 */
	private String country;

	/** リーグ（大会） */
	private String league;

	/** ラウンド番号 */
	private Integer roundNo;

	/** 元のキー（「国: リーグ - ラウンド N」） */
	private String dataCategory;

	/** 試合時刻（日本時間 "yyyy-MM-dd HH:mm:ss"。分からなければ null） */
	private String matchTime;

	/** team がホームなら H、アウェーなら A */
	private String ha;

	/** ホームチーム */
	private String homeTeamName;

	/** アウェーチーム */
	private String awayTeamName;

	/** ホームの得点 */
	private Integer homeScore;

	/** アウェーの得点 */
	private Integer awayScore;

	/** team の結果 W / D / L（PK 決着は PK の勝敗） */
	private String result;

	/** PK 決着 */
	private boolean pk;

	/** PK 戦の得点（team 視点。PK 決着でなければ null） */
	private Integer pkGoalsFor;

	/** PK 戦の失点 */
	private Integer pkGoalsAgainst;

	/** 前半の得点・失点（team 視点。分からなければ null） */
	private Integer goalsFor1st;

	private Integer goalsAgainst1st;

	/** 先制 T: team / O: 相手 / N: 両者無得点 / U: 不明 */
	private String firstGoal;
}
