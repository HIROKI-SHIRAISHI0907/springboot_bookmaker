package dev.web.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_a010.TeamColorDTO;
import dev.web.api.bm_a010.TeamColorRequest;
import dev.web.api.bm_a010.TeamColorResponse;
import dev.web.api.bm_a010.TeamColorSearchCondition;
import dev.web.api.bm_a010.TeamColorService;
import lombok.RequiredArgsConstructor;

/**
 * TeamColor取得用
 * @author shiraishitoshio
 *
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class TeamColorWebController {

	private final TeamColorService service;

	/**
	 * team_color_master を全件取得する。
	 *
	 * GET /api/team-color-master
	 */
	@GetMapping("/team-color-master")
	public ResponseEntity<List<TeamColorDTO>> findAll() {
		return ResponseEntity.ok(service.findAll());
	}

	/**
	 * country_league_season_master を条件検索する（指定された条件のみ WHERE に効く）。
	 *
	 * GET /api/team-color-master/search
	 */
	@GetMapping("/team-color-master/search")
	public ResponseEntity<List<TeamColorDTO>> search(
			@ModelAttribute TeamColorSearchCondition cond) {
		return ResponseEntity.ok(service.search(cond));
	}

	@PostMapping("/team-color-master/update")
	public ResponseEntity<TeamColorResponse> update(@RequestBody TeamColorRequest dto) {
		return ResponseEntity.ok(service.update(dto));
	}
}
