package dev.web.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_a003.CountryLeagueDTO;
import dev.web.api.bm_a003.CountryLeagueRequest;
import dev.web.api.bm_a003.CountryLeagueResponse;
import dev.web.api.bm_a003.CountryLeagueSearchCondition;
import dev.web.api.bm_a003.CountryLeagueService;
import lombok.RequiredArgsConstructor;

/**
 * countryLeague取得用
 * @author shiraishitoshio
 *
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class CountryLeagueWebController {

	private final CountryLeagueService service;

    /**
     * team_member_master の未登録項目を更新する（未登録項目のみ反映）。
     *
     * PATCH /api/country-league-master
     */
    @PatchMapping("/country-league-master")
    public ResponseEntity<CountryLeagueResponse> patchTeam(
            @RequestBody CountryLeagueRequest req) {

    	CountryLeagueResponse res = service.patchLink(req);

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
        return ResponseEntity.ok(service.findAll());
    }

    /**
     * country_league_master を条件検索する（指定された条件のみ WHERE に効く）。
     *
     * GET /api/country-league-master/search
     */
    @GetMapping("/country-league-master/search")
    public ResponseEntity<List<CountryLeagueDTO>> search(
            @ModelAttribute CountryLeagueSearchCondition cond) {
        return ResponseEntity.ok(service.search(cond));
    }

    @PostMapping("/country-league-master/update")
	public ResponseEntity<CountryLeagueResponse> update(@RequestBody CountryLeagueRequest dto) {
		return ResponseEntity.ok(service.update(dto));
	}
}
