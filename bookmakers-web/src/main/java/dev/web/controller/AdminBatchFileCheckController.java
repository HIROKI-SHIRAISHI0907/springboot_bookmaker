package dev.web.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_a016.BatchFileCheckService;
import dev.web.wrapper.BatchFileCheckResponseWrapper;

/**
 * バッチ実行前のS3ファイル状態確認API
 */
@RestController
@RequestMapping("/v1/api/admin")
public class AdminBatchFileCheckController {

	@Autowired
	private BatchFileCheckService batchFileCheckService;

	/**
	 * 各バッチの事前ファイル確認状態を返却
	 */
	@GetMapping("/file-checks")
	public BatchFileCheckResponseWrapper getFileChecks() {
		return this.batchFileCheckService.getAllStatuses();
	}
}
