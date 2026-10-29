package dev.web.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_a034.LeagueTeamMoveRequest;
import dev.web.api.bm_a034.LeagueTeamMoveResponse;
import dev.web.api.bm_a034.LeagueTeamRemoveRequest;
import dev.web.api.bm_a034.LeagueTeamResponse;
import dev.web.api.bm_a034.LeagueTeamService;
import lombok.RequiredArgsConstructor;

/**
 * リーグ所属チーム付け替え設定（LeagueTeamPage.tsx）
 * <ul>
 *   <li>GET  /api/admin/league-team/countries   国の一覧（リーグ数つき）</li>
 *   <li>GET  /api/admin/league-team?country=    国のリーグ一覧と所属チーム</li>
 *   <li>POST /api/admin/league-team/move        チームの所属リーグを付け替える</li>
 *   <li>POST /api/admin/league-team/remove      チームをリーグから外す（論理削除: del_flg = '1'）</li>
 * </ul>
 * @author shiraishitoshio
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/league-team")
public class AdminLeagueTeamController {

	private final LeagueTeamService service;

	@GetMapping("/countries")
	public ResponseEntity<List<LeagueTeamResponse.Country>> countries() {
		return ResponseEntity.ok(service.getCountries());
	}

	@GetMapping
	public ResponseEntity<LeagueTeamResponse> leagues(@RequestParam(name = "country") String country) {
		return ResponseEntity.ok(service.getLeagues(country));
	}

	@PostMapping("/move")
	public ResponseEntity<LeagueTeamMoveResponse> move(@RequestBody LeagueTeamMoveRequest req) {
		return ResponseEntity.ok(service.move(req));
	}

	@PostMapping("/remove")
	public ResponseEntity<LeagueTeamMoveResponse> remove(@RequestBody LeagueTeamRemoveRequest req) {
		return ResponseEntity.ok(service.remove(req));
	}
}
