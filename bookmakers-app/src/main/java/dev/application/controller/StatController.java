package dev.application.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import dev.application.analyze.common.entity.StatRequestResource;
import dev.application.analyze.common.entity.StatResponseResource;
import dev.application.main.service.CoreStat;
import lombok.RequiredArgsConstructor;

/**
 * 統計分析コントローラークラス
 * @author shiraishitoshio
 *
 */
@RestController
@RequiredArgsConstructor
public class StatController {

	@Autowired
	private CoreStat statService;

	/**
	 * 実行メソッド
	 * @return
	 * @throws Exception
	 */
	@PostMapping("/stat")
	public ResponseEntity<StatResponseResource> execute(@RequestBody
			StatRequestResource requestResource) throws Exception {

		// 統計用データサービス
		//this.statService.execute();

		StatResponseResource response = new StatResponseResource();
		response.setReturnCd(null);

		return ResponseEntity.ok(response);

	}

}
