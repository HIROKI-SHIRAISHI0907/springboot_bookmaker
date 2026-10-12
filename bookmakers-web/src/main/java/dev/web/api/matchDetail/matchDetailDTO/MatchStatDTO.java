package dev.web.api.matchDetail.matchDetailDTO;

import lombok.Data;

/**
 * スタッツ比較の1行（最新の行の値）
 * @author shiraishitoshio
 */
@Data
public class MatchStatDTO {

	/** 例: xg, shots */
	private String key;

	/** 例: ゴール期待値（xG） */
	private String label;

	/** 表示用（DB の値そのまま。例: "55%"、"34%（31/90）"） */
	private String home;

	private String away;

	/** バーの長さ用の数値（読めなければ null） */
	private Double homeValue;

	private Double awayValue;
}
