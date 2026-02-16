package dev.web.api.bm_w003;

import lombok.Data;

@Data
public class OverviewRequest {

	/** チーム名（英語）*/
	private String teamEnglish;

	/** チームハッシュ */
	private String teamHash;

}
