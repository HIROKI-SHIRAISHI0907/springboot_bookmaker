package dev.web.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_a023.LoadedSlotsResult;
import dev.web.api.bm_a023.NoEcsSlotsRequest;
import dev.web.api.bm_a023.NoEcsSlotsResponse;
import dev.web.api.bm_a023.NoEcsSlotsS3Service;
import lombok.RequiredArgsConstructor;

/**
 * noEcsRun の JSON を S3 から取得して返す API
 */
@RestController
@RequestMapping("/api/admin/noecs")
@RequiredArgsConstructor
public class NoEcsSlotsController {

    private final NoEcsSlotsS3Service service;

    /**
     * POSTで取得
     */
    @PostMapping("/slots")
    public ResponseEntity<NoEcsSlotsResponse> getSlots(@RequestBody NoEcsSlotsRequest req) {
        LoadedSlotsResult loaded = service.loadSlotsJsonWithMeta(req);

        NoEcsSlotsResponse res = new NoEcsSlotsResponse();
        res.setBucket(loaded.getBucket());
        res.setKey(loaded.getKey());
        res.setFileName(loaded.getFileName());
        res.setBody(loaded.getBody());
        res.setMessage("OK");

        return ResponseEntity.ok(res);
    }

    /**
     * GETで取得
     * 画面テスト用
     */
    @GetMapping("/slots")
    public ResponseEntity<NoEcsSlotsResponse> getSlotsByQuery(
            @RequestParam String batchCode,
            @RequestParam(required = false) String day,
            @RequestParam(required = false) String fileName) {

        NoEcsSlotsRequest req = new NoEcsSlotsRequest();
        req.setBatchCode(batchCode);
        req.setFileName(fileName);

        if (day != null && !day.isBlank()) {
            req.setDay(java.time.LocalDate.parse(day));
        }

        LoadedSlotsResult loaded = service.loadSlotsJsonWithMeta(req);

        NoEcsSlotsResponse res = new NoEcsSlotsResponse();
        res.setBucket(loaded.getBucket());
        res.setKey(loaded.getKey());
        res.setFileName(loaded.getFileName());
        res.setBody(loaded.getBody());
        res.setMessage("OK");

        return ResponseEntity.ok(res);
    }
}
