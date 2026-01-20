// src/main/java/dev/web/controller/StandingsController.java
package dev.web.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_w006.StandingsAPIService;

/**
 * リーグ順位表API コントローラー.
 *
 * エンドポイント:
 *   GET /api/{country}/{league}/standings
 *
 * フロント側:
 *   fetchLeagueStanding(countryRaw, leagueRaw)
 *
 * に対応する。
 *
 * @author shiraishitoshio
 */
@RestController
@RequestMapping("/api/standings")
public class StandingsController {

    private final StandingsAPIService standingsService;

    public StandingsController(StandingsAPIService standingsService) {
        this.standingsService = standingsService;
    }

    /**
     * 順位表取得.
     *
     * GET /api/standings/{country}/{league}
     */
    @GetMapping("/{country}/{league}")
    public ResponseEntity<?> getStandings(
            @PathVariable("country") String country,
            @PathVariable("league") String league
    ) {
        if (!StringUtils.hasText(country) || !StringUtils.hasText(league)) {
            return ResponseEntity.badRequest()
                    .body(new SimpleMessage("country and league are required"));
        }

        var body = standingsService.getStandings(country, league);
        return ResponseEntity.ok(body);
    }

    // ---------- private helpers ----------

    /**
     * フロント側の { message: string } と互換の簡易 DTO.
     */
    public static class SimpleMessage {
        private final String message;

        public SimpleMessage(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }
}