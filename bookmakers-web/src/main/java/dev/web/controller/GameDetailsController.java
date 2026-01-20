// src/main/java/dev/web/controller/GameDetailController.java
package dev.web.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_w005.GameDetailAPIService;
import dev.web.api.bm_w005.GameDetailDTO;
import dev.web.api.bm_w005.GameDetailResponse;

/**
 * GameDetailAPI コントローラー
 *
 * エンドポイント:
 *   GET /api/games/detail/{country}/{league}/{team}/{seq}
 *
 * フロント側:
 *   fetchGameDetail(country, league, teamSlug, seq)
 *
 * に対応。
 *
 * @author shiraishitoshio
 */
@RestController
@RequestMapping("/api/games/detail")
public class GameDetailsController {

    private final GameDetailAPIService gameDetailsService;

    public GameDetailsController(GameDetailAPIService gameDetailsService) {
        this.gameDetailsService = gameDetailsService;
    }

    @GetMapping("/{country}/{league}/{team}/{seq}")
    public ResponseEntity<?> getGameDetail(
            @PathVariable("country") String country,
            @PathVariable("league") String league,
            @PathVariable("team") String teamSlug,
            @PathVariable("seq") String seqStr
    ) {
        if (!StringUtils.hasText(country) ||
            !StringUtils.hasText(league)  ||
            !StringUtils.hasText(seqStr)) {
            return ResponseEntity.badRequest()
                    .body(new SimpleMessage("country/league/seq are required"));
        }

        long seq;
        try {
            seq = Long.parseLong(seqStr);
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest()
                    .body(new SimpleMessage("seq must be numeric"));
        }

        var opt = gameDetailsService.getGameDetail(country, league, seq);
        if (opt.isEmpty()) {
        	return ResponseEntity.ok(new GameDetailResponse(new GameDetailDTO()));
        }

        return ResponseEntity.ok(new GameDetailResponse(opt.get()));
    }

    // ---------- private helpers ----------

    /**
     * フロント側の { message: string } と互換の簡易 DTO。
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
