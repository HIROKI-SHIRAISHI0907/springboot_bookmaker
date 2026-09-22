package dev.web.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_a009.EcsScrapeTaskProgressRequest;
import dev.web.api.bm_a009.EcsScrapeTaskProgressResponse;
import dev.web.api.bm_a009.EcsScrapeTaskProgressService;
import dev.web.batch.EcsScrapeTaskRunner;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/scrape")
@RequiredArgsConstructor
public class EcsScrapeTaskProgressController {

	private final EcsScrapeTaskProgressService service;
	private final EcsScrapeTaskRunner runService;

	/**
	 * 最新RUNNINGタスクの進捗
	 * GET /api/admin/scrape/ecs/{batchCode}/latest/progress
	 */
	@GetMapping("/ecs/{batchCode}/latest/progress")
	public ResponseEntity<EcsScrapeTaskProgressResponse> latest(@PathVariable String batchCode) {
		return ResponseEntity.ok(service.getLatestProgress(batchCode));
	}

	/**
	 * 指定タスクの進捗
	 * GET /api/admin/scrape/ecs/{batchCode}/tasks/{taskId}/progress
	 */
	@GetMapping("/ecs/{batchCode}/tasks/{taskId}/progress")
	public ResponseEntity<EcsScrapeTaskProgressResponse> byTask(
			@PathVariable String batchCode,
			@PathVariable String taskId) {
		return ResponseEntity.ok(service.getProgress(batchCode, taskId));
	}

	/**
	 * 手動実行
	 *
	 * runMode / targetDate は未来データ取得スクレイピング（B005）用のオプション項目。
	 * それ以外のバッチコードでは指定不要（従来通りbatchCdのみでOK）。
	 */
	@PostMapping("/ecs/run")
	public ResponseEntity<Map<String, String>> run(
			@RequestBody EcsScrapeTaskProgressRequest req) throws Exception {

		String normalizedBatchCd = service.normalizeBatchCode(req.getBatchCd());

		String runMode = (req.getRunMode() != null && !req.getRunMode().isBlank())
				? req.getRunMode().trim().toUpperCase()
				: null;

		if ("SPECIFIC_DATE".equals(runMode)
				&& (req.getTargetDate() == null || req.getTargetDate().isBlank())) {
			return ResponseEntity.badRequest().body(Map.of(
					"error", "TARGET_DATE_REQUIRED"
			));
		}

		Map<String, String> extraEnv = new HashMap<>();
		if (runMode != null) {
			extraEnv.put("RUN_MODE", runMode);
		}
		if ("SPECIFIC_DATE".equals(runMode) && req.getTargetDate() != null && !req.getTargetDate().isBlank()) {
			extraEnv.put("TARGET_DATE", req.getTargetDate().trim());
		}

		String taskArn = runService.runScrape(normalizedBatchCd, extraEnv, true);

		return ResponseEntity.accepted().body(Map.of(
				"taskArn", taskArn,
				"batchCd", normalizedBatchCd
		));
	}
}