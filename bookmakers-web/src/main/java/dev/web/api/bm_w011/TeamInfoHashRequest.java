package dev.web.api.bm_w011;

import lombok.Data;

@Data
public class TeamInfoHashRequest {

	/** チーム名（英語）*/
	private String teamEnglish;

	/** チームハッシュ */
	private String teamHash;

}
