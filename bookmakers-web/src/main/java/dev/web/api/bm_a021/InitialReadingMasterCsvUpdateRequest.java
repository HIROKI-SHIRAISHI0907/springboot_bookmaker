package dev.web.api.bm_a021;

import java.util.List;

import lombok.Data;

@Data
public class InitialReadingMasterCsvUpdateRequest {

	/** マスタ名 */
	private String masterName;

	/** 更新対象一覧 */
	private List<InitialReadingMasterCsvUpdateStatusTargetRequest> targets;

}
