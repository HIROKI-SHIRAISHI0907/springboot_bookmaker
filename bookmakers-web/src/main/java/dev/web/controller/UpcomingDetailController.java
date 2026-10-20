package dev.web.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.upcomingDetail.bm_w001.UpcomingDetailService;
import lombok.RequiredArgsConstructor;

/**
 * UpcomingDetail.tsx（これからの試合の詳細: 過去の対戦成績・会場・順位表・ゴール数などの統計）  未ログインでも可
 * <ul>
 *   <li>GET /v1/api/upcoming-detail?country=&amp;league=&amp;home=&amp;away=&amp;h2hLimit=10
 *       home・away が空、または同じチームのときは 400 { message }</li>
 * </ul>
 * @author shiraishitoshio
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/upcoming-detail")
public class UpcomingDetailController {

	private final UpcomingDetailService service;

	@GetMapping
	public ResponseEntity<?> detail(
			@RequestParam(name = "country", required = false) String country,
			@RequestParam(name = "league", required = false) String league,
			@RequestParam(name = "home", required = false) String home,
			@RequestParam(name = "away", required = false) String away,
			@RequestParam(name = "h2hLimit", required = false) Integer h2hLimit) {
		try {
			return ResponseEntity.ok(service.get(country, league, home, away, h2hLimit));
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
		}
	}
}
