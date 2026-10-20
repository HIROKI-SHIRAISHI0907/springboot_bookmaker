package dev.web.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.headToHead.bm_w001.HeadToHeadService;
import lombok.RequiredArgsConstructor;

/**
 * 対戦成績（同じ 2 チームの過去の対戦）  未ログインでも可
 * <ul>
 *   <li>GET /v1/api/head-to-head?team=&amp;opponent=&amp;limit=10
 *       team 視点の通算・ホーム時・アウェー時の成績と、直近の対戦（新しい順・最大 50 件）。
 *       対戦が無ければ 0 件で 200。team・opponent が空・同じなら 400 { message }</li>
 * </ul>
 * @author shiraishitoshio
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/head-to-head")
public class HeadToHeadController {

	private final HeadToHeadService service;

	@GetMapping
	public ResponseEntity<?> get(
			@RequestParam(name = "team", required = false) String team,
			@RequestParam(name = "opponent", required = false) String opponent,
			@RequestParam(name = "limit", required = false) Integer limit) {
		try {
			return ResponseEntity.ok(service.get(team, opponent, limit));
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
		}
	}
}
