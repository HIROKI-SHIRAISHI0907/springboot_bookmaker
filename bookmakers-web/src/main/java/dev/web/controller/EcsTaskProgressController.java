package dev.web.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
     * 最新RUNNINGタスクの進捗
     * GET /api/ecs/{batchCode}/latest/progress
     */
    @GetMapping("/ecs/{batchCode}/latest/progress")
    public ResponseEntity<EcsTaskProgressResponse> latest(@PathVariable String batchCode) {
        return ResponseEntity.ok(service.getLatestProgress(batchCode));
    }

    /**
     * 指定タスクの進捗
     * GET /api/ecs/{batchCode}/tasks/{taskId}/progress
     */
    @GetMapping("/ecs/{batchCode}/tasks/{taskId}/progress")
    public ResponseEntity<EcsTaskProgressResponse> byTask(
            @PathVariable String batchCode,
            @PathVariable String taskId) {
        return ResponseEntity.ok(service.getProgress(batchCode, taskId));
    }

}
