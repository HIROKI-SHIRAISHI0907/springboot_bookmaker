package dev.web.api.bm_w012;

import lombok.Data;

@Data
public class RankHistoryRequest {

	/** チーム名（英語）*/
	private String teamEnglish;

	/** チームハッシュ */
	private String teamHash;

}
