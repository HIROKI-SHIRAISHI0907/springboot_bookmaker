package dev.web.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_u001.StatSizeFinalizeRequest;
import dev.web.api.bm_u001.StatSizeFinalizeResponse;
import dev.web.api.bm_u001.StatSizeFinalizeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * StatSizeFinalizeController
 * @author shiraishitoshio
 *
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class StatSizeFinalizeController {

	private final StatSizeFinalizeService statSizeFinalizeService;

	/**
	 * 選択肢登録・更新
	 * @param req リクエスト
	 * @return レスポンス
	 */
	@PostMapping("/sub-input")
	public ResponseEntity<StatSizeFinalizeResponse> register(
			@Valid @RequestBody StatSizeFinalizeRequest req) {

		StatSizeFinalizeResponse res = statSizeFinalizeService.setStatFinalize(req);

		String code = res.getResponseCode();
		if ("200".equals(code)) {
			return ResponseEntity.ok(res);
		}
		if ("400".equals(code)) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(res);
		}
		if ("404".equals(code)) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(res);
		}

		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(res);
	}
}
