package dev.web.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.dashboard.bm_w006.DashboardResultsResponse;
import dev.web.api.dashboard.bm_w006.DashboardResultsService;
import lombok.RequiredArgsConstructor;

/**
 * Dashboard.tsx（トップ画面）本日の試合結果
 * <ul>
 *   <li>GET /v1/api/dashboard/results  未ログインでも可。今日（JST）終了した試合をリーグごとに返す</li>
 * </ul>
 * @author shiraishitoshio
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/dashboard")
public class DashboardResultsController {

	private final DashboardResultsService service;

	@GetMapping("/results")
	public ResponseEntity<DashboardResultsResponse> results() {
		return ResponseEntity.ok(service.getResults());
	}
}
