package dev.web.api.bm_a021;

import java.util.List;

import lombok.Data;

@Data
public class InitialReadingMasterCsvUpdateResponse {

	/** メッセージ */
	private String message;

	/** 更新件数 */
	private int updateCount;

	/** 更新対象 */
	private List<InitialReadingMasterCsvUpdateTargetRequest> updatedTargets;
}
