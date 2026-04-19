package dev.web.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_w005.GameDetailAPIService;
import dev.web.api.bm_w005.GameDetailDTO;
import dev.web.api.bm_w005.GameDetailRequest;
import dev.web.api.bm_w005.GameDetailResponse;

/**
 * GameDetailAPI コントローラー
 *
 * 内部API:
 *   POST /api/games/detail
 *
 * body:
 *   { "seq": 12345 }
 *
 * 画面URL:
 *   /teamDetail/{teamEnglish}/{teamHash}
 */
@RestController
@RequestMapping("/api/games/detail")
public class GameDetailsViaLiveDataController {

    private final GameDetailAPIService gameDetailsService;

    public GameDetailsViaLiveDataController(GameDetailAPIService gameDetailsService) {
        this.gameDetailsService = gameDetailsService;
    }

    @PostMapping(
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public GameDetailResponse getGameDetail(@RequestBody(required = false) GameDetailRequest request) {
        if (request == null || request.getSeq() <= 0L) {
            return new GameDetailResponse(new GameDetailDTO());
        }

        return gameDetailsService.getGameDetail(request.getSeq());
    }
}
