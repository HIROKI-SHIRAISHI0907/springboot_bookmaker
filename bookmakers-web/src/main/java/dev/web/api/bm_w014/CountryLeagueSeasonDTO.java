package dev.web.api.bm_w014;

import lombok.Data;

/**
 * CountryLeagueSeasonDTO
 * @author shiraishitoshio
 *
 */
@Data
public class CountryLeagueSeasonDTO {

	/** 国 */
	private String country;

	/** リーグ */
	private String league;

	/** シーズン年 */
	private String seasonYear;

	/** パス */
	private String path;

	/** 削除フラグ*/
	private String delFlg;
}
