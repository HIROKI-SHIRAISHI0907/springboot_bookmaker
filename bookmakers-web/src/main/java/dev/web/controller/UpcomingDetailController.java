package dev.web.controller;

import org.springframework.http.HttpStatus;
import dev.web.api.dashboard.support.LeagueVisibility;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.upcomingDetail.bm_w001.UpcomingBadgeService;
import dev.web.api.upcomingDetail.bm_w001.UpcomingDetailService;
import lombok.RequiredArgsConstructor;

/**
 * UpcomingDetail.tsx（これからの試合の詳細: 過去の対戦成績・会場・順位表・ゴール数などの統計）  未ログインでも可
 * <ul>
 *   <li>GET /v1/api/upcoming-detail?country=&amp;league=&amp;home=&amp;away=&amp;h2hLimit=10
 *       home・away が空、または同じチームのときは 400 { message }</li>
 *   <li>GET /v1/api/upcoming-detail/badges?country=&amp;league=&amp;home=&amp;away=&amp;round=ラウンド N
 *       チームのアイコン（連勝中・前節ホーム初勝利・ボール保持率リーグ1位 など。2026-11-27 追加）</li>
 * </ul>
 * @author shiraishitoshio
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/upcoming-detail")
public class UpcomingDetailController {

	private final UpcomingDetailService service;

	private final UpcomingBadgeService badgeService;

	/** スクレイピング対象外・強制非表示の国・リーグは 404（2026-11-23 追加） */
	private final LeagueVisibility leagueVisibility;

	@GetMapping
	public ResponseEntity<?> detail(
			@RequestParam(name = "country", required = false) String country,
			@RequestParam(name = "league", required = false) String league,
			@RequestParam(name = "home", required = false) String home,
			@RequestParam(name = "away", required = false) String away,
			@RequestParam(name = "h2hLimit", required = false) Integer h2hLimit) {
		if (leagueVisibility.isHidden(country, league)) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "このリーグは現在表示していません"));
		}
		try {
			return ResponseEntity.ok(service.get(country, league, home, away, h2hLimit));
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
		}
	}

	@GetMapping("/badges")
	public ResponseEntity<?> badges(
			@RequestParam(name = "country", required = false) String country,
			@RequestParam(name = "league", required = false) String league,
			@RequestParam(name = "home", required = false) String home,
			@RequestParam(name = "away", required = false) String away,
			@RequestParam(name = "round", required = false) String round) {
		if (leagueVisibility.isHidden(country, league)) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "このリーグは現在表示していません"));
		}
		return ResponseEntity.ok(badgeService.get(country, league, home, away, round));
	}
}
