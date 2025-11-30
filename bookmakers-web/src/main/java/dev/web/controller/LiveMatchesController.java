package dev.web.controller;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_w007.LiveMatchResponse;
import dev.web.repository.LiveMatchesRepository;

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

    private final LiveMatchesRepository liveMatchesRepository;

    public LiveMatchesController(LiveMatchesRepository liveMatchesRepository) {
        this.liveMatchesRepository = liveMatchesRepository;
    }

    /**
     * 現在開催中の試合一覧を取得。
     *
     *   GET /api/live-matches
     *     ?country=...&league=... で絞り込み（省略時は全カテゴリ）。
     */
    @GetMapping("/live-matches")
    public ResponseEntity<?> getLiveMatches(
            @RequestParam(name = "country", required = false) String countryParam,
            @RequestParam(name = "league",  required = false) String leagueParam
    ) {
        try {
            // Node 実装では encodeURIComponent されているが、
            // 念のため URLDecode しておく
            String country = trimSafe(safeDecode(countryParam));
            String league  = trimSafe(safeDecode(leagueParam));

            if (!StringUtils.hasText(country) || !StringUtils.hasText(league)) {
                // どちらか欠けている場合は「全カテゴリ」として扱う
                country = null;
                league  = null;
            }

            List<LiveMatchResponse> list = liveMatchesRepository.findLiveMatches(country, league);
            return ResponseEntity.ok(list);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new SimpleMessage("server error: " + e.getMessage()));
        }
    }

    // ------------- helpers -------------

    private String safeDecode(String s) {
        if (s == null) return null;
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return s;
        }
    }

    private String trimSafe(String s) {
        return s == null ? null : s.trim();
    }

    /**
     * フロント側の { message: string } 相当の簡易エラーレスポンス DTO。
     */
    public static class SimpleMessage {
        /** メッセージ */
        private final String message;

        public SimpleMessage(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }
}
