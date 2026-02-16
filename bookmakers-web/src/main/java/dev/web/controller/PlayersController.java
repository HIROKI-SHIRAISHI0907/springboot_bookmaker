package dev.web.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import dev.web.api.bm_w009.PlayersAPIService;
import dev.web.api.bm_w009.PlayersResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/players")
@RequiredArgsConstructor
public class PlayersController {

    private final PlayersAPIService service;

    /**
     * GET /api/players/{teamEnglish}/{teamHash}
     */
    @GetMapping("/{teamEnglish}/{teamHash}")
    public PlayersResponse getPlayers(
            @PathVariable String teamEnglish,
            @PathVariable String teamHash) {

        // Spring 側で decodeURIComponent 相当はやってくれるので、
        // フロント側と同じ encodeURIComponent で OK
    	PlayersResponse res = service.getPlayers(teamEnglish, teamHash);
    	if (res == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "players not found");
        }
        return res;
    }
}
