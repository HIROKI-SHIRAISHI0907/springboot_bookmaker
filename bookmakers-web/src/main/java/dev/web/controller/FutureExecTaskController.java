package dev.web.controller;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_w013.StatRequestResource;
import dev.web.api.bm_w013.StatResponseResource;
import dev.web.batch.EcsBatchTaskRunner;
import lombok.RequiredArgsConstructor;

/**
 * Futureタスク実行用
 * @author shiraishitoshio
 *
 */
@RestController
@RequestMapping("/api/admin/exec/task")
@RequiredArgsConstructor
public class FutureExecTaskController {

	private static final Set<String> VALID_RUN_MODES = Set.of("WEEK", "NEXT_DAY_ONLY", "SPECIFIC_DATE");

	private final EcsBatchTaskRunner runner;

	/**
	 * /future を叩いたら B005 のFargateタスクを起動する。
	 *
	 * runMode:
	 *   WEEK（省略時）  : 従来通り、今日から1週間分を取得
	 *   NEXT_DAY_ONLY  : 翌日の1日分だけを取得
	 *   SPECIFIC_DATE  : targetDate(YYYY-MM-DD) で指定した1日だけを取得（過去日・未来日どちらも可）
	 */
	@PostMapping("/future")
	public ResponseEntity<StatResponseResource> execute(@RequestBody(required = false) StatRequestResource req) {

		String runMode = (req != null && req.getRunMode() != null && !req.getRunMode().isBlank())
				? req.getRunMode().trim().toUpperCase()
				: "WEEK";

		if (!VALID_RUN_MODES.contains(runMode)) {
			StatResponseResource errRes = new StatResponseResource();
			errRes.setReturnCd("INVALID_RUN_MODE");
			return ResponseEntity.badRequest().body(errRes);
		}

		String targetDate = (req != null) ? req.getTargetDate() : null;

		if ("SPECIFIC_DATE".equals(runMode)) {
			if (targetDate == null || targetDate.isBlank()) {
				StatResponseResource errRes = new StatResponseResource();
				errRes.setReturnCd("TARGET_DATE_REQUIRED");
				return ResponseEntity.badRequest().body(errRes);
			}
			try {
				LocalDate.parse(targetDate);
			} catch (DateTimeParseException e) {
				StatResponseResource errRes = new StatResponseResource();
				errRes.setReturnCd("TARGET_DATE_INVALID");
				return ResponseEntity.badRequest().body(errRes);
			}
		}

		// ECSタスクへ渡す環境変数（nullは入れない）
		Map<String, String> env = new HashMap<>();
		env.put("RUN_MODE", runMode);
		if ("SPECIFIC_DATE".equals(runMode)) {
			env.put("TARGET_DATE", targetDate);
		}

		String taskArn = runner.runBatch("B005", env);

		StatResponseResource res = new StatResponseResource();
		res.setReturnCd("ACCEPTED");
		res.setTaskArn(taskArn);

		return ResponseEntity.ok(res);
	}
}