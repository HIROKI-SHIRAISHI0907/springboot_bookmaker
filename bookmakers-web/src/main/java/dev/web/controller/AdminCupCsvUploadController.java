package dev.web.controller;

import java.io.IOException;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import dev.web.api.bm_a031.UnauthorizedUploadException;
import dev.web.api.bm_a036.CupCompetitionResponse;
import dev.web.api.bm_a036.CupCompetitionService;
import dev.web.api.bm_a036.CupCsvUploadResponse;
import dev.web.api.bm_a036.CupCsvUploadService;
import dev.web.util.CsvUtil;
import lombok.RequiredArgsConstructor;

/**
 * 大会一括アップロード（CupCsvUploadPage.tsx）
 * <ul>
 *   <li>GET  /v1/api/admin/csv/upload/cup/format  フォーマット（cup_yyyyMMddHHmmss.zip。中に見出し行だけの CSV）</li>
 *   <li>POST /v1/api/admin/csv/upload/cup/url-json  登録した大会（大会名・URL）を S3 の b026_cup_competition.json に出力
 *       （結果タブのスクレイパー CupResultData_aws_S3.py が読む。200: 出力した / 400: 大会が無い / 500: S3 に置けない）</li>
 *   <li>POST /v1/api/admin/csv/upload/cup         zip のアップロード（multipart/form-data、項目名 file。Authorization: Bearer {JWT}）
 *       <ul>
 *         <li>200: 反映した（同じ大会URL の大会は大会名を更新、無ければ登録）</li>
 *         <li>400: ファイル名が決まりに合わない・CSV が読めない・大会名か大会URL が空の行がある など（どの行も反映しない）</li>
 *         <li>401: 未ログイン</li>
 *       </ul>
 *   </li>
 * </ul>
 * <p>他の CSV アップロード（AdminCsvDataUploadController の /{masterName}/format）より、こちらの /cup/format が優先される。</p>
 * @author shiraishitoshio
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/csv/upload/cup")
public class AdminCupCsvUploadController {

	private final CupCsvUploadService service;

	private final CupCompetitionService competitionService;

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<CupCsvUploadResponse> upload(
			@RequestParam("file") MultipartFile file,
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
		CupCsvUploadResponse res = service.upload(file, authorization);
		return CupCsvUploadResponse.STATUS_SUCCESS.equals(res.getStatus())
				? ResponseEntity.ok(res)
				: ResponseEntity.badRequest().body(res);
	}

	@PostMapping("/url-json")
	public ResponseEntity<CupCompetitionResponse.Result> urlJson() {
		CupCompetitionResponse.Result r = competitionService.exportUrlJson();
		if ("0".equals(r.getResponseCode())) {
			return ResponseEntity.ok(r);
		}
		return r.getMessage() != null && r.getMessage().startsWith("S3")
				? ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(r)
				: ResponseEntity.badRequest().body(r);
	}

	@GetMapping("/format")
	public ResponseEntity<byte[]> format() throws IOException {
		CsvUtil.CsvFile zip = service.downloadFormat();
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.parseMediaType("application/zip"));
		headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + zip.getFileName() + "\"");
		headers.set(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION);
		return ResponseEntity.ok().headers(headers).body(zip.getBytes());
	}

	/** 未ログイン → 401 { message } */
	@ExceptionHandler(UnauthorizedUploadException.class)
	public ResponseEntity<Map<String, String>> handleUnauthorized(UnauthorizedUploadException e) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", e.getMessage()));
	}
}
