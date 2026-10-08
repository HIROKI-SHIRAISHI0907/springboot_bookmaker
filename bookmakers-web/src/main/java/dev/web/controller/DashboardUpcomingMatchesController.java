package dev.web.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.dashboard.auth.DashboardAuthResolver;
import dev.web.api.dashboard.bm_w005.DashboardUpcomingResponse;
import dev.web.api.dashboard.bm_w005.DashboardUpcomingService;
import lombok.RequiredArgsConstructor;

/**
 * UpcomingMatches.tsx（これからの試合・全試合画面）
 * <ul>
 *   <li>GET /v1/api/upcoming-matches?hours=36&amp;league=国 / リーグ  未ログインでも可（数値は null）
 *       hours は最大 168（7日）。league を付けるとそのリーグだけ。</li>
 * </ul>
 * @author shiraishitoshio
 *
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/upcoming-matches")
public class DashboardUpcomingMatchesController {

	private final DashboardUpcomingService service;

	private final DashboardAuthResolver authResolver;

	@GetMapping
	public ResponseEntity<DashboardUpcomingResponse> all(
			@RequestParam(name = "hours", required = false) Integer hours,
			@RequestParam(name = "league", required = false) String league) {
		return ResponseEntity.ok(service.getAll(hours, league, authResolver.isLoggedIn()));
	}
}
