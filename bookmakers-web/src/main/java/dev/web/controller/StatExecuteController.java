package dev.web.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_w016.StatExecuteRequest;
import dev.web.batch.EcsScrapeTaskRunner;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class StatExecuteController {

	private final EcsScrapeTaskRunner runService;

	@PostMapping("/stat/each")
	public ResponseEntity<Map<String, String>> runStat(
			@RequestBody(required = false) StatExecuteRequest req) throws Exception {

		String country = trim(req == null ? null : req.getCountry());
		String league = trim(req == null ? null : req.getLeague());

		if (country.isEmpty() && !league.isEmpty()) {
			throw new IllegalArgumentException("league を指定する場合は country も指定してください。");
		}

		Map<String, String> extraEnv = new LinkedHashMap<>();
		if (!country.isEmpty()) {
			extraEnv.put("BM_COUNTRY", country);
		}
		if (!league.isEmpty()) {
			extraEnv.put("BM_LEAGUE", league);
		}

		String taskArn = runService.runScrape("B014", extraEnv, true);

		return ResponseEntity.accepted().body(Map.of(
				"taskArn", taskArn,
				"batchCd", "B014"
		));
	}

	private static String trim(String s) {
		return s == null ? "" : s.trim();
	}
}
