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

import dev.web.api.bm_a002.CountryLeagueSeasonDTO;
import dev.web.api.bm_a002.CountryLeagueSeasonFindAllService;
import dev.web.api.bm_a002.CountryLeagueSeasonSearchCondition;
import dev.web.api.bm_a002.CountryLeagueSeasonSearchService;
import dev.web.api.bm_a002.CountryLeagueSeasonRequest;
import dev.web.api.bm_a002.CountryLeagueSeasonResponse;
import dev.web.api.bm_a002.CountryLeagueSeasonService;
import lombok.RequiredArgsConstructor;

/**
 * countryLeagueSeason取得用
 * @author shiraishitoshio
 *
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class CountryLeagueSeasonWebController {

    private final CountryLeagueSeasonService service;

    private final CountryLeagueSeasonFindAllService allService;

    private final CountryLeagueSeasonSearchService searchService;

    /**
     * country_league_season_master の link を更新する。
     *
     * PATCH /api/country-league-season-master
     */
    @PatchMapping("/country-league-season-master")
    public ResponseEntity<CountryLeagueSeasonResponse> patchCountryLeagueSeasonMaster(
            @RequestBody CountryLeagueSeasonRequest req) {

        CountryLeagueSeasonResponse res = service.patchLink(req);

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
     * country_league_season_master を全件取得する。
     *
     * GET /api/country-league-season-master
     */
    @GetMapping("/country-league-season-master")
    public ResponseEntity<List<CountryLeagueSeasonDTO>> findAll() {
        return ResponseEntity.ok(allService.findAll());
    }

    /**
     * country_league_season_master を条件検索する（指定された条件のみ WHERE に効く）。
     *
     * GET /api/country-league-season-master/search
     */
    @GetMapping("/country-league-season-master/search")
    public ResponseEntity<List<CountryLeagueSeasonDTO>> search(
            @ModelAttribute CountryLeagueSeasonSearchCondition cond) {
        return ResponseEntity.ok(searchService.search(cond));
    }
}
