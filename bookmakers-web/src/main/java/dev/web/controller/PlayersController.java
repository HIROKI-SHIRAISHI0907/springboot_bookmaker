package dev.web.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_w009.PlayersResponse;
import dev.web.api.bm_w009.PlayersService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PlayersController {

    private final PlayersService service;

    /**
     * GET /api/{country}/{league}/{team}/players
     */
    @GetMapping("/{country}/{league}/{team}/players")
    public PlayersResponse getPlayers(
            @PathVariable String country,
            @PathVariable String league,
            @PathVariable("team") String teamSlug) {

        // Spring 側で decodeURIComponent 相当はやってくれるので、
        // フロント側と同じ encodeURIComponent で OK
        return service.getPlayers(country, league, teamSlug);
    }
}
