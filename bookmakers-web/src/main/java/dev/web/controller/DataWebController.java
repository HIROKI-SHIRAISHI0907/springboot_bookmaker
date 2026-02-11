package dev.web.controller;

import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.common.entity.DataEntity;
import dev.web.api.bm_a001.DataRequest;
import dev.web.api.bm_a001.DataResponse;
import dev.web.api.bm_a001.DataService;
import lombok.RequiredArgsConstructor;

/**
 * Data取得用
 * @author shiraishitoshio
 *
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class DataWebController {

    private final DataService service;

    /**
     * dataを更新する。
     *
     * PATCH /api/data
     */
    @PatchMapping("/data")
    public ResponseEntity<DataResponse> patchData(
            @RequestBody DataRequest req) {

        DataResponse res = service.update(req);

        HttpStatus status = switch (res.getResponseCode()) {
            case "200" -> HttpStatus.OK;                    // SUCCESS
            case "400" -> HttpStatus.BAD_REQUEST;           // 必須不足
            case "404" -> HttpStatus.NOT_FOUND;             // NOT_FOUND
            case "409" -> HttpStatus.CONFLICT;              // LINK_ALREADY_USED
            default -> HttpStatus.INTERNAL_SERVER_ERROR;    // ERROR
        };

        return ResponseEntity.status(status).body(res);
    }

    /**
     * data を1件取得する。
     *
     * GET /api/data
     */
    @GetMapping("/data")
    public ResponseEntity<Optional<DataEntity>> find(@RequestBody DataRequest req) {
        return ResponseEntity.ok(service.find(req));
    }

}
