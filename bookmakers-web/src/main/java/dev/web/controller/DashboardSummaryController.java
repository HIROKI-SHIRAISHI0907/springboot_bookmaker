package dev.web.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.dashboard.bm_w004.DashboardSummaryResponse;
import dev.web.api.dashboard.bm_w004.DashboardSummaryService;
import lombok.RequiredArgsConstructor;

/**
 * Dashboard.tsx（トップ画面）: 数字カード
 * <ul>
 *   <li>GET /v1/api/dashboard/summary  未ログインでも同じ内容</li>
 * </ul>
 * @author shiraishitoshio
 *
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/api/dashboard")
public class DashboardSummaryController {

	private final DashboardSummaryService service;

	@GetMapping("/summary")
	public ResponseEntity<DashboardSummaryResponse> summary() {
		return ResponseEntity.ok(service.getSummary());
	}
}
