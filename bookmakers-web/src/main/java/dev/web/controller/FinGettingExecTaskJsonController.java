package dev.web.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_a012.FinGettingAsync;
import dev.web.api.bm_a012.FinGettingRequest;
import dev.web.api.bm_a012.FinGettingService;
import dev.web.api.bm_w013.StatResponseResource;
import dev.web.batch.EcsScrapeTaskRunner;
import lombok.RequiredArgsConstructor;

/**
 * Json作成タスク実行用
 * @author shiraishitoshio
 *
 */
@RestController
@RequestMapping("/api/admin/exec/task")
@RequiredArgsConstructor
public class FinGettingExecTaskJsonController {

	private static final String BATCH_CODE = "B010";

	private final FinGettingAsync asyncRunner;

	private final EcsScrapeTaskRunner runService;

    private final FinGettingService service;

    /**
     * /fin-getting-json を叩いたら B010 のFargateタスクを起動する
     * @throws Exception
     */
    @PostMapping("/fin-getting-json")
    public ResponseEntity<StatResponseResource> execute(
    		@RequestBody FinGettingRequest req) throws Exception {

    	// JSONをupload
    	service.convertAndUpload(req);

    	// スクレイピングを行う
    	String taskArn1 = runService.runScrape(BATCH_CODE, Map.of(), true);

    	// バッチ処理を実行（スクレイピング完了後）
    	asyncRunner.waitScrapeAndRunBatch(BATCH_CODE);

    	StatResponseResource res = new StatResponseResource();
        // あなたのDTO設計に合わせて詰めてOK
        res.setReturnCd("ACCEPTED");
        // resに taskArn を入れられるなら入れるのがおすすめ（進捗追跡できる）
        res.setTaskArn(taskArn1);

        return ResponseEntity.accepted().body(res);
    }

}
