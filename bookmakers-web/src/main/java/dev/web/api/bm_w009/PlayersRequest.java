package dev.web.api.bm_w009;

import lombok.Data;

@Data
public class PlayersRequest {

	/** チーム名（英語）*/
	private String teamEnglish;

	/** チームハッシュ */
	private String teamHash;

}
