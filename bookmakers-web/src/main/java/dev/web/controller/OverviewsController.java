// src/main/java/dev/web/controller/OverviewsController.java
package dev.web.controller;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_w003.OverviewResponseDTO;
import dev.web.api.bm_w003.ScheduleMatchDTO;
import dev.web.api.bm_w003.SurfaceSnapshotDTO;
import dev.web.repository.bm.OverviewsRepository;

/**
 * OverviewsControllerクラス
 *  - 月次サマリ: GET /api/{country}/{league}/{team}/overview
 *  - 試合概要:   GET /api/{country}/{league}/match/{seq}
 */
@RestController
@RequestMapping("/api")
public class OverviewsController {

    private static final Logger log = LoggerFactory.getLogger(OverviewsController.class);

    private final OverviewsRepository overviewsRepository;

    public OverviewsController(OverviewsRepository overviewsRepository) {
        this.overviewsRepository = overviewsRepository;
    }

    private String safeDecode(String s) {
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }

    // --------------------------------------------------------
    // 月次: GET /api/:country/:league/:team/overview
    // 返却形: { items: OverviewResponseDTO[] }
    // --------------------------------------------------------
    @GetMapping("/{country}/{league}/{team}/overview")
    public ResponseEntity<?> getMonthlyOverview(
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
            // スラッグ→日本語チーム名
            String teamJa = overviewsRepository.findTeamJa(country, league, teamSlug);

            List<OverviewResponseDTO> items =
                    overviewsRepository.findMonthlyOverview(country, league, teamJa);

            // フロント期待: { items: [...] }
            return ResponseEntity.ok(new OverviewListResponse(items));
        } catch (Exception e) {
            log.error("[GET /api/{}/{}/{}/overview] error", country, league, teamSlug, e);
            return ResponseEntity.status(500)
                    .body(new ErrorResponse("server error", e.getMessage()));
        }
    }

    // --------------------------------------------------------
    // 試合概要: GET /api/:country/:league/match/:seq
    // 返却形: { match: {...}, surfaces: [...] }
    // --------------------------------------------------------
    @GetMapping("/{country}/{league}/match/{seq}")
    public ResponseEntity<?> getScheduleOverview(
            @PathVariable("country") String countryEncoded,
            @PathVariable("league") String leagueEncoded,
            @PathVariable("seq") long seq
    ) {
        String country = safeDecode(countryEncoded);
        String league = safeDecode(leagueEncoded);

        if (country.isEmpty() || league.isEmpty() || seq <= 0) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("country, league, valid seq are required", null));
        }

        try {
            // 1) マッチ情報取得
            ScheduleMatchDTO match = overviewsRepository.findMatch(country, league, seq);
            if (match == null) {
                return ResponseEntity.status(404)
                        .body(new ErrorResponse("match not found", "seq=" + seq));
            }

            // 2) surface_overview から home/away の当該月/年データ
            List<SurfaceSnapshotDTO> surfaces =
                    overviewsRepository.findSurfacesForMatch(
                            country,
                            league,
                            match.getGameYear(),
                            match.getGameMonth(),
                            match.getHomeTeam(),
                            match.getAwayTeam()
                    );

            return ResponseEntity.ok(new ScheduleOverviewResponse(match, surfaces));
        } catch (Exception e) {
            log.error("[GET /api/{}/{}/match/{}] error", country, league, seq, e);
            return ResponseEntity.status(500)
                    .body(new ErrorResponse("server error", e.getMessage()));
        }
    }

    // ====== レスポンスラッパ ======

    /** 月次: { items: [...] } */
    static class OverviewListResponse {
        private List<OverviewResponseDTO> items;

        public OverviewListResponse(List<OverviewResponseDTO> items) {
            this.items = items;
        }

        public List<OverviewResponseDTO> getItems() {
            return items;
        }

        public void setItems(List<OverviewResponseDTO> items) {
            this.items = items;
        }
    }

    /** 試合概要: { match: {...}, surfaces: [...] } */
    static class ScheduleOverviewResponse {
        private ScheduleMatchDTO match;
        private List<SurfaceSnapshotDTO> surfaces;

        public ScheduleOverviewResponse(ScheduleMatchDTO match, List<SurfaceSnapshotDTO> surfaces) {
            this.match = match;
            this.surfaces = surfaces;
        }

        public ScheduleMatchDTO getMatch() {
            return match;
        }

        public void setMatch(ScheduleMatchDTO match) {
            this.match = match;
        }

        public List<SurfaceSnapshotDTO> getSurfaces() {
            return surfaces;
        }

        public void setSurfaces(List<SurfaceSnapshotDTO> surfaces) {
            this.surfaces = surfaces;
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
