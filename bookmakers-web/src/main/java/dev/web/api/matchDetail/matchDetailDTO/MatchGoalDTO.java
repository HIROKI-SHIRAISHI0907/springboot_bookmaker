package dev.web.api.matchDetail.matchDetailDTO;

import lombok.Data;

/**
 * ゴール（スコアが増えたところから求める）
 * @author shiraishitoshio
 */
@Data
public class MatchGoalDTO {

	private Double minute;

	private String label;

	/** H / A */
	private String side;

	/** ゴール後のスコア */
	private int homeScore;

	private int awayScore;
}
