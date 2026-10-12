package dev.web.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.dashboard.auth.DashboardAuthResolver;
import dev.web.api.matchDetail.bm_w001.MatchDetailResponse;
import dev.web.api.matchDetail.bm_w001.MatchDetailService;
import lombok.RequiredArgsConstructor;

/**
 * MatchDetail.tsx（試合詳細）
 * <ul>
 *   <li>GET /v1/api/match-detail?seqKey=  未ログインでも可（勝敗確率の数値は null）。見つからなければ found=false</li>
 * </ul>
 * @author shiraishitoshio
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/match-detail")
public class MatchDetailController {

	private final MatchDetailService service;

	private final DashboardAuthResolver authResolver;

	@GetMapping
	public ResponseEntity<MatchDetailResponse> detail(@RequestParam(name = "seqKey") String seqKey) {
		return ResponseEntity.ok(service.getDetail(seqKey, authResolver.isLoggedIn()));
	}
}
