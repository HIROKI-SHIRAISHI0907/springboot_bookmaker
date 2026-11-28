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
import dev.web.api.bm_a040.RealtimeCsvInsertResponse;
import dev.web.api.bm_a040.RealtimeCsvInsertService;
import dev.web.util.CsvUtil;
import lombok.RequiredArgsConstructor;

/**
 * リアルタイムデータ CSV アップロード（RealtimeDataCsvUploadPage.tsx。static_data に直接登録。2026-11-28 追加）
 * <ul>
 *   <li>GET  /v1/api/admin/csv/upload/realtimeData/format  フォーマット（realtimedata_yyyyMMddHHmmss.zip）</li>
 *   <li>POST /v1/api/admin/csv/upload/realtimeData         zip のアップロード（multipart/form-data、項目名 file）
 *       200: 登録した / 400: エラー（どの行も登録しない） / 401: 未ログイン</li>
 * </ul>
 * @author shiraishitoshio
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/csv/upload/realtimeData")
public class AdminRealtimeCsvInsertController {

	private final RealtimeCsvInsertService service;

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<RealtimeCsvInsertResponse> upload(
			@RequestParam("file") MultipartFile file,
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
		RealtimeCsvInsertResponse res = service.upload(file, authorization);
		return RealtimeCsvInsertResponse.STATUS_SUCCESS.equals(res.getStatus())
				? ResponseEntity.ok(res)
				: ResponseEntity.badRequest().body(res);
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
