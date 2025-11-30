package dev.web.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_w008.CorrelationsService;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class CorrelationsController {

    private final CorrelationsService service;

    @GetMapping("/{country}/{league}/{team}/correlations")
    public ResponseEntity<?> getCorrelations(
            @PathVariable String country,
            @PathVariable String league,
            @PathVariable String team,
            @RequestParam(required = false) String opponent) {

        var dto = service.getCorrelations(country, league, team, opponent);
        if (dto == null) {
            return ResponseEntity.status(404)
                    .body(java.util.Map.of("message", "team not found"));
        }
        return ResponseEntity.ok(dto);
    }
}
