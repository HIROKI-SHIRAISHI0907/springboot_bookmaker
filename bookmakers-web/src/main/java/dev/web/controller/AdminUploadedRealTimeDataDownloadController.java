package dev.web.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_a027.UploadedRealTimeDataDownloadRequest;
import dev.web.api.bm_a027.UploadedRealTimeDataDownloadSearchCondition;
import dev.web.api.bm_a027.UploadedRealTimeDataDownloadSearchRequest;
import dev.web.api.bm_a027.UploadedRealTimeDataDownloadSearchResponse;
import dev.web.api.bm_a027.UploadedRealTimeDataDownloadService;

/**
 * アップロードされたリアルタイムデータzipファイルをダウンロードできるコントローラー
 * @author shiraishitoshio
 *
 */
@RestController
@RequestMapping("/api/admin")
public class AdminUploadedRealTimeDataDownloadController {

	@Autowired
	private UploadedRealTimeDataDownloadService uploadedRealTimeDataDownloadService;

	/**
	 * real-time-data の初期表示用一覧を取得する（検索条件なし）。
	 *
	 * GET /api/real-time-data/upload/init
	 */
	@GetMapping("/real-time-data/upload/init")
	public ResponseEntity<List<UploadedRealTimeDataDownloadSearchResponse>> init() {
		return ResponseEntity.ok(uploadedRealTimeDataDownloadService.init());
	}

	/**
	 * real-time-data を条件検索する（指定された条件のみ WHERE に効く）。
	 *
	 * GET /api/real-time-data/upload/search
	 */
	@GetMapping("/real-time-data/upload/search")
	public ResponseEntity<List<UploadedRealTimeDataDownloadSearchResponse>> search(
			@ModelAttribute UploadedRealTimeDataDownloadSearchRequest cond) {
		UploadedRealTimeDataDownloadSearchCondition condition = new
				UploadedRealTimeDataDownloadSearchCondition();
		condition.setUploadDate(cond.getUploadDate());
		condition.setCountry(cond.getCountry());
		condition.setLeague(cond.getLeague());
		condition.setFinFlg(cond.isFinFlg());
		return ResponseEntity.ok(uploadedRealTimeDataDownloadService.search(condition));
	}

	/**
	 * real-time-data をダウンロードする（ファイル名一致のみ）。
	 *
	 * GET /api/real-time-data/upload/download
	 */
	@GetMapping("/real-time-data/upload/download")
	public ResponseEntity<Resource> download(
	        @ModelAttribute UploadedRealTimeDataDownloadRequest cond) {
	    return uploadedRealTimeDataDownloadService.download(cond);
	}

}
