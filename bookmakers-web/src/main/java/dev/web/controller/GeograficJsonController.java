package dev.web.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_a012.FinGettingRequest;
import dev.web.api.bm_a012.FinGettingService;
import dev.web.api.bm_w013.StatResponseResource;
import lombok.RequiredArgsConstructor;

/**
 * Json作成タスク実行用（地理API）
 * @author shiraishitoshio
 *
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class GeograficJsonController {

	private final FinGettingService finGettingService;

	/**
	 * /geografic-json を叩いたら B010 のFargateタスクを起動する
	 * @throws Exception
	 */
	@PostMapping("/geografic-json")
	public ResponseEntity<StatResponseResource> execute(
			@RequestBody FinGettingRequest req) throws Exception {

		// JSONをupload
		finGettingService.convertAndUpload(req);

		StatResponseResource res = new StatResponseResource();
		// あなたのDTO設計に合わせて詰めてOK
		res.setReturnCd("ACCEPTED");

		return ResponseEntity.ok(res);

	}

}
