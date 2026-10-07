package dev.web.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.dashboard.auth.DashboardAuthResolver;
import dev.web.api.dashboard.bm_w003.DashboardLiveResponse;
import dev.web.api.dashboard.bm_w003.DashboardLiveService;
import lombok.RequiredArgsConstructor;

/**
 * Dashboard.tsx（トップ画面）: ライブ・注目の試合
 * <ul>
 *   <li>GET /v1/api/dashboard/live  未ログインでも可（確率・ゴール数・平均得点などの数値は null）</li>
 * </ul>
 * @author shiraishitoshio
 *
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/dashboard")
public class DashboardLiveController {

	private final DashboardLiveService service;

	private final DashboardAuthResolver authResolver;

	@GetMapping("/live")
	public ResponseEntity<DashboardLiveResponse> live() {
		return ResponseEntity.ok(service.getLive(authResolver.isLoggedIn()));
	}
}
