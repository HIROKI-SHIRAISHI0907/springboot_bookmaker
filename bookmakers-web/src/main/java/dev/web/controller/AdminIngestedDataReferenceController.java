package dev.web.controller;

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
public class AdminIngestedDataReferenceController {

	private final IngestedDataService service;

	/**
     * 投入済みデータ参照（期間＋テーブル指定）
     *
     * GET /api/admin/ingested
     *   ?country=日本
     *   &onlyNeedsAttention=true   … 未来・終了済・統計詳細のいずれかが無いもの（以前からの指定。下の 3 つ全部と同じ）
     *   &missingFuture=true        … 未来データが無いもの（2026-11 追加）
     *   &missingFinished=true      … 終了済データが無いもの（2026-11 追加）
     *   &missingDetail=true        … 統計詳細データが無いもの（2026-11 追加。複数選んだら「いずれかが無い」）
     *   &limit=100
     *   &offset=0
     */
    @GetMapping("/admin/ingested")
    public IngestedDataReferenceResponse getIngestedData(
    		@RequestParam(required = false) String country,

            @RequestParam(required = false) Boolean onlyNeedsAttention,

            // 2026-11 追加: それぞれ「無いもの」で絞る（選んだもののいずれかが無いもの）
            @RequestParam(required = false) Boolean missingFuture,
            @RequestParam(required = false) Boolean missingFinished,
            @RequestParam(required = false) Boolean missingDetail,

            @RequestParam(defaultValue = "100")
            int limit,

            @RequestParam(defaultValue = "0")
            int offset) {

    	IngestedDataReferenceRequest req = new IngestedDataReferenceRequest();
    	req.setCountry(country);
    	req.setOnlyMissingFinishedOrFuture(Boolean.TRUE.equals(onlyNeedsAttention));
    	req.setMissingFuture(Boolean.TRUE.equals(missingFuture));
    	req.setMissingFinished(Boolean.TRUE.equals(missingFinished));
    	req.setMissingDetail(Boolean.TRUE.equals(missingDetail));
        req.setLimit(limit);
        req.setOffset(offset);

        return service.search(req);
    }

}
