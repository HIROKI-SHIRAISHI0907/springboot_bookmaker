package dev.web.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_w010.EachStatsAPIService;
import dev.web.api.bm_w010.TeamStatsResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class EachStatsController {

    private final EachStatsAPIService service;

    /**
     * GET /api/stats/{country}/{league}/{team}
     *
     * frontend: fetchTeamFeatureStats(country, league, teamSlug)
     */
    @GetMapping("/{country}/{league}/{team}")
    public TeamStatsResponse getStats(
            @PathVariable String country,
            @PathVariable String league,
            @PathVariable("team") String teamSlug) {

        // Spring が decodeURIComponent 相当をやってくれるので、
        // Node 側と同じく生の文字列で OK
        return service.getStats(country, league, teamSlug);
    }
}
