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
 * Dashboard.tsx（トップ画面）: これからの試合
 * <ul>
 *   <li>GET /v1/api/dashboard/upcoming?limit=20  未ログインでも可（数値は null）</li>
 * </ul>
 * @author shiraishitoshio
 *
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/dashboard")
public class DashboardUpcomingController {

	private final DashboardUpcomingService service;

	private final DashboardAuthResolver authResolver;

	@GetMapping("/upcoming")
	public ResponseEntity<DashboardUpcomingResponse> upcoming(
			@RequestParam(name = "limit", required = false) Integer limit) {
		return ResponseEntity.ok(service.getUpcoming(limit, authResolver.isLoggedIn()));
	}
}
