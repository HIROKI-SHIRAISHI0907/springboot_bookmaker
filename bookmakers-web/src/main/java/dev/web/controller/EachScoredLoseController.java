package dev.web.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import dev.web.api.bm_w014.EachScoreLostDataResponseDTO;
import dev.web.api.bm_w014.EachScoredLostAPIService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/scoredLost")
@RequiredArgsConstructor
public class EachScoredLoseController {

	private final EachScoredLostAPIService eachScoredLostAPIService;

    /**
     * チーム指定
     * @param country
     * @param league
     * @param team
     * @return
     */
    @GetMapping("/{teamEnglish}/{teamHash}")
    public Map<String, Object> getFuture(
            @PathVariable String teamEnglish,
            @PathVariable String teamHash
    ) {
        List<EachScoreLostDataResponseDTO> matches = eachScoredLostAPIService
        		.getEachScoreLoseMatches(teamEnglish, teamHash);
        if (matches == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "matches not found");
        }
        return Map.of("matches", matches);
    }

}
