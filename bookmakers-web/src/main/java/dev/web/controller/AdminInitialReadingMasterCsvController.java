package dev.web.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_a021.InitialReadingMasterCsvResponse;
import dev.web.api.bm_a021.InitialReadingMasterCsvService;

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
	public InitialReadingMasterCsvResponse getStatus(
			@RequestParam String masterName,
			@RequestParam String country,
			@RequestParam String league) {
		return this.initialReadingMasterCsvService.getStatus(
				masterName, country, league);
	}
}
