package dev.web.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_a012.FinGettingRequest;
import dev.web.api.bm_a012.FinGettingService;
import dev.web.api.bm_w013.StatResponseResource;
import dev.web.batch.EcsBatchTaskRunner;
import dev.web.batch.EcsScrapeTaskRunner;
import lombok.RequiredArgsConstructor;

/**
 * Json作成タスク実行用
 * @author shiraishitoshio
 *
 */
@RestController
@RequestMapping("/api/admin/make")
@RequiredArgsConstructor
public class FinGettingExecTaskJsonController {

	private final EcsScrapeTaskRunner runService;

	private final EcsBatchTaskRunner runner;

    private final FinGettingService service;

    private static final String BATCH_CODE = "B010";

    /**
     * /fin-getting-json を叩いたら B010 のFargateタスクを起動する
     * @throws Exception
     */
    @PostMapping("/fin-getting-json")
    public ResponseEntity<StatResponseResource> execute(
    		@RequestBody FinGettingRequest req) throws Exception {

    	// JSONをupload
    	String s3Key = service.convertAndUpload(req);

    	// スクレイピングを行う
    	String taskArn1 = runService.runScrape(BATCH_CODE, Map.of(), true);

    	// 進捗管理
        service.getProgress();

    	 // 必要ならリクエスト内容を env で渡す（nullは入れない）
        Map<String, String> env = new HashMap<>();
        // 例: env.put("COUNTRY", req.getCountry());
        // 例: env.put("LEAGUE", req.getLeague());

        String taskArn2 = runner.runBatch(BATCH_CODE, env);
    	StatResponseResource res = new StatResponseResource();
        // あなたのDTO設計に合わせて詰めてOK
        res.setReturnCd("ACCEPTED");
        // resに taskArn を入れられるなら入れるのがおすすめ（進捗追跡できる）
        res.setTaskArn(taskArn2);

        return ResponseEntity.ok(res);
    }
}
