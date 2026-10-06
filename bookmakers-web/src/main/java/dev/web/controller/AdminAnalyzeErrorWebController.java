package dev.web.controller;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_a099.AnalyzeErrorBatchResponse;
import dev.web.api.bm_a099.AnalyzeErrorDTO;
import dev.web.api.bm_a099.AnalyzeErrorExportResult;
import dev.web.api.bm_a099.AnalyzeErrorListResponse;
import dev.web.api.bm_a099.AnalyzeErrorResolveRequest;
import dev.web.api.bm_a099.AnalyzeErrorResponse;
import dev.web.api.bm_a099.AnalyzeErrorSearchCondition;
import dev.web.api.bm_a099.AnalyzeErrorService;
import dev.web.api.bm_a099.AnalyzeErrorSummaryDTO;
import lombok.RequiredArgsConstructor;

/**
 * 統計処理で登録できなかった試合（analyze_error_match）の管理画面用
 * <ul>
 *   <li>GET   /api/analyze-error/matches            一覧（条件・OFFSET ページング）</li>
 *   <li>GET   /api/analyze-error/matches/export     明細ダウンロード（CSV。一覧と同じ条件、seqs で行指定も可）</li>
 *   <li>GET   /api/analyze-error/matches/{seq}      詳細（スタックトレース付き）</li>
 *   <li>GET   /api/analyze-error/summary            BM × 種別 × 国・リーグ × 項目ごとの件数</li>
 *   <li>PATCH /api/analyze-error/matches/{seq}      対応済み / 未対応に戻す / メモ</li>
 *   <li>PATCH /api/analyze-error/matches/resolve/batch  まとめて対応済み / 未対応に戻す</li>
 * </ul>
 * @author shiraishitoshio
 *
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/analyze-error")
public class AdminAnalyzeErrorWebController {

	private final AnalyzeErrorService service;

	@GetMapping("/matches")
	public ResponseEntity<AnalyzeErrorListResponse> search(
			@RequestParam(name = "status", required = false, defaultValue = "unresolved") String status,
			@RequestParam(name = "bmNumber", required = false) String bmNumber,
			@RequestParam(name = "errorType", required = false) String errorType,
			@RequestParam(name = "country", required = false) String country,
			@RequestParam(name = "league", required = false) String league,
			@RequestParam(name = "errorField", required = false) String errorField,
			@RequestParam(name = "keyword", required = false) String keyword,
			@RequestParam(name = "offset", required = false, defaultValue = "0") Integer offset,
			@RequestParam(name = "limit", required = false) Integer limit) {

		AnalyzeErrorSearchCondition cond = new AnalyzeErrorSearchCondition();
		cond.setStatus(status);
		cond.setBmNumber(bmNumber);
		cond.setErrorType(errorType);
		cond.setCountry(country);
		cond.setLeague(league);
		cond.setErrorField(errorField);
		cond.setKeyword(keyword);

		return ResponseEntity.ok(service.search(cond, offset, limit));
	}

	/**
	 * 明細ダウンロード（CSV）。/matches/{seq} より先に書く（"export" が seq として扱われないように。
	 * Spring はリテラルのパスを優先するが、読みやすさのため）。
	 * 件数はヘッダー X-Total-Count（条件に合う全件）・X-Exported-Count（出力件数）で返す。
	 */
	@GetMapping("/matches/export")
	public ResponseEntity<byte[]> export(
			@RequestParam(name = "status", required = false, defaultValue = "unresolved") String status,
			@RequestParam(name = "bmNumber", required = false) String bmNumber,
			@RequestParam(name = "errorType", required = false) String errorType,
			@RequestParam(name = "country", required = false) String country,
			@RequestParam(name = "league", required = false) String league,
			@RequestParam(name = "errorField", required = false) String errorField,
			@RequestParam(name = "keyword", required = false) String keyword,
			@RequestParam(name = "seqs", required = false) List<String> seqs) {

		AnalyzeErrorSearchCondition cond = new AnalyzeErrorSearchCondition();
		cond.setStatus(status);
		cond.setBmNumber(bmNumber);
		cond.setErrorType(errorType);
		cond.setCountry(country);
		cond.setLeague(league);
		cond.setErrorField(errorField);
		cond.setKeyword(keyword);
		if (seqs != null) {
			List<String> s = seqs.stream().filter(v -> v != null && !v.isBlank()).map(String::trim).distinct().collect(Collectors.toList());
			if (s.size() > AnalyzeErrorService.MAX_BATCH) {
				return ResponseEntity.badRequest().build();
			}
			cond.setSeqs(s.isEmpty() ? null : s);
		}

		AnalyzeErrorExportResult res = service.exportCsv(cond);

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
		headers.setContentDisposition(ContentDisposition.attachment()
				.filename(res.getFileName(), StandardCharsets.UTF_8).build());
		headers.add("X-Total-Count", String.valueOf(res.getTotal()));
		headers.add("X-Exported-Count", String.valueOf(res.getExported()));
		// 別オリジン（Vite の開発サーバーなど）から fetch したときに画面で読めるように
		headers.add(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "Content-Disposition, X-Total-Count, X-Exported-Count");
		return ResponseEntity.ok().headers(headers).body(res.getBody());
	}

	@GetMapping("/matches/{seq}")
	public ResponseEntity<AnalyzeErrorDTO> findBySeq(@PathVariable("seq") String seq) {
		AnalyzeErrorDTO dto = service.findBySeq(seq);
		if (dto == null) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok(dto);
	}

	@GetMapping("/summary")
	public ResponseEntity<List<AnalyzeErrorSummaryDTO>> summary() {
		return ResponseEntity.ok(service.findSummary());
	}

	@PatchMapping("/matches/{seq}")
	public ResponseEntity<AnalyzeErrorResponse> patch(@PathVariable("seq") String seq,
			@RequestBody AnalyzeErrorResolveRequest req) {
		AnalyzeErrorResponse res = service.updateResolved(seq, req.getResolvedFlg(), req.getResolvedBy(), req.getNote());
		return ResponseEntity.status(toStatus(res.getResponseCode())).body(res);
	}

	@PatchMapping("/matches/resolve/batch")
	public ResponseEntity<AnalyzeErrorBatchResponse> patchBatch(@RequestBody AnalyzeErrorResolveRequest req) {
		AnalyzeErrorBatchResponse res = service.updateResolvedBatch(req);
		return ResponseEntity.status(toStatus(res.getResponseCode())).body(res);
	}

	private static HttpStatus toStatus(String code) {
		return switch (code == null ? "" : code) {
		case "200" -> HttpStatus.OK;
		case "207" -> HttpStatus.MULTI_STATUS;
		case "400" -> HttpStatus.BAD_REQUEST;
		case "404" -> HttpStatus.NOT_FOUND;
		case "409" -> HttpStatus.CONFLICT;
		default -> HttpStatus.INTERNAL_SERVER_ERROR;
		};
	}
}
