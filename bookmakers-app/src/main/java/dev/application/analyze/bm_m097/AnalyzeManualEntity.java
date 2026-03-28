package dev.application.analyze.bm_m097;

import lombok.Data;

@Data
public class AnalyzeManualEntity {

	/** 通番 */
	private Long id;

	/** 対戦チームカテゴリ */
	private String gameCategory;

	/** 試合時間 */
	private String times;

	/** ホームチーム */
	private String homeTeamName;

	/** アウェーチーム */
	private String awayTeamName;

	/** マッチID */
	private String matchId;

}
