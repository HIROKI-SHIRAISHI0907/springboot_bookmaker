// src/main/java/dev/web/controller/GamesController.java
package dev.web.controller;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_w005.GameMatchDTO;
import dev.web.api.bm_w005.GameMatchesResponse;
import dev.web.repository.bm.GamesRepository;

/**
 * GamesAPI コントローラー
 *
 * エンドポイント:
 *   GET /api/games/{country}/{league}/{team}
 *
 * フロント側 fetchTeamGames(...) に対応。
 *
 * @author shiraishitoshio
 */
@RestController
@RequestMapping("/api/games")
public class GamesController {

    private final GamesRepository gamesRepository;

    public GamesController(GamesRepository gamesRepository) {
        this.gamesRepository = gamesRepository;
    }

    @GetMapping("/{country}/{league}/{team}")
    public ResponseEntity<?> getTeamGames(
            @PathVariable("country") String country,
            @PathVariable("league") String league,
            @PathVariable("team") String teamSlug
    ) {

        if (!StringUtils.hasText(country) || !StringUtils.hasText(league) || !StringUtils.hasText(teamSlug)) {
            return ResponseEntity.badRequest()
                    .body(new SimpleMessage("country/league/team are required"));
        }

        try {
            // スラッグ -> 日本語名
            String teamJa = gamesRepository.findTeamJa(country, league, teamSlug);

            // 試合一覧取得（LIVE/FINISHED 混在）
            List<GameMatchDTO> all = gamesRepository.findGamesForTeam(country, league, teamJa);

            List<GameMatchDTO> live = new ArrayList<>();
            List<GameMatchDTO> finished = new ArrayList<>();

            for (GameMatchDTO dto : all) {
                if ("FINISHED".equalsIgnoreCase(dto.getStatus())) {
                    finished.add(dto);
                } else {
                    live.add(dto);
                }
            }

            GameMatchesResponse body = new GameMatchesResponse(live, finished);
            return ResponseEntity.ok(body);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body(new SimpleMessage("server error: " + e.getMessage()));
        }
    }

    // ---------- private helpers ----------

    /**
     * フロント側の { message: string } と互換の簡易 DTO
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
