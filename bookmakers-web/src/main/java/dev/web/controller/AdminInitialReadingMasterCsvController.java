package dev.web.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_a021.InitialReadingMasterCsvResponse;
import dev.web.api.bm_a021.InitialReadingMasterCsvService;
import dev.web.api.bm_a021.InitialReadingMasterCsvUpdateRequest;
import dev.web.api.bm_a021.InitialReadingMasterCsvUpdateResponse;

/**
 * マスタ登録CSV初回読み込み確認API
 */
@RestController
@RequestMapping("/api/admin")
public class AdminInitialReadingMasterCsvController {

	@Autowired
	private InitialReadingMasterCsvService initialReadingMasterCsvService;

	/**
	 * マスタ登録CSV初回読み込み状態を返却
	 */
	@GetMapping("/master/initial/csv")
	public InitialReadingMasterCsvResponse getStatus(String masterName) {
		return this.initialReadingMasterCsvService.getStatus(masterName);
	}

	/**
	 * モーダルで確認した対象の initial_flg を一括で 1 に更新
	 */
	@PostMapping("/master/initial/csv/update-status")
	public InitialReadingMasterCsvUpdateResponse updateStatus(
			@RequestBody InitialReadingMasterCsvUpdateRequest request) {
		return this.initialReadingMasterCsvService.updateStatus(request);
	}
}
