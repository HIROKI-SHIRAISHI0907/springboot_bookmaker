package dev.web.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_w021.EcsTaskProgressResponse;
import dev.web.api.bm_w021.EcsTaskProgressService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/ecs/tasks")
@RequiredArgsConstructor
public class EcsTaskProgressController {

    private final EcsTaskProgressService service;

    /**
     * ECSタスクの進捗率を取得する（CloudWatch Logsの最新[PROGRESS]ログから算出）。
     * <pre>
     * GET /api/ecs/tasks/latest/progress
     * </pre>
     */
    @GetMapping("/latest/progress")
    public ResponseEntity<EcsTaskProgressResponse> getProgress() {
    	return ResponseEntity.ok(service.getLatestProgress());
    }
}
