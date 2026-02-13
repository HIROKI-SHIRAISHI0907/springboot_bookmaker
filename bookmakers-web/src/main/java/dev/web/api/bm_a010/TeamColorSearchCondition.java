package dev.web.api.bm_a010;

import lombok.Data;

/**
 * CountryLeagueSeasonSearchCondition
 * @author shiraishitoshio
 *
 */
@Data
public class TeamColorSearchCondition {

	/** ID */
	private String id;

	/** 国 */
	private String country;

	/** リーグ */
	private String league;

	/** チーム */
	private String team;

	/** メインカラーコード */
	private String teamColorMainHex;

	/** サブカラーコード */
	private String teamColorSubHex;

}
