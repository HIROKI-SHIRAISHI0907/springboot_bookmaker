package dev.web.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_w010.EachStatsService;
import dev.web.api.bm_w010.TeamStatsResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class EachStatsController {

    private final EachStatsService service;

    /**
     * GET /api/{country}/{league}/{team}/stats
     *
     * frontend: fetchTeamFeatureStats(country, league, teamSlug)
     */
    @GetMapping("/{country}/{league}/{team}/stats")
    public TeamStatsResponse getStats(
            @PathVariable String country,
            @PathVariable String league,
            @PathVariable("team") String teamSlug) {

        // Spring が decodeURIComponent 相当をやってくれるので、
        // Node 側と同じく生の文字列で OK
        return service.getStats(country, league, teamSlug);
    }
}
