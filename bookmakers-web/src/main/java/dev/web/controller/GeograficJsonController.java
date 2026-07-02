package dev.web.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_a022.GeograficService;
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

	private final GeograficService geograficService;

	/**
	 * /geografic-json を叩いたら B010 のFargateタスクを起動する
	 * @throws Exception
	 */
	@PostMapping("/geografic-json")
	public ResponseEntity<StatResponseResource> execute() throws Exception {

		// JSONをupload
		String s3KeyString = geograficService.convertAndUpload();

		StatResponseResource res = new StatResponseResource();
		// あなたのDTO設計に合わせて詰めてOK
		res.setReturnCd((s3KeyString == null) ? "WARN: 対象のJSON出力対象データがありません。" : "ACCEPTED");

		return ResponseEntity.ok(res);

	}

}
