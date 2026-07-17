package dev.web.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import dev.web.api.bm_w011.LeagueGroupedResponse;
import dev.web.api.bm_w011.LeaguesAPIService;
import dev.web.api.bm_w011.TeamDetailResponse;
import dev.web.api.bm_w011.TeamsInLeagueResponse;
import dev.web.jwt.JwtCurrentUserService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class LeaguesController {

    private final LeaguesAPIService service;
    private final JwtCurrentUserService jwtCurrentUserService;

    /** ハンバーガーメニュー用 GET /api/leagues/grouped */
    @GetMapping("/leagues/grouped")
    public List<LeagueGroupedResponse> getLeaguesGrouped(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {

        Long userId = jwtCurrentUserService.resolve(authorizationHeader).getUserId();
        return service.getLeaguesGrouped(userId);
    }

    /**
     * GET /api/leagues/{country}/{league}
     */
    @GetMapping("/leagues/{country}/{league}")
    public TeamsInLeagueResponse getTeamsInLeague(
            @PathVariable String country,
            @PathVariable String league,
            @RequestParam(required = false) String subLeague) {

        return service.getTeamsInLeague(country, league, subLeague);
    }

    /** GET /api/leagues/{teamEnglish}/{teamHash}/teamDetail */
    @GetMapping("/leagues/{teamEnglish}/{teamHash}/teamDetail")
    public TeamDetailResponse getTeamDetail(
            @PathVariable String teamEnglish,
            @PathVariable String teamHash) {

        TeamDetailResponse res = service.getTeamDetail(teamEnglish, teamHash);
        if (res == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "team not found");
        }
        return res;
    }
}
