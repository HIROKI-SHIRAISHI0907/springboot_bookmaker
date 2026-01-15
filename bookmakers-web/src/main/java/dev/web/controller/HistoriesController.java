// src/main/java/dev/web/controller/HistoriesController.java
package dev.web.controller;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_w002.HistoryDetailResponseDTO;
import dev.web.api.bm_w002.HistoryResponseDTO;
import dev.web.repository.master.HistoriesRepository;

/**
 * HistoriesControllerクラス
 *  - 一覧: GET /api/{country}/{league}/{team}/history
 *  - 詳細: GET /api/{country}/{league}/{team}/history/{seq}
 */
@RestController
@RequestMapping("/api")
public class HistoriesController {

    private static final Logger log = LoggerFactory.getLogger(HistoriesController.class);

    private final HistoriesRepository historiesRepository;

    public HistoriesController(HistoriesRepository historiesRepository) {
        this.historiesRepository = historiesRepository;
    }

    private String safeDecode(String s) {
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }

    // --------------------------------------------------------
    // 一覧: GET /api/:country/:league/:team/history
    // 返却形: { matches: HistoryResponseDTO[] }
    // --------------------------------------------------------
    @GetMapping("/{country}/{league}/{team}/history")
    public ResponseEntity<?> listHistory(
            @PathVariable("country") String countryEncoded,
            @PathVariable("league") String leagueEncoded,
            @PathVariable("team") String teamSlug
    ) {
        String country = safeDecode(countryEncoded);
        String league = safeDecode(leagueEncoded);

        if (country.isEmpty() || league.isEmpty() || teamSlug.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("country, league, team are required", null));
        }

        try {
            // スラッグ → 日本語チーム名
            String teamJa = historiesRepository.findTeamJa(country, league, teamSlug);

            List<HistoryResponseDTO> matches =
                    historiesRepository.findPastMatches(country, league, teamJa);

            // フロント期待: { matches: [...] }
            return ResponseEntity.ok(new MatchesResponse(matches));
        } catch (Exception e) {
            log.error("[GET /api/{}/{}/{}/history] error", country, league, teamSlug, e);
            return ResponseEntity.status(500)
                    .body(new ErrorResponse("server error", e.getMessage()));
        }
    }

    // --------------------------------------------------------
    // 詳細: GET /api/:country/:league/:team/history/:seq
    // 返却形: { detail: HistoryDetailResponseDTO }
    // --------------------------------------------------------
    @GetMapping("/{country}/{league}/{team}/history/{seq}")
    public ResponseEntity<?> historyDetail(
            @PathVariable("country") String countryEncoded,
            @PathVariable("league") String leagueEncoded,
            @PathVariable("team") String teamSlug,  // 今は使わないがパスの一部
            @PathVariable("seq") long seq
    ) {
        String country = safeDecode(countryEncoded);
        String league = safeDecode(leagueEncoded);

        if (country.isEmpty() || league.isEmpty() || seq <= 0) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("country, league, valid seq are required", null));
        }

        try {
            Optional<HistoryDetailResponseDTO> opt =
                    historiesRepository.findHistoryDetail(country, league, seq);

            if (opt.isEmpty()) {
                return ResponseEntity.status(404)
                        .body(new ErrorResponse("not found", "no record for seq " + seq));
            }

            return ResponseEntity.ok(new DetailResponse(opt.get()));
        } catch (Exception e) {
            log.error("[GET /api/{}/{}/{}/history/{}] error", country, league, teamSlug, seq, e);
            return ResponseEntity.status(500)
                    .body(new ErrorResponse("server error", e.getMessage()));
        }
    }

    // ====== レスポンスラッパ ======

    /** 一覧: { matches: [...] } */
    static class MatchesResponse {
        private List<HistoryResponseDTO> matches;

        public MatchesResponse(List<HistoryResponseDTO> matches) {
            this.matches = matches;
        }

        public List<HistoryResponseDTO> getMatches() {
            return matches;
        }

        public void setMatches(List<HistoryResponseDTO> matches) {
            this.matches = matches;
        }
    }

    /** 詳細: { detail: {...} } */
    static class DetailResponse {
        private HistoryDetailResponseDTO detail;

        public DetailResponse(HistoryDetailResponseDTO detail) {
            this.detail = detail;
        }

        public HistoryDetailResponseDTO getDetail() {
            return detail;
        }

        public void setDetail(HistoryDetailResponseDTO detail) {
            this.detail = detail;
        }
    }

    /** エラー用 */
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
