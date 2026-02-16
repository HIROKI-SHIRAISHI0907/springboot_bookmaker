package dev.web.api.bm_w010;

import lombok.Data;

@Data
public class TeamStatsRequest {

	/** チーム名（英語）*/
	private String teamEnglish;

	/** チームハッシュ */
	private String teamHash;

}
