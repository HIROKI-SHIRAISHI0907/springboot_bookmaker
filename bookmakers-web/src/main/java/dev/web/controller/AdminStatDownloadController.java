package dev.web.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_a024.StatDownloadRequest;
import dev.web.api.bm_a024.StatDownloadService;

@RestController
@RequestMapping("/api/admin")
public class AdminStatDownloadController {

	private final StatDownloadService statDownloadService;

	public AdminStatDownloadController(
			StatDownloadService statDownloadService) {
		this.statDownloadService = statDownloadService;
	}

	/**
	 * 管理者による統計CSVバックアップZIPから、
	 * 指定した国リーグ名のCSVのみ再ZIP化してダウンロード返却する
	 * <p>
	 * curl -X POST "http://localhost:8080/v1/api/admin/stat-download" \
  	 * -H "Content-Type: application/json; charset=UTF-8" \
  	 * -H "Accept: application/zip" \
  	 * -d '{
     * "zipFileName": "season_delete_backup_20260707140718.zip",
     * "folder": "EachCsvTransaction",
     * "countryLeagueName": "サンプル国_サンプルリーグ"
  	 * }' \
  	 * --output ~/Downloads/filtered_stat.zip
	 * </p>
	 */
	@PostMapping("/stat-download")
	public ResponseEntity<byte[]> download(@RequestBody StatDownloadRequest req) {
		return statDownloadService.downloadFilteredZip(req);
	}
}
