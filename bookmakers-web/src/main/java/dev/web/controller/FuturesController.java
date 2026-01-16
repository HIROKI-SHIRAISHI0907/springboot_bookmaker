package dev.web.controller;


import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_w001.FutureMatchesResponse;
import dev.web.api.bm_w001.FuturesResponseDTO;
import dev.web.repository.bm.FuturesRepository;

@RestController
@RequestMapping("/api")
public class FuturesController {

	private final FuturesRepository repository;

    public FuturesController(FuturesRepository repository) {
        this.repository = repository;
    }

    /**
     * GET /api/{country}/{league}/{team}/future
     *
     * - future_master から start_flg='1' (予定) のみ取得
     * - :team は英語スラッグ。country_league_master で日本語名に解決してマッチング
     * - 並び順: ラウンド番号(昇順) → 試合時間(昇順)
     */
    @GetMapping("/{country}/{league}/{team}/future")
    public ResponseEntity<?> getFutureMatches(
            @PathVariable("country") String countryEncoded,
            @PathVariable("league") String leagueEncoded,
            @PathVariable("team") String teamSlug
    ) {
        String country = safeDecode(countryEncoded);
        String league = safeDecode(leagueEncoded);

        try {
            // スラッグ → 日本語名
            String teamJa = this.repository.findTeamJa(country, league, teamSlug);

            // 予定のみ取得
            List<FuturesResponseDTO> matches = this.repository.findFutureMatches(teamJa, country, league);

            return ResponseEntity.ok(new FutureMatchesResponse(matches));
        } catch (Exception e) {
            return ResponseEntity
                    .status(500)
                    .body(new ErrorResponse("server error", e.getMessage()));
        }
    }

    private String safeDecode(String s) {
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }

    // 簡単なエラーレスポンス用クラス
    static class ErrorResponse {
        private String message;
        private String detail;

        public ErrorResponse(String message, String detail) {
            this.message = message;
            this.detail = detail;
        }

        public String getMessage() { return message; }
        public String getDetail() { return detail; }
    }

}
