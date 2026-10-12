package dev.web.api.matchDetail.matchDetailDTO;

import lombok.Data;

/**
 * 試合の流れの1点（同じ試合時間は最後の行）
 * @author shiraishitoshio
 */
@Data
public class MatchTimelinePointDTO {

	/** 試合時間（分。小数あり） */
	private Double minute;

	/** 表示用の試合時間（例: 23'、ハーフタイム） */
	private String label;

	private Integer homeScore;

	private Integer awayScore;

	private Double homeXg;

	private Double awayXg;

	private Integer homeShots;

	private Integer awayShots;

	private Integer homeRank;

	private Integer awayRank;
}
