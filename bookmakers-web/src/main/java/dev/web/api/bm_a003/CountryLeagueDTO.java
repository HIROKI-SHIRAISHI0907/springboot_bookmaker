package dev.web.api.bm_a003;

import lombok.Data;

/**
 * CountryLeagueDTO
 * @author shiraishitoshio
 *
 */
@Data
public class CountryLeagueDTO {

	/** ID */
	private String id;

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
