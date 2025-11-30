// src/main/java/dev/web/controller/StandingsController.java
package dev.web.controller;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_w006.LeagueStandingResponse;
import dev.web.api.bm_w006.StandingRowDTO;
import dev.web.repository.StandingsRepository;

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
@RequestMapping("/api")
public class StandingsController {

    private final StandingsRepository standingsRepository;

    public StandingsController(StandingsRepository standingsRepository) {
        this.standingsRepository = standingsRepository;
    }

    /**
     * 順位表取得.
     *
     * @param countryRaw URLエンコードされた国名（日本語）
     * @param leagueRaw  URLエンコードされたリーグ名（日本語）
     * @return LeagueStandingDTO
     */
    @GetMapping("/{country}/{league}/standings")
    public ResponseEntity<?> getStandings(
            @PathVariable("country") String countryRaw,
            @PathVariable("league") String leagueRaw
    ) {
        String country = safeDecode(countryRaw);
        String league  = safeDecode(leagueRaw);

        if (!StringUtils.hasText(country) || !StringUtils.hasText(league)) {
            return ResponseEntity.badRequest()
                    .body(new SimpleMessage("country and league are required"));
        }

        try {
            List<StandingRowDTO> rows = standingsRepository.findStandings(country, league);

            LeagueStandingResponse body = new LeagueStandingResponse(rows);
            // season / updatedAt は現状設定なし（null）
            body.setSeason(null);
            body.setUpdatedAt(null);

            return ResponseEntity.ok(body);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body(new SimpleMessage("server error: " + e.getMessage()));
        }
    }

    // ---------- private helpers ----------

    private String safeDecode(String s) {
        if (s == null) return null;
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return s;
        }
    }

    /**
     * フロント側の { message: string } と互換の簡易 DTO.
     */
    public static class SimpleMessage {
        /** メッセージ本文 */
        private final String message;

        public SimpleMessage(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }
}
