package dev.web.controller;

import org.springframework.http.HttpStatus;
import dev.web.api.dashboard.support.LeagueVisibility;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.team.bm_w004.TeamDetailStatsResponse;
import dev.web.api.team.bm_w004.TeamDetailStatsService;
import lombok.RequiredArgsConstructor;

/**
 * チームページ（TeamResults.tsx  /team-results）の統計詳細データ（2026-11 追加）
 * <ul>
 *   <li>GET /v1/api/team-detail-stats?country=&amp;league=&amp;team=  未ログインでも可</li>
 * </ul>
 * @author shiraishitoshio
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TeamDetailStatsController {

	private final TeamDetailStatsService service;

	/** スクレイピング対象外・強制非表示の国・リーグは 404（2026-11-23 追加） */
	private final LeagueVisibility leagueVisibility;

	@GetMapping("/team-detail-stats")
	public ResponseEntity<TeamDetailStatsResponse> stats(
			@RequestParam(name = "country", required = false) String country,
			@RequestParam(name = "league", required = false) String league,
			@RequestParam(name = "team", required = false) String team) {
		if (leagueVisibility.isHidden(country, league)) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
		}
		return ResponseEntity.ok(service.getStats(country, league, team));
	}
}
