package dev.web.controller;

import java.util.List;

import org.apache.coyote.BadRequestException;
import org.apache.ibatis.javassist.NotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_w002.HistoriesAPIService;
import dev.web.api.bm_w002.HistoryDetailResponseDTO;
import dev.web.api.bm_w002.HistoryMatchesResponse;
import dev.web.api.bm_w002.HistoryResponseDTO;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/history")
@RequiredArgsConstructor
public class HistoriesController {

    private final HistoriesAPIService historiesAPIService;

    // 一覧: GET /api/history/{teamEnglish}/{teamHash}
    @GetMapping("/{teamEnglish}/{teamHash}")
    public ResponseEntity<HistoryMatchesResponse.MatchesResponse> listHistory(
    		@PathVariable String teamEnglish,
            @PathVariable String teamHash
    ) throws BadRequestException {

        List<HistoryResponseDTO> matches = historiesAPIService.listHistory(teamEnglish, teamHash);
        return ResponseEntity.ok(new HistoryMatchesResponse.MatchesResponse(matches));
    }

    // 詳細: GET /api/history/{teamEnglish}/{teamHash}/{seq}
    @GetMapping("/{teamEnglish}/{teamHash}/{seq}")
    public ResponseEntity<HistoryMatchesResponse.DetailResponse> historyDetail(
            @PathVariable String country,
            @PathVariable String league,
            @PathVariable String team,
            @PathVariable long seq
    ) throws BadRequestException, NotFoundException {

        HistoryDetailResponseDTO detail = historiesAPIService
                .getHistoryDetail(country, league, seq)
                .orElseThrow(() -> new NotFoundException("no record for seq " + seq));

        return ResponseEntity.ok(new HistoryMatchesResponse.DetailResponse(detail));
    }

}
