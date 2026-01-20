package dev.web.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_w017.CountryLeagueDTO;
import dev.web.api.bm_w017.CountryLeagueFindAllService;
import dev.web.api.bm_w017.CountryLeagueSearchCondition;
import dev.web.api.bm_w017.CountryLeagueSearchService;
import dev.web.api.bm_w017.CountryLeagueUpdateRequest;
import dev.web.api.bm_w017.CountryLeagueUpdateResponse;
import dev.web.api.bm_w017.CountryLeagueUpdateService;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class CountryLeagueController {

	private final CountryLeagueUpdateService service;

    private final CountryLeagueFindAllService allService;

    private final CountryLeagueSearchService searchService;

    /**
     * team_member_master の未登録項目を更新する（未登録項目のみ反映）。
     *
     * PATCH /api/country-league-master
     */
    @PatchMapping("/country-league-master")
    public ResponseEntity<CountryLeagueUpdateResponse> patchTeam(
            @RequestBody CountryLeagueUpdateRequest req) {

    	CountryLeagueUpdateResponse res = service.patchLink(req);

        HttpStatus status = switch (res.getResponseCode()) {
            case "200" -> HttpStatus.OK;
            case "400" -> HttpStatus.BAD_REQUEST;
            case "404" -> HttpStatus.NOT_FOUND;
            case "409" -> HttpStatus.CONFLICT;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };

        return ResponseEntity.status(status).body(res);
    }

    /**
     * country_league_master を全件取得する。
     *
     * GET /api/country-league-master
     */
    @GetMapping("/country-league-master")
    public ResponseEntity<List<CountryLeagueDTO>> findAll() {
        return ResponseEntity.ok(allService.findAll());
    }

    /**
     * country_league_master を条件検索する（指定された条件のみ WHERE に効く）。
     *
     * GET /api/country-league-master/search
     */
    @GetMapping("/country-league-master/search")
    public ResponseEntity<List<CountryLeagueDTO>> search(
            @ModelAttribute CountryLeagueSearchCondition cond) {
        return ResponseEntity.ok(searchService.search(cond));
    }
}
