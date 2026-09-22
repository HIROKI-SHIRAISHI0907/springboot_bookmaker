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
 * Futureタスク実行用
 * @author shiraishitoshio
 *
 */
@RestController
@RequestMapping("/api/admin/exec/task")
@RequiredArgsConstructor
public class FutureExecTaskController {

	private static final String BATCH_CODE = "B005";

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

		// 必要ならリクエスト内容を env で渡す（nullは入れない）
        Map<String, String> env = new HashMap<>();
        // 例: env.put("COUNTRY", req.getCountry());
        // 例: env.put("LEAGUE", req.getLeague());

        String taskArn = runner.runBatch(BATCH_CODE, env);

        StatResponseResource res = new StatResponseResource();
        // あなたのDTO設計に合わせて詰めてOK
        res.setReturnCd("ACCEPTED");
        // resに taskArn を入れられるなら入れるのがおすすめ（進捗追跡できる）
        res.setTaskArn(taskArn);

        return ResponseEntity.ok(res);
	}
}