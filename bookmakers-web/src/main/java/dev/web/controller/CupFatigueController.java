package dev.web.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.cupFatigue.bm_w007.CupFatigueResponse;
import dev.web.api.cupFatigue.bm_w007.CupFatigueService;
import lombok.RequiredArgsConstructor;

/**
 * 直近の大会（ACL など）出場（UpcomingDetail.tsx / MatchDetail.tsx の CupFatigueBox）
 * <ul>
 *   <li>GET /v1/api/cup-fatigue?country=&amp;league=&amp;home=&amp;away=&amp;at=&amp;days=
 *       at = この試合の開始日時（JST, yyyy-MM-dd HH:mm。無ければ今）、days = 前後何日を見るか（既定 7・最大 14）。
 *       未ログインでも可。</li>
 * </ul>
 * @author shiraishitoshio
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/cup-fatigue")
public class CupFatigueController {

	private final CupFatigueService service;

	@GetMapping
	public ResponseEntity<CupFatigueResponse> get(
			@RequestParam(name = "country", required = false) String country,
			@RequestParam(name = "league", required = false) String league,
			@RequestParam(name = "home", required = false) String home,
			@RequestParam(name = "away", required = false) String away,
			@RequestParam(name = "at", required = false) String at,
			@RequestParam(name = "days", required = false) Integer days) {
		return ResponseEntity.ok(service.get(country, league, home, away, at, days));
	}
}
