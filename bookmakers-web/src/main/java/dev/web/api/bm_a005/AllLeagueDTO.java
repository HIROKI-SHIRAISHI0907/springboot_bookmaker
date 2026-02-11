package dev.web.api.bm_a005;

import lombok.Data;

/**
 * AllLeagueDTO
 * @author shiraishitoshio
 *
 */
@Data
public class AllLeagueDTO {

	/** ID */
	private String id;

	/** 国 */
	private String country;

	/** リーグ */
	private String league;

	/** 論理フラグ */
	private String logicFlg;

	/** 表示フラグ */
	private String dispFlg;

}
