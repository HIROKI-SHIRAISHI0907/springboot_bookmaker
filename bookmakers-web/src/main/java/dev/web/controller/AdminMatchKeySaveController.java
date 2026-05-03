package dev.web.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_a013.MatchKeySaveListResponse;
import dev.web.api.bm_a013.MatchKeySaveService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminMatchKeySaveController {

    private final MatchKeySaveService matchKeySaveService;

    /**
     * match_key_save 一覧取得
     * GET /v1/api/admin/match-key-save
     */
    @GetMapping("/match-key-save")
    public MatchKeySaveListResponse getMatchKeySaveList() {
        List<String> matchKeys = matchKeySaveService.getAllMatchKeys();
        return new MatchKeySaveListResponse(matchKeys.size(), matchKeys);
    }
}
