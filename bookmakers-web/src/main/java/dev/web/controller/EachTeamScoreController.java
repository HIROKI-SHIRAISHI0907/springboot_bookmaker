package dev.web.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_w015.EachTeamScoreResponseDTO;
import dev.web.api.bm_w015.EachTeamScoreService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class EachTeamScoreController {

    private final EachTeamScoreService eachTeamScoreService;

    @GetMapping("/each-team-score/{teamEnglish}/{teamHash}")
    public ResponseEntity<List<EachTeamScoreResponseDTO>> getEachTeamScore(
    		@PathVariable String teamEnglish,
            @PathVariable String teamHash) {

        List<EachTeamScoreResponseDTO> response =
                eachTeamScoreService.getEachTeamScore(teamEnglish, teamHash);

        if (response == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(response);
    }
}
