package dev.web.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.team.bm_w001.TeamResultsResponse;
import dev.web.api.team.bm_w001.TeamResultsService;
import dev.web.api.teamMember.bm_w001.TeamMembersResponse;
import dev.web.api.teamMember.bm_w001.TeamMembersService;
import lombok.RequiredArgsConstructor;

/**
 * TeamResults.tsx（チームの過去の結果・メンバー）  未ログインでも可
 * <ul>
 *   <li>GET /v1/api/team-results?country=&amp;league=&amp;team=&amp;limit=5  過去の結果</li>
 *   <li>GET /v1/api/team-results/members?country=&amp;league=&amp;team=      メンバー</li>
 * </ul>
 * @author shiraishitoshio
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/team-results")
public class TeamResultsController {

	private final TeamResultsService service;

	private final TeamMembersService membersService;

	@GetMapping
	public ResponseEntity<TeamResultsResponse> results(
			@RequestParam(name = "country") String country,
			@RequestParam(name = "league") String league,
			@RequestParam(name = "team") String team,
			@RequestParam(name = "limit", required = false) Integer limit) {
		return ResponseEntity.ok(service.getResults(country, league, team, limit));
	}

	@GetMapping("/members")
	public ResponseEntity<TeamMembersResponse> members(
			@RequestParam(name = "country") String country,
			@RequestParam(name = "league") String league,
			@RequestParam(name = "team") String team) {
		return ResponseEntity.ok(membersService.getMembers(country, league, team));
	}
}
