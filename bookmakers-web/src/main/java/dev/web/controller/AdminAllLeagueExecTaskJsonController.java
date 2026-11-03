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
import dev.web.api.dashboard.auth.DashboardAuthResolver;
import dev.web.batch.EcsBatchTaskRunner;
import dev.web.repository.master.SetupPendingRepository;
import lombok.RequiredArgsConstructor;

/**
 * AllLeagueJsonタスク実行用
 * @author shiraishitoshio
 *
 */
@RestController
@RequestMapping("/api/admin/exec/task")
@RequiredArgsConstructor
public class AdminAllLeagueExecTaskJsonController {

    private final EcsBatchTaskRunner runner;

    /** 「対象データアップロード実行」待ちを解消する（2026-11-03 追加） */
    private final SetupPendingRepository setupPendingRepository;

    private final DashboardAuthResolver authResolver;

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

        // B009 を起動できたら、リーグ所属チームの変更による「対象データアップロード実行」待ちを解消する
        // （起動に失敗したときは runBatch が例外を投げるので、ここには来ない＝待ちのまま）
        String operator = authResolver.currentUserId();
        setupPendingRepository.resolve(SetupPendingRepository.SCRAPE_TARGET_UPLOAD,
                operator == null || operator.isBlank() ? "ADMIN" : operator);

        StatResponseResource res = new StatResponseResource();
        // あなたのDTO設計に合わせて詰めてOK
        res.setReturnCd("ACCEPTED");
        // resに taskArn を入れられるなら入れるのがおすすめ（進捗追跡できる）
        res.setTaskArn(taskArn);

        return ResponseEntity.ok(res);
    }
}
