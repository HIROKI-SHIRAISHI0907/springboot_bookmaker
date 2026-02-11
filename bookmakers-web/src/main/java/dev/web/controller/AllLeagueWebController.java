package dev.web.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_a005.AllLeagueDTO;
import dev.web.api.bm_a005.AllLeagueRequest;
import dev.web.api.bm_a005.AllLeagueResponse;
import dev.web.api.bm_a005.AllLeagueService;
import lombok.RequiredArgsConstructor;

/**
 * AllLeague取得用
 * @author shiraishitoshio
 *
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class AllLeagueWebController {

    private final AllLeagueService service;

    /**
     * all_league_scrape_master の link を更新する。
     *
     * PATCH /api/all-league-master
     */
    @PatchMapping("/all-league-master")
    public ResponseEntity<AllLeagueResponse> patch(
            @RequestBody AllLeagueRequest req) {

    	AllLeagueResponse res = service.upsert(
        		req.getCountry(),
        		req.getLeague(),
        		req.getLogicFlg(),
        		req.getDispFlg());

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
     * all_league_scrape_master を全件取得する。
     *
     * GET /api/all-league-master
     */
    @GetMapping("/all-league-master")
    public ResponseEntity<List<AllLeagueDTO>> findAll() {
        return ResponseEntity.ok(service.findAll());
    }

}
