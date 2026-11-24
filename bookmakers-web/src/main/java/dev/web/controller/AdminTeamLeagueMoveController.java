package dev.web.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_a039.TeamLeagueMoveConfirmRequest;
import dev.web.api.bm_a039.TeamLeagueMoveDTO;
import dev.web.api.bm_a039.TeamLeagueMoveService;
import lombok.RequiredArgsConstructor;

/**
 * チーム昇格・降格確認設定（TeamLeagueMovePage.tsx  /admin/team-league-move。2026-11-24 追加）
 * <ul>
 *   <li>GET  /v1/api/admin/team-league-move?all=false  未確定だけ（all=true で確定・取り消し済みも）</li>
 *   <li>POST /v1/api/admin/team-league-move/confirm?all=  { items: [{ id, moveType: UP|DOWN|OTHER }] } 確定</li>
 *   <li>POST /v1/api/admin/team-league-move/cancel?all=   { items: [{ id }] } 取り消し（誤検知）</li>
 * </ul>
 * 入力エラーは 400 { message }。
 * @author shiraishitoshio
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminTeamLeagueMoveController {

	private final TeamLeagueMoveService service;

	@GetMapping("/team-league-move")
	public List<TeamLeagueMoveDTO> findAll(@RequestParam(name = "all", defaultValue = "false") boolean all) {
		return service.findAll(all);
	}

	@PostMapping(value = "/team-league-move/confirm", consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<?> confirm(@RequestBody TeamLeagueMoveConfirmRequest request,
			@RequestParam(name = "all", defaultValue = "false") boolean all) {
		try {
			return ResponseEntity.ok(service.confirm(request, all));
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
		}
	}

	@PostMapping(value = "/team-league-move/cancel", consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<?> cancel(@RequestBody TeamLeagueMoveConfirmRequest request,
			@RequestParam(name = "all", defaultValue = "false") boolean all) {
		try {
			return ResponseEntity.ok(service.cancel(request, all));
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
		}
	}
}
