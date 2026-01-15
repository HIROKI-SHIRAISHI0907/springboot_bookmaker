package dev.web.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_w012.CountryLeagueUpdateRequest;
import dev.web.api.bm_w012.CountryLeagueUpdateResponse;
import dev.web.api.bm_w012.CountryLeagueUpdateService;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class CountryLeagueUpdateController {

	private final CountryLeagueUpdateService service;

	/**
	 * チームのリンク情報を更新する。
	 *
	 * PATCH /api/country-league-master
	 *
	 * @param req     リクエストボディ
	 * @return 更新結果レスポンス
	 */
	@PatchMapping("/country-league-master")
	public ResponseEntity<CountryLeagueUpdateResponse> updateCountryLeagueMaster(
			@RequestBody CountryLeagueUpdateRequest req) {

		CountryLeagueUpdateResponse res = service.patchLink(req);

		// responseCode に応じて HTTP Status を切り替える
		HttpStatus status = switch (res.getResponseCode()) {
			case "200" -> HttpStatus.OK;           // SUCCESS
			case "400" -> HttpStatus.BAD_REQUEST; // 入力不正
			case "404" -> HttpStatus.NOT_FOUND;   // NOT_FOUND
			case "409" -> HttpStatus.CONFLICT;    // LINK_ALREADY_USED
			default     -> HttpStatus.INTERNAL_SERVER_ERROR; // ERROR
		};

		return ResponseEntity.status(status).body(res);
	}
}
