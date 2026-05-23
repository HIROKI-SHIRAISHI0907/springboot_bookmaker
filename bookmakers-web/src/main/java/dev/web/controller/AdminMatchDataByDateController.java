package dev.web.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_a018.MatchDataByDateListResponse;
import dev.web.api.bm_a018.MatchDataByDateService;

/**
 * DBに登録された対戦データを日付指定で取得
 */
@RestController
@RequestMapping("/api/admin")
public class AdminMatchDataByDateController {

    @Autowired
    private MatchDataByDateService matchDataByDateService;

    @GetMapping("/matches/by-date")
    public MatchDataByDateListResponse getMatchDataByDate(
            @RequestParam(value = "targetDate", required = false) String targetDate,
            @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(value = "size", required = false, defaultValue = "10") Integer size) {
        return matchDataByDateService.getMatchDataByDate(targetDate, page, size);
    }
}
