package dev.web.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_a038.MatchDetailReferenceService;
import lombok.RequiredArgsConstructor;

/**
 * 統計詳細データ参照（MatchDetailReferencePage.tsx  /admin/match-detail。2026-11 追加）
 * <ul>
 *   <li>GET /v1/api/admin/match-detail?keyword=&amp;dateFrom=&amp;dateTo=&amp;errorsOnly=&amp;offset=&amp;limit=
 *       match_detail_match の一覧（各テーブルの行数つき）。入力エラーは 400 { message }</li>
 * </ul>
 * 試合ごとの中身は GET /v1/api/match-detail/extra?matchId=（{@link MatchDetailController}）。
 * @author shiraishitoshio
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminMatchDetailReferenceController {

	private final MatchDetailReferenceService service;

	@GetMapping("/match-detail")
	public ResponseEntity<?> search(
			@RequestParam(name = "keyword", required = false) String keyword,
			@RequestParam(name = "dateFrom", required = false) String dateFrom,
			@RequestParam(name = "dateTo", required = false) String dateTo,
			@RequestParam(name = "errorsOnly", required = false) Boolean errorsOnly,
			@RequestParam(name = "offset", required = false) Integer offset,
			@RequestParam(name = "limit", required = false) Integer limit) {
		try {
			return ResponseEntity.ok(service.search(keyword, dateFrom, dateTo, errorsOnly, offset, limit));
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
		}
	}
}
