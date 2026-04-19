// src/main/java/dev/web/controller/GameDetailController.java
package dev.web.controller;

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

    @GetMapping("/{teamEnglish}/{teamHash}")
    public GameDetailResponse getGameDetail(
    		@PathVariable String teamEnglish,
            @PathVariable String teamHash,
            @PathVariable("seq") String seqStr
    ) {
    	if (!StringUtils.hasText(teamEnglish) || !StringUtils.hasText(teamHash)) {
            return new GameDetailResponse(new GameDetailDTO());
        }

        long seq;
        try {
            seq = Long.parseLong(seqStr);
        } catch (NumberFormatException e) {
        	return new GameDetailResponse(new GameDetailDTO());
        }

        GameDetailResponse opt = gameDetailsService.getGameDetail(teamEnglish, teamHash, seq);
        return opt;
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
