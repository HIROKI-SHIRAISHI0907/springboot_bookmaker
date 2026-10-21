package dev.web.controller;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import dev.web.api.bm_a031.CsvDataUploadService;
import dev.web.api.bm_a031.CsvUploadResponse;
import dev.web.api.bm_a031.CsvUploadStatus;
import dev.web.api.bm_a031.DataCategoryFormatRequest;
import dev.web.api.bm_a031.UnauthorizedUploadException;
import dev.web.util.CsvUtil;
import lombok.RequiredArgsConstructor;

/**
 * CSVデータのアップロード
 *
 * <h3>アップロード</h3>
 * <p>POST /v1/api/admin/csv/upload/{season|team|future|realtime|dataCategory}（multipart/form-data、項目名 file）</p>
 * <p>Authorization: Bearer {JWT} を付けて送る（ログインユーザーは JWT から決める）。</p>
 * <ul>
 *   <li>zip だけ受け付ける。zip の名前は season_ / team_ / future_ / realtime_ で始め、中の CSV は zip と同じ名前にする
 *       （Mac の「圧縮」で付く 〇〇.csv.zip も可）。</li>
 *   <li>realtime（リアルタイムデータ）は非表示・再表示だけ。</li>
 *   <li>dataCategory（データカテゴリ統一）は処理区分 2（更新）の行だけ反映する。zip の名前は dataCategory_ で始める。</li>
 *   <li>200: 受け付けた（判定前。DB への反映はバッチサービス）</li>
 *   <li>400: ファイル名が決まりに合わない・CSV が読めない・データが無い・行エラーがある（どの行も反映しない）</li>
 *   <li>500: ファイルの保管（S3）に失敗した</li>
 *   <li>401: 未ログイン（Authorization: Bearer の JWT が無い・不正・ユーザーなし）。受け付けず、状況管理にも記録しない</li>
 * </ul>
 *
 * <h3>フォーマットのダウンロード</h3>
 * <p>GET /v1/api/admin/csv/upload/{season|team|future|realtime|dataCategory}/format</p>
 * <ul>
 *   <li>ヘッダー行だけの CSV を zip にして返す（例: season_20261018203000.zip の中に season_20261018203000.csv）。</li>
 *   <li>CSV に行を追記して、同じ名前のまま zip にし直せばアップロードできる。</li>
 * </ul>
 * <p>POST /v1/api/admin/csv/upload/dataCategory/format（JSON: { rows: [...] }）</p>
 * <ul>
 *   <li>データカテゴリ統一管理画面に表示中のデータを入れたフォーマット（dataCategory_yyyyMMddHHmmss.zip）を返す。</li>
 * </ul>
 *
 * @author shiraishitoshio
 */
@RestController
@RequestMapping("/api/admin/csv/upload")
@RequiredArgsConstructor
public class AdminCsvDataUploadController {

	private final CsvDataUploadService csvDataUploadService;

	/** シーズンデータ（country_league_season_master） */
	@PostMapping(value = "/season", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<CsvUploadResponse> uploadSeason(
			@RequestParam("file") MultipartFile file,
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
		return toResponse(csvDataUploadService.uploadSeason(file, authorization));
	}

	/** チームデータ（country_league_master） */
	@PostMapping(value = "/team", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<CsvUploadResponse> uploadTeam(
			@RequestParam("file") MultipartFile file,
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
		return toResponse(csvDataUploadService.uploadTeam(file, authorization));
	}

	/** 試合予定データ（future_master） */
	@PostMapping(value = "/future", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<CsvUploadResponse> uploadFuture(
			@RequestParam("file") MultipartFile file,
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
		return toResponse(csvDataUploadService.uploadFuture(file, authorization));
	}

	/** リアルタイムデータ（static_data。非表示・再表示のみ） */
	@PostMapping(value = "/realtime", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<CsvUploadResponse> uploadRealtime(
			@RequestParam("file") MultipartFile file,
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
		return toResponse(csvDataUploadService.uploadRealtime(file, authorization));
	}

	/** データカテゴリ統一（static_data・future_master のカテゴリ書き換え。処理区分 2 の行だけ） */
	@PostMapping(value = "/dataCategory", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<CsvUploadResponse> uploadDataCategory(
			@RequestParam("file") MultipartFile file,
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
		return toResponse(csvDataUploadService.uploadDataCategory(file, authorization));
	}

	/**
	 * データカテゴリ統一: 画面に表示中のデータを入れたフォーマットのダウンロード
	 * <p>本文の rows を画面の並びのまま CSV にして zip で返す（dataCategory_yyyyMMddHHmmss.zip）。</p>
	 */
	@PostMapping(value = "/dataCategory/format", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<byte[]> downloadDataCategoryFormat(@RequestBody DataCategoryFormatRequest request)
			throws IOException {
		return zipResponse(csvDataUploadService.createDataCategoryFormat(request));
	}

	/**
	 * フォーマット（ヘッダー行だけの CSV を zip にしたもの）のダウンロード
	 * @param masterName season / team / future / realtime
	 */
	@GetMapping("/{masterName}/format")
	public ResponseEntity<byte[]> downloadFormat(@PathVariable("masterName") String masterName) throws IOException {
		CsvUtil.CsvFile zip = csvDataUploadService.downloadFormat(masterName);
		if (zip == null) {
			return ResponseEntity.notFound().build();
		}
		return zipResponse(zip);
	}

	/** zip を返す（画面の fetch からファイル名を読めるように Content-Disposition を公開する） */
	private static ResponseEntity<byte[]> zipResponse(CsvUtil.CsvFile zip) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.parseMediaType("application/zip"));
		headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + zip.getFileName() + "\"");
		// 画面（fetch）からファイル名を読めるようにする
		headers.set(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION);
		return ResponseEntity.ok().headers(headers).body(zip.getBytes());
	}

	/** 未ログイン → 401 { message } */
	@ExceptionHandler(UnauthorizedUploadException.class)
	public ResponseEntity<Map<String, String>> handleUnauthorized(UnauthorizedUploadException e) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", e.getMessage()));
	}

	/**
	 * 受付結果 → HTTP ステータス
	 * <ul>
	 *   <li>PENDING（受け付けた）→ 200</li>
	 *   <li>ERROR かつ phase=STORE（S3 保管失敗）→ 500</li>
	 *   <li>それ以外（バリデーションエラー）→ 400</li>
	 * </ul>
	 */
	private static ResponseEntity<CsvUploadResponse> toResponse(CsvUploadResponse res) {
		if (Objects.equals(res.getStatus(), CsvUploadStatus.PENDING)) {
			return ResponseEntity.ok(res);
		}
		if (Objects.equals(res.getErrorPhase(), CsvUploadStatus.PHASE_STORE)) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(res);
		}
		return ResponseEntity.badRequest().body(res);
	}
}