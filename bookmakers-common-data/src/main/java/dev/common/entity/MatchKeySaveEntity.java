package dev.common.entity;

import lombok.Data;

@Data
public class MatchKeySaveEntity {

	/** マッチキー */
	private String matchKey;

	/** データカテゴリ */
	private String dataCategory;

	/** ホームチーム */
	private String homeTeamName;

	/** アウェーチーム */
	private String awayTeamName;

	/** 論理フラグ */
	private String logicFlg;

}
