package dev.web.controller;

import java.time.OffsetDateTime;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_a011.IngestedDataReferenceRequest;
import dev.web.api.bm_a011.IngestedDataReferenceResponse;
import dev.web.api.bm_a011.IngestedDataService;
import lombok.RequiredArgsConstructor;

/**
 * 投入済みデータ参照コントローラー
 * @author shiraishitoshio
 *
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class IngestedDataReferenceController {

	private final IngestedDataService service;

	/**
     * 投入済みデータ参照（期間＋テーブル指定）
     *
     * GET /api/admin/ingested
     *   ?from=2026-02-12T00:00:00Z
     *   &to=2026-02-19T23:59:59Z
     *   &tables=FUTURE_MASTER,DATA
     *   &limit=100
     *   &offset=0
     */
    @GetMapping("/admin/ingested")
    public IngestedDataReferenceResponse getIngestedData(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime from,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime to,

            @RequestParam(defaultValue = "true")
            boolean includeFutureMaster,

            @RequestParam(defaultValue = "true")
            boolean includeData,

            @RequestParam(defaultValue = "100")
            int limit,

            @RequestParam(defaultValue = "0")
            int offset) {

    	IngestedDataReferenceRequest req = new IngestedDataReferenceRequest();
        req.setFrom(from);
        req.setTo(to);
        req.setIncludeFutureMaster(includeFutureMaster);
        req.setIncludeData(includeData);
        req.setLimit(limit);
        req.setOffset(offset);

        return service.search(req);
    }

}
