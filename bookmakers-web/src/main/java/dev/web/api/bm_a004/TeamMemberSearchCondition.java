package dev.web.api.bm_a004;

import lombok.Data;

/**
 * TeamMemberSearchCondition
 * @author shiraishitoshio
 *
 */
@Data
public class TeamMemberSearchCondition {

	/** 国 */ //設定あり
	private String country;

	/** リーグ */ //設定あり
	private String league;

	/** チーム(暫定データ) */ //設定あり
	private String team;

	/** 選手名 */ //設定あり
	private String member;

	/** ポジション(今までの変化も記録) */ //設定あり
	private String position;

	/** 削除フラグ */ //設定あり
	private String delFlg;

}
