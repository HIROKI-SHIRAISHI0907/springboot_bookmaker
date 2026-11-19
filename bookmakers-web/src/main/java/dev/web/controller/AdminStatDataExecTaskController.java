package dev.web.controller;

import java.util.HashMap;
import java.util.Map;

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
 * B006 統計CSVデータ取り入れ実行（2026-11-20 追加）
 *
 * <p>POST /api/admin/exec/task/stat-data を叩いたら、統計用プロジェクト bookmakers-app のタスクを
 *    BM_JOB=B006 で起動する（ecs.job.B006 = stats-backend / bookmakers-app）。</p>
 * <p>以前は管理画面の B006 ボタンが /stat-csv（B011 統計CSVデータ生成。bookmakers-batch）を呼んでいた。</p>
 */
@RestController
@RequestMapping("/api/admin/exec/task")
@RequiredArgsConstructor
public class AdminStatDataExecTaskController {

	private final EcsBatchTaskRunner runner;

	@PostMapping("/stat-data")
	public ResponseEntity<StatResponseResource> execute(@RequestBody(required = false) StatRequestResource req) {
		Map<String, String> env = new HashMap<>();
		String taskArn = runner.runBatch("B006", env);

		StatResponseResource res = new StatResponseResource();
		res.setReturnCd("ACCEPTED");
		res.setTaskArn(taskArn);
		return ResponseEntity.ok(res);
	}
}
