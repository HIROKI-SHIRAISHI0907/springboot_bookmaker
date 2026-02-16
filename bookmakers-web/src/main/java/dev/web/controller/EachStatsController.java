package dev.web.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_w010.EachStatsAPIService;
import dev.web.api.bm_w010.TeamStatsResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class EachStatsController {

    private final EachStatsAPIService service;

    /**
     * GET /api/stats/{teamEnglish}/{teamHash}
     *
     * frontend: fetchTeamFeatureStats(teamEnglish, teamHash)
     */
    @GetMapping("/{teamEnglish}/{teamHash}")
    public ResponseEntity<?> getStats(
            @PathVariable String teamEnglish,
            @PathVariable String teamHash) {

        // Spring が decodeURIComponent 相当をやってくれるので、
        // Node 側と同じく生の文字列で OK
    	TeamStatsResponse dto = service.getStats(teamEnglish, teamHash);
    	if (dto == null) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body(java.util.Map.of("message", "team not found"));
		}
        return ResponseEntity.ok(dto);
    }
}
