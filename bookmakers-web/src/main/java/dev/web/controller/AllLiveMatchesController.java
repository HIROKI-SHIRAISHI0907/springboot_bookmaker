package dev.web.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_w007.LiveMatchDTO;
import dev.web.api.bm_w007.LiveMatchesAPIService;
import dev.web.api.bm_w007.MultiLiveMatchesResponse;

/**
 * AllLiveMatchesAPI コントローラー
 *
 * エンドポイント:
 *   GET /api/live-matches/all
 *
 * @author shiraishitoshio
 */
@RestController
@RequestMapping("/api")
public class AllLiveMatchesController {

    private final LiveMatchesAPIService liveMatchesService;

    public AllLiveMatchesController(LiveMatchesAPIService liveMatchesService) {
        this.liveMatchesService = liveMatchesService;
    }

    @GetMapping("/live-matches/all")
    public MultiLiveMatchesResponse getAllLiveMatches() {
    	List<LiveMatchDTO> matches = liveMatchesService.getAllLiveMatches();

    	MultiLiveMatchesResponse response = new MultiLiveMatchesResponse();
    	response.setMatches(matches);
    	response.setCount(matches.size());

    	return response;
    }

}