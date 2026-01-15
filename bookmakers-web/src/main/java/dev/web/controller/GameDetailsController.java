// src/main/java/dev/web/controller/GameDetailController.java
package dev.web.controller;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_w005.GameDetailDTO;
import dev.web.api.bm_w005.GameDetailResponse;
import dev.web.repository.master.GameDetailsRepository;

/**
 * GameDetailAPI コントローラー
 *
 * エンドポイント:
 *   GET /api/{country}/{league}/{team}/games/detail/{seq}
 *
 * フロント側:
 *   fetchGameDetail(country, league, teamSlug, seq)
 *
 * に対応。
 *
 * @author shiraishitoshio
 */
@RestController
@RequestMapping("/api")
public class GameDetailsController {

    private final GameDetailsRepository gameDetailsRepository;

    public GameDetailsController(GameDetailsRepository gameDetailRepository) {
        this.gameDetailsRepository = gameDetailRepository;
    }

    @GetMapping("/{country}/{league}/{team}/games/detail/{seq}")
    public ResponseEntity<?> getGameDetail(
            @PathVariable("country") String countryRaw,
            @PathVariable("league") String leagueRaw,
            @PathVariable("team") String teamSlug,   // SQL では使わないがパス互換のため受け取る
            @PathVariable("seq") String seqStr
    ) {
        String country = safeDecode(countryRaw);
        String league  = safeDecode(leagueRaw);

        if (!StringUtils.hasText(country) || !StringUtils.hasText(league) || !StringUtils.hasText(seqStr)) {
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

        try {
            var opt = gameDetailsRepository.findGameDetail(country, league, seq);
            if (opt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new SimpleMessage("not found"));
            }

            GameDetailDTO dto = opt.get();
            GameDetailResponse body = new GameDetailResponse(dto);
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
