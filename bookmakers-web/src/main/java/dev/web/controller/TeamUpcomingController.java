package dev.web.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.dashboard.auth.DashboardAuthResolver;
import dev.web.api.team.bm_w002.TeamUpcomingResponse;
import dev.web.api.team.bm_w002.TeamUpcomingService;
import lombok.RequiredArgsConstructor;

/**
 * TeamResults.tsx（チームページ）: 次節・これからの試合
 * <ul>
 *   <li>GET /v1/api/team-upcoming?country=&amp;league=&amp;team=&amp;limit=3  未ログインでも可（数値は null）</li>
 * </ul>
 * @author shiraishitoshio
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/team-upcoming")
public class TeamUpcomingController {

	private final TeamUpcomingService service;

	private final DashboardAuthResolver authResolver;

	@GetMapping
	public ResponseEntity<TeamUpcomingResponse> upcoming(
			@RequestParam(name = "country") String country,
			@RequestParam(name = "league") String league,
			@RequestParam(name = "team") String team,
			@RequestParam(name = "limit", required = false) Integer limit) {
		return ResponseEntity.ok(service.getUpcoming(country, league, team, limit, authResolver.isLoggedIn()));
	}
}
