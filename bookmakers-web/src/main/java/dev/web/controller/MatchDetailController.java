package dev.web.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.dashboard.auth.DashboardAuthResolver;
import dev.web.api.matchDetail.bm_w001.MatchDetailResponse;
import dev.web.api.matchDetail.bm_w001.MatchDetailService;
import dev.web.api.matchDetail.bm_w003.MatchDetailExtraResponse;
import dev.web.api.matchDetail.bm_w003.MatchDetailExtraService;
import lombok.RequiredArgsConstructor;

/**
 * MatchDetail.tsx（試合詳細）
 * <ul>
 *   <li>GET /v1/api/match-detail?seqKey=  未ログインでも可（勝敗確率の数値は null）。見つからなければ found=false</li>
 *   <li>GET /v1/api/match-detail/extra?matchId=  統計詳細データ（match_detail_*）。未ログインでも可。無ければ found=false</li>
 * </ul>
 * @author shiraishitoshio
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/match-detail")
public class MatchDetailController {

	private final MatchDetailService service;

	private final DashboardAuthResolver authResolver;

	private final MatchDetailExtraService extraService;

	@GetMapping
	public ResponseEntity<MatchDetailResponse> detail(@RequestParam(name = "seqKey") String seqKey) {
		return ResponseEntity.ok(service.getDetail(seqKey, authResolver.isLoggedIn()));
	}

	@GetMapping("/extra")
	public ResponseEntity<MatchDetailExtraResponse> extra(@RequestParam(name = "matchId") String matchId) {
		return ResponseEntity.ok(extraService.getExtra(matchId));
	}
}
