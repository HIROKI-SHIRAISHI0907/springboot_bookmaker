package dev.web.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_w008.CorrelationsAPIService;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/leagues/correlations")
public class CorrelationsController {

	private final CorrelationsAPIService service;

	@GetMapping("/{country}/{league}/{team}")
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
