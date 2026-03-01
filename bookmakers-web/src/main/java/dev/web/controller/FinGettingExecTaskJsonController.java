package dev.web.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_a012.FinGettingRequest;
import dev.web.api.bm_a012.FinGettingService;
import dev.web.api.bm_w013.StatResponseResource;
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

    private final FinGettingService service;

    /**
     * /fin-getting-json を叩いたら JSON作成する
     * @throws Exception
     */
    @PostMapping("/fin-getting-json")
    public ResponseEntity<StatResponseResource> execute(
    		@RequestBody FinGettingRequest req) throws Exception {

    	String s3Key = service.convertAndUpload(req);
    	StatResponseResource res = new StatResponseResource();
        res.setReturnCd("OK");     // 運用ルールに合わせて変更OK

        return ResponseEntity.ok(res);
    }
}
