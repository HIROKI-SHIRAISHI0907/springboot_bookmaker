package dev.web.api.bm_a021;

import lombok.Data;

@Data
public class InitialReadingMasterCsvResponse {

	/** マスタ名 */
	private String masterName;

	/** 初回フラグ */
	private String chkFlg;

	/** メッセージ */
	private String message;

}
