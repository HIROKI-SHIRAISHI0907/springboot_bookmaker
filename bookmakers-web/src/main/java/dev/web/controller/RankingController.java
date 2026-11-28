package dev.web.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.dashboard.support.LeagueVisibility;
import dev.web.api.ranking.bm_w001.RankingService;
import lombok.RequiredArgsConstructor;

/**
 * Rankings.tsx（データ別のランキングと順位の変遷。2026-11-28 追加）  未ログインでも可
 * <ul>
 *   <li>GET /v1/api/rankings/leagues: 選べる国・リーグ</li>
 *   <li>GET /v1/api/rankings/metrics?country=&amp;league=: 選べるデータ（スタッツは統計ができたリーグだけ available）</li>
 *   <li>GET /v1/api/rankings?country=&amp;league=&amp;metric=POINTS: 現在のランキングとラウンドごとの順位</li>
 * </ul>
 * スクレイピング対象外・強制非表示の国・リーグは 404。
 * @author shiraishitoshio
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/rankings")
public class RankingController {

	private final RankingService service;

	private final LeagueVisibility leagueVisibility;

	@GetMapping("/leagues")
	public ResponseEntity<?> leagues() {
		return ResponseEntity.ok(service.leagues());
	}

	@GetMapping("/metrics")
	public ResponseEntity<?> metrics(
			@RequestParam(name = "country", required = false) String country,
			@RequestParam(name = "league", required = false) String league) {
		if (leagueVisibility.isHidden(country, league)) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "このリーグは現在表示していません"));
		}
		return ResponseEntity.ok(service.metrics(country, league));
	}

	@GetMapping
	public ResponseEntity<?> ranking(
			@RequestParam(name = "country", required = false) String country,
			@RequestParam(name = "league", required = false) String league,
			@RequestParam(name = "metric", required = false) String metric) {
		if (leagueVisibility.isHidden(country, league)) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "このリーグは現在表示していません"));
		}
		try {
			return ResponseEntity.ok(service.get(country, league, metric));
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
		}
	}
}
