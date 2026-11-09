package dev.web.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_a037.SubLeagueSeasonDTO;
import dev.web.api.bm_a037.SubLeagueSeasonSaveRequest;
import dev.web.api.bm_a037.SubLeagueSeasonService;
import lombok.RequiredArgsConstructor;

/**
 * サブリーグ保有国リーグ登録設定（SubLeagueSeasonPage.tsx  /admin/sub-league-season。2026-11-09 追加）
 * <ul>
 *   <li>GET  /v1/api/admin/sub-league-season       国・リーグ ごとの設定（status = UNSET / HAS / NONE、subLeagues）</li>
 *   <li>POST /v1/api/admin/sub-league-season       { items: [{ country, league, hasSubLeague, subLeagues }] } 変更した 国・リーグ だけ。
 *       入力エラーは 400 { message }（1 件でもエラーなら何も更新しない）</li>
 *   <li>POST /v1/api/admin/sub-league-season/skip  設定しない（未設定の 国・リーグ を全部「サブリーグなし」にする）</li>
 * </ul>
 * @author shiraishitoshio
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminSubLeagueSeasonController {

	private final SubLeagueSeasonService service;

	@GetMapping("/sub-league-season")
	public List<SubLeagueSeasonDTO> findAll() {
		return service.findAll();
	}

	@PostMapping(value = "/sub-league-season", consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<?> save(@RequestBody SubLeagueSeasonSaveRequest request) {
		try {
			return ResponseEntity.ok(service.save(request));
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
		}
	}

	@PostMapping(value = "/sub-league-season/skip", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<?> skip() {
		return ResponseEntity.ok(service.skipUnset());
	}
}
