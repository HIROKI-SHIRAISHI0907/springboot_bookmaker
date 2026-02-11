package dev.web.api.bm_a003;

import lombok.Data;

/**
 * CountryLeagueSearchCondition
 * @author shiraishitoshio
 *
 */
@Data
public class CountryLeagueSearchCondition {

	/** 国 */
	private String country;

	/** リーグ */
	private String league;

	/** チーム */
	private String team;

	/** リンク */
	private String link;

	/** 削除フラグ */
	private String delFlg;

}
