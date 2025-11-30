package dev.web.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import dev.web.api.bm_w011.LeagueFlatItemResponse;
import dev.web.api.bm_w011.LeagueGroupedResponse;
import dev.web.api.bm_w011.LeaguesService;
import dev.web.api.bm_w011.TeamDetailResponse;
import dev.web.api.bm_w011.TeamsInLeagueResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class LeaguesController {

    private final LeaguesService service;

    /** フラット一覧（必要なら） GET /api/leagues */
    @GetMapping("/leagues")
    public List<LeagueFlatItemResponse> getLeaguesFlat() {
        return service.getLeaguesFlat();
    }

    /** ハンバーガーメニュー用 GET /api/leagues/grouped */
    @GetMapping("/leagues/grouped")
    public List<LeagueGroupedResponse> getLeaguesGrouped() {
        return service.getLeaguesGrouped();
    }

    /** GET /api/{country}/{league}/leagues - チーム一覧 */
    @GetMapping("/{country}/{league}/leagues")
    public TeamsInLeagueResponse getTeamsInLeague(
            @PathVariable String country,
            @PathVariable String league) {
        // Spring が decode 済みなのでそのまま Node 側と同じ文字列になる想定
        return service.getTeamsInLeague(country, league);
    }

    /** GET /api/{country}/{league}/{team}/leagues - チーム詳細 */
    @GetMapping("/{country}/{league}/{team}/leagues")
    public TeamDetailResponse getTeamDetail(
            @PathVariable String country,
            @PathVariable String league,
            @PathVariable("team") String teamEnglish) {

        TeamDetailResponse res = service.getTeamDetail(country, league, teamEnglish);
        if (res == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "team not found");
        }
        return res;
    }
}
