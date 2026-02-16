package dev.web.api.bm_w005;

import lombok.Data;

@Data
public class GameMatchesRequest {

	/** チーム名（英語）*/
	private String teamEnglish;

	/** チームハッシュ */
	private String teamHash;

}
