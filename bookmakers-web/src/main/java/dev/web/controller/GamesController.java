// src/main/java/dev/web/controller/GamesController.java
package dev.web.controller;

import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import dev.web.api.bm_w005.GameAPIService;
import dev.web.api.bm_w005.GameMatchesResponse;
import lombok.RequiredArgsConstructor;

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
@RequiredArgsConstructor
public class GamesController {

	private final GameAPIService service;

    @GetMapping("/{teamEnglish}/{teamHash}")
    public GameMatchesResponse getTeamGames(
            @PathVariable String teamEnglish,
            @PathVariable String teamHash
    ) {

        if (!StringUtils.hasText(teamEnglish) || !StringUtils.hasText(teamHash)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
            		"country/league/team are required");
        }

        return service.getTeamGames(teamEnglish, teamHash);
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
