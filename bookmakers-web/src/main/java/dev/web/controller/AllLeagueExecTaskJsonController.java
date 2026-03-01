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
 * AllLeagueJsonタスク実行用
 * @author shiraishitoshio
 *
 */
@RestController
@RequestMapping("/api/admin/exec/task")
@RequiredArgsConstructor
public class AllLeagueExecTaskJsonController {

    private final EcsBatchTaskRunner runner;

    /**
     * /all-league-scrape-master-json を叩いたら B009 のFargateタスクを起動する
     */
    @PostMapping("/all-league-scrape-master-json")
    public ResponseEntity<StatResponseResource> execute(@RequestBody StatRequestResource req) {

        // 必要ならリクエスト内容を env で渡す（nullは入れない）
        Map<String, String> env = new HashMap<>();
        // 例: env.put("COUNTRY", req.getCountry());
        // 例: env.put("LEAGUE", req.getLeague());

        String taskArn = runner.runBatch("B009", env);

        StatResponseResource res = new StatResponseResource();
        // あなたのDTO設計に合わせて詰めてOK
        res.setReturnCd("ACCEPTED");
        // resに taskArn を入れられるなら入れるのがおすすめ（進捗追跡できる）
        res.setTaskArn(taskArn);

        return ResponseEntity.ok(res);
    }
}
