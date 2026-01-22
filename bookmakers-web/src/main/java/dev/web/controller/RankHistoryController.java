package dev.web.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_w012.RankHistoryAPIService;
import dev.web.api.bm_w012.RankHistoryResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RankHistoryController {

    private final RankHistoryAPIService service;

    /**
     * GET /api/rank-history/{country}/{league}
     */
    @GetMapping("/rank-history/{country}/{league}")
    public RankHistoryResponse getRankHistory(
            @PathVariable String country,
            @PathVariable String league) {
        return service.getRankHistory(country, league);
    }
}
