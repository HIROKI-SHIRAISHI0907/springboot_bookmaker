package dev.web.api.matchDetail.matchDetailDTO;

import lombok.Data;

/**
 * 順位の変動表の1行（どちらかの順位が変わったところ。最初の行は試合開始時点）
 * @author shiraishitoshio
 */
@Data
public class MatchRankChangeDTO {

	private Double minute;

	private String label;

	private Integer homeRank;

	private Integer awayRank;

	/** 前の行からの変化（マイナス = 順位が上がった。最初の行は null） */
	private Integer homeDelta;

	private Integer awayDelta;

	/** そのときのスコア */
	private Integer homeScore;

	private Integer awayScore;
}
