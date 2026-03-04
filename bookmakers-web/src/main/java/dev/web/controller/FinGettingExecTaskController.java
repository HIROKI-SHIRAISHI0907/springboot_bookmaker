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
 * Json作成タスク実行用
 * @author shiraishitoshio
 *
 */
@RestController
@RequestMapping("/api/admin/exec/task")
@RequiredArgsConstructor
public class FinGettingExecTaskController {

	private static final String BATCH_CODE = "B010";

	private final EcsBatchTaskRunner runner;

    /**
     * /fin-getting-json を叩いたら B010 のFargateタスクを起動する
     * @throws Exception
     */
    @PostMapping("/fin-getting-json")
    public ResponseEntity<StatResponseResource> execute(
    		@RequestBody StatRequestResource req) throws Exception {

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
