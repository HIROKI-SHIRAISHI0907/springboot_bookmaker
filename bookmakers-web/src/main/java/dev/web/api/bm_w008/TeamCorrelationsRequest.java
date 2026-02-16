package dev.web.api.bm_w008;

import lombok.Data;

@Data
public class TeamCorrelationsRequest {

	/** チーム名（英語）*/
	private String teamEnglish;

	/** チームハッシュ */
	private String teamHash;

}
