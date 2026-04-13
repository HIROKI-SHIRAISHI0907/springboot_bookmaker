package dev.web.controller;

import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_a014.AllLeagueDataManualUpdateRequest;
import dev.web.api.bm_a014.AllLeagueDataManualUpdateService;
import lombok.RequiredArgsConstructor;

/**
 * AllLeague取得用
 * @author shiraishitoshio
 *
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class AllLeagueDataManualUpdateController {

	private final AllLeagueDataManualUpdateService service;

	@GetMapping("/master/sub-league/board")
	public ResponseEntity<?> board() {
		return ResponseEntity.ok(Map.of(
				"items", service.findBoardItems()
		));
	}

	@PostMapping("/master/sub-league")
	public ResponseEntity<?> execute(@RequestBody AllLeagueDataManualUpdateRequest req) {
		service.save(req);
		return ResponseEntity.ok(Map.of(
				"returnCd", "0",
				"message", "OK"
		));
	}

	@GetMapping("/master/sub-league/targets")
	public ResponseEntity<?> targets() {
		return ResponseEntity.ok(Map.of(
				"countries", service.findTargets().stream()
						.collect(java.util.stream.Collectors.groupingBy(
								dev.web.repository.master.AllLeagueDataManualUpdateRepository.CountryLeagueTargetRow::getCountry,
								java.util.LinkedHashMap::new,
								java.util.stream.Collectors.mapping(
										dev.web.repository.master.AllLeagueDataManualUpdateRepository.CountryLeagueTargetRow::getLeague,
										java.util.stream.Collectors.toList()
								)
						))
						.entrySet().stream()
						.map(e -> Map.of(
								"country", e.getKey(),
								"leagues", e.getValue()
						))
						.collect(Collectors.toList())
		));
	}

}
