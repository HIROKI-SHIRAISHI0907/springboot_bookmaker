package dev.web.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_w007.LiveMatchesAPIService;

/**
 * LiveMatchesAPI コントローラー
 *
 * エンドポイント:
 *   GET /api/live-matches
 *     ?country=国名&league=リーグ名 （任意）
 *
 * フロント側:
 *   - fetchLiveMatchesByLeague(country, league)
 *   - fetchLiveMatchesTodayAll()
 *
 * と互換のレスポンス（単純な配列）を返す。
 *
 * @author shiraishitoshio
 */
@RestController
@RequestMapping("/api")
public class LiveMatchesController {

    private final LiveMatchesAPIService liveMatchesService;

    public LiveMatchesController(LiveMatchesAPIService liveMatchesService) {
        this.liveMatchesService = liveMatchesService;
    }

    @GetMapping("/live-matches")
    public ResponseEntity<?> getLiveMatches(
            @RequestParam(name = "country", required = false) String country,
            @RequestParam(name = "league",  required = false) String league
    ) {
        var list = liveMatchesService.getLiveMatches(country, league);
        return ResponseEntity.ok(list);
    }
}