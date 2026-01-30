package dev.web.controller;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_u001.AdminStatOrigin;
import dev.web.api.bm_u001.StatSizeFinalizeRequest;
import dev.web.api.bm_u001.StatSizeFinalizeResponse;

@RestController
@RequestMapping("/api/admin")
public class StatOriginalController {

	private final AdminStatOrigin adminStatOrigin;

	public StatOriginalController(
			@Qualifier("adminStatOriginUseCaseImpl") AdminStatOrigin adminStatOrigin) {
		this.adminStatOrigin = adminStatOrigin;
	}

	/**
	 * 管理者による表示統計データ狭窄管理
	 */
	@PostMapping("/stat-origin")
	public ResponseEntity<StatSizeFinalizeResponse> setStatFinalize(@RequestBody StatSizeFinalizeRequest req) {
		try {
			StatSizeFinalizeResponse res = adminStatOrigin.executeAll(req);
			return ResponseEntity.ok(res);
		} catch (Exception e) {
			StatSizeFinalizeResponse res = new StatSizeFinalizeResponse();
			res.setResponseCode("500");
			res.setMessage("サーバーエラーが発生しました。");
			return ResponseEntity.internalServerError().body(res);
		}
	}
}
