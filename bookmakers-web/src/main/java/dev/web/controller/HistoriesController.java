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

@RestController
@RequestMapping("/api/history")
public class HistoriesController {

    private final HistoriesAPIService historiesAPIService;

    public HistoriesController(HistoriesAPIService historiesAPIService) {
        this.historiesAPIService = historiesAPIService;
    }

    // 一覧: GET /api/history/{country}/{league}/{team}
    @GetMapping("/{country}/{league}/{team}")
    public ResponseEntity<HistoryMatchesResponse.MatchesResponse> listHistory(
            @PathVariable String country,
            @PathVariable String league,
            @PathVariable String team
    ) throws BadRequestException {
        validate(country, league, team);

        List<HistoryResponseDTO> matches = historiesAPIService.listHistory(country, league, team);
        return ResponseEntity.ok(new HistoryMatchesResponse.MatchesResponse(matches));
    }

    // 詳細: GET /api/history/{country}/{league}/{team}/{seq}
    @GetMapping("/{country}/{league}/{team}/{seq}")
    public ResponseEntity<HistoryMatchesResponse.DetailResponse> historyDetail(
            @PathVariable String country,
            @PathVariable String league,
            @PathVariable String team,
            @PathVariable long seq
    ) throws BadRequestException, NotFoundException {
        validate(country, league, team);
        if (seq <= 0) throw new BadRequestException("valid seq is required");

        HistoryDetailResponseDTO detail = historiesAPIService
                .getHistoryDetail(country, league, seq)
                .orElseThrow(() -> new NotFoundException("no record for seq " + seq));

        return ResponseEntity.ok(new HistoryMatchesResponse.DetailResponse(detail));
    }

    private static void validate(String country, String league, String team) throws BadRequestException {
        if (isBlank(country) || isBlank(league) || isBlank(team)) {
            throw new BadRequestException("country, league, team are required");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
