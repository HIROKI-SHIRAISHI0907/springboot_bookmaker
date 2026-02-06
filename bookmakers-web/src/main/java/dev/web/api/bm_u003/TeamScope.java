package dev.web.api.bm_u003;

import java.util.List;

import lombok.Data;

/**
 * TeamScope
 * @author shiraishitoshio
 *
 */
@Data
public class TeamScope {

	/** 国 */
	private String country;

	/** リーグ */
	private String league;

	/** チーム */
	private List<String> teams;

}
