package dev.web.api.bm_w001;

import lombok.Data;

@Data
public class FutureMatchesRequest {

	/** チーム名（英語）*/
	private String teamEnglish;

	/** チームハッシュ */
	private String teamHash;

}
