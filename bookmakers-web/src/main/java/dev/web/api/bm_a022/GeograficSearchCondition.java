package dev.web.api.bm_a022;

import lombok.Data;

/**
 * CountryLeagueSeasonSearchCondition
 * @author shiraishitoshio
 *
 */
@Data
public class GeograficSearchCondition {

	/** ID */
	private Integer id;

	/** 国 */
	private String country;

	/** チーム */
	private String teamName;

	/** ホーム都市 */
	private String homeCity;

}
