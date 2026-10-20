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

import dev.web.api.bm_a033.LeagueLevelDTO;
import dev.web.api.bm_a033.LeagueLevelSaveRequest;
import dev.web.api.bm_a033.LeagueLevelService;
import lombok.RequiredArgsConstructor;

/**
 * リーグレベル情報反映設定（LeagueLevelPage.tsx  /admin/league-level）
 * <ul>
 *   <li>GET  /v1/api/admin/league-level  国・リーグ ごとのリーグレベル（country_league_master.league_level）</li>
 *   <li>POST /v1/api/admin/league-level  { items: [{ country, league, leagueLevel }] } 変更した 国・リーグ だけ。
 *       国・リーグ の全行に同じ値を入れる。入力エラーは 400 { message }（1 件でもエラーなら何も更新しない）</li>
 * </ul>
 * @author shiraishitoshio
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminLeagueLevelWebController {

	private final LeagueLevelService service;

	@GetMapping("/league-level")
	public List<LeagueLevelDTO> findAll() {
		return service.findAll();
	}

	@PostMapping(value = "/league-level", consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<?> save(@RequestBody LeagueLevelSaveRequest request) {
		try {
			return ResponseEntity.ok(service.save(request));
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
		}
	}
}
