package dev.web.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_w021.EcsScrapeTaskProgressResponse;
import dev.web.api.bm_w021.EcsScrapeTaskProgressService;
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

    /** 手動実行 */
    @PostMapping("/ecs/{batchCode}/run")
    public ResponseEntity<Map<String, String>> run(@PathVariable String batchCode) {
    	String taskArn = runService.runScrape(batchCode, Map.of(), true);
        return ResponseEntity.accepted().body(Map.of("taskArn", taskArn));
    }

}
