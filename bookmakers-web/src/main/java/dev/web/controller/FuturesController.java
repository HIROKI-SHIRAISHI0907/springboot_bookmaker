package dev.web.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_w001.FuturesAPIService;
import dev.web.api.bm_w001.FuturesResponseDTO;

@RestController
@RequestMapping("/api/future")
public class FuturesController {

	private final FuturesAPIService futuresAPIService;

    public FuturesController(FuturesAPIService futuresAPIService) {
        this.futuresAPIService = futuresAPIService;
    }

    @GetMapping("/{country}/{league}/{team}")
    public Map<String, Object> getFuture(
            @PathVariable String country,
            @PathVariable String league,
            @PathVariable String team
    ) {
        List<FuturesResponseDTO> matches = futuresAPIService.getFutureMatches(country, league, team);
        return Map.of("matches", matches);
    }

}
