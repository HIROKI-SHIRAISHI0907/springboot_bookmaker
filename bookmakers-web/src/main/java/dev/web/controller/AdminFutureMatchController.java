package dev.web.controller;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_a030.FutureMatchAdminService;
import dev.web.api.bm_a030.FutureMatchesResponse;
import lombok.RequiredArgsConstructor;

/**
 * 指定日の試合予定一覧（管理画面: FutureMatchesByDatePage）
 *
 * <p>GET /api/future/admin/matches/date?date=yyyy-MM-dd&amp;offset=0&amp;limit=10</p>
 * （context-path が /v1 のため、画面からは /v1/api/future/admin/matches/date で呼ばれる）
 * @author shiraishitoshio
 *
 */
@RestController
@RequestMapping("/api/future/admin")
@RequiredArgsConstructor
public class AdminFutureMatchController {

	private final FutureMatchAdminService futureMatchAdminService;

	/**
	 * 指定日（日本時間）の future_master を試合開始日時順に返す。
	 *
	 * @param date 対象日（yyyy-MM-dd）。省略時は今日（日本時間）
	 * @param offset 取得開始位置（0 以上）
	 * @param limit 取得件数（1〜100。省略時 10）
	 */
	@GetMapping("/matches/date")
	public ResponseEntity<FutureMatchesResponse> findByDate(
			@RequestParam(name = "date", required = false) String date,
			@RequestParam(name = "offset", required = false, defaultValue = "0") int offset,
			@RequestParam(name = "limit", required = false, defaultValue = "10") int limit) {

		LocalDate target;
		try {
			target = (date == null || date.isBlank())
					? LocalDate.now(FutureMatchAdminService.JST)
					: LocalDate.parse(date.trim());
		} catch (DateTimeParseException e) {
			return ResponseEntity.badRequest().build();
		}

		return ResponseEntity.ok(futureMatchAdminService.findByDate(target, offset, limit));
	}
}
