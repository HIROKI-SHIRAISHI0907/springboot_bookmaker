package dev.web.controller;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_a032.FileUploadStatusDTO;
import dev.web.api.bm_a032.FileUploadStatusSearchCondition;
import dev.web.api.bm_a032.FileUploadStatusService;
import dev.web.util.CsvUtil;
import lombok.RequiredArgsConstructor;

/**
 * ファイルアップロード状況管理
 *
 * <ul>
 *   <li>GET /v1/api/admin/csv/upload/status?masterName=&amp;status=&amp;dateFrom=&amp;dateTo=&amp;fileName=&amp;loginUser=&amp;page=&amp;size=
 *       一覧（新しい順・エラー行なし）。条件の形が正しくなければ 400 { message }</li>
 *   <li>GET /v1/api/admin/csv/upload/status/{id} 詳細（エラー行を含む）。無ければ 404</li>
 *   <li>GET /v1/api/admin/csv/upload/status/{id}/error-file 異常終了のファイル（error/ の {名前}_error.zip。
 *       中の CSV に「エラー詳細」列付き）をダウンロード。異常終了でない・ファイルが無いときは 404 { message }</li>
 * </ul>
 *
 * @author shiraishitoshio
 */
@RestController
@RequestMapping("/api/admin/csv/upload/status")
@RequiredArgsConstructor
public class AdminFileUploadStatusController {

	private final FileUploadStatusService fileUploadStatusService;

	@GetMapping
	public ResponseEntity<?> search(
			@RequestParam(value = "masterName", required = false) String masterName,
			@RequestParam(value = "status", required = false) String status,
			@RequestParam(value = "dateFrom", required = false) String dateFrom,
			@RequestParam(value = "dateTo", required = false) String dateTo,
			@RequestParam(value = "fileName", required = false) String fileName,
			@RequestParam(value = "loginUser", required = false) String loginUser,
			@RequestParam(value = "page", required = false) Integer page,
			@RequestParam(value = "size", required = false) Integer size) {
		FileUploadStatusSearchCondition c = new FileUploadStatusSearchCondition();
		c.setMasterName(masterName);
		c.setStatus(status);
		c.setDateFrom(dateFrom);
		c.setDateTo(dateTo);
		c.setFileName(fileName);
		c.setLoginUser(loginUser);
		try {
			return ResponseEntity.ok(fileUploadStatusService.search(c, page, size));
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
		}
	}

	@GetMapping("/{id}")
	public ResponseEntity<FileUploadStatusDTO> find(@PathVariable("id") long id) {
		FileUploadStatusDTO d = fileUploadStatusService.find(id);
		return d == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(d);
	}

	/**
	 * 異常終了のファイルのダウンロード
	 * <p>{名前}_error.zip（中の CSV は元の列＋「エラー詳細」列）。直してそのままアップロードし直せる。</p>
	 */
	@GetMapping("/{id}/error-file")
	public ResponseEntity<?> downloadErrorFile(@PathVariable("id") long id) {
		CsvUtil.CsvFile file = fileUploadStatusService.downloadErrorFile(id);
		if (file == null) {
			return ResponseEntity.status(404)
					.contentType(MediaType.APPLICATION_JSON)
					.body(Map.of("message", "ダウンロードできるエラーファイルがありません。（異常終了でない、またはファイルを保管できなかった受付です）"));
		}
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.parseMediaType("application/zip"));
		headers.setContentDisposition(ContentDisposition.attachment()
				.filename(file.getFileName(), StandardCharsets.UTF_8).build());
		headers.setContentLength(file.getBytes().length);
		// 別オリジン（Vite の開発サーバーなど）から fetch したときに画面でファイル名を読めるように
		headers.add(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "Content-Disposition");
		return ResponseEntity.ok().headers(headers).body(file.getBytes());
	}
}
