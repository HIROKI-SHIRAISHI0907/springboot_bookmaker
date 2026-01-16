package dev.web.api.bm_u003;

import java.util.List;

import lombok.Data;

/**
 * CountryScope
 * @author shiraishitoshio
 *
 */
@Data
public class LeagueScope {

	/** 国 */
	private String country;

	/** リーグ */
	private List<String> leagues;

}
