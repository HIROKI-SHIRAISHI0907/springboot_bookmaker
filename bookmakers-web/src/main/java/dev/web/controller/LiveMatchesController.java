package dev.web.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_w007.LiveMatchDTO;
import dev.web.api.bm_w007.LiveMatchesAPIService;
import dev.web.api.bm_w007.MultiLiveMatchesResponse;

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

    @GetMapping("/live-matches/{teamEnglish}/{teamHash}")
    public MultiLiveMatchesResponse getLiveMatches(
            @PathVariable String teamEnglish,
            @PathVariable String teamHash
    ) {
    	List<LiveMatchDTO> list = liveMatchesService.getLiveMatches(teamEnglish, teamHash);

        MultiLiveMatchesResponse response = new MultiLiveMatchesResponse();
    	response.setMatches(list);
    	response.setCount(list.size());

        return response;
    }
}