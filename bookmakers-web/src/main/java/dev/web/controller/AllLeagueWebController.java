package dev.web.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_a005.AllLeagueBatchRequest;
import dev.web.api.bm_a005.AllLeagueBatchResponse;
import dev.web.api.bm_a005.AllLeagueDTO;
import dev.web.api.bm_a005.AllLeagueRequest;
import dev.web.api.bm_a005.AllLeagueResponse;
import dev.web.api.bm_a005.AllLeagueService;
import lombok.RequiredArgsConstructor;

/**
 * AllLeague取得用
 * @author shiraishitoshio
 *
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/api")
public class AllLeagueWebController {

	private final AllLeagueService service;

	@PatchMapping("/all-league-master")
	public ResponseEntity<AllLeagueResponse> patch(@RequestBody AllLeagueRequest req) {
		AllLeagueResponse res = service.upsert(req.getCountry(), req.getLeague(), req.getLogicFlg(), req.getDispFlg());

		HttpStatus status = switch (res.getResponseCode()) {
		case "200" -> HttpStatus.OK;
		case "400" -> HttpStatus.BAD_REQUEST;
		case "404" -> HttpStatus.NOT_FOUND;
		case "409" -> HttpStatus.CONFLICT;
		default -> HttpStatus.INTERNAL_SERVER_ERROR;
		};

		return ResponseEntity.status(status).body(res);
	}

	@PatchMapping("/all-league-master/batch")
	public ResponseEntity<AllLeagueBatchResponse> patchBatch(@RequestBody AllLeagueBatchRequest req) {
		if (req.getItems() == null || req.getItems().isEmpty()) {
			return ResponseEntity.badRequest().body(
					new AllLeagueBatchResponse("400", 0, 0, 0, List.of()));
		}

		int total = req.getItems().size();
		int success = 0;

		var results = new java.util.ArrayList<AllLeagueBatchResponse.ItemResult>();

		for (AllLeagueRequest item : req.getItems()) {
			try {
				AllLeagueResponse r = service.upsert(
						item.getCountry(),
						item.getLeague(),
						item.getLogicFlg(),
						item.getDispFlg());

				if ("200".equals(r.getResponseCode()))
					success++;

				results.add(new AllLeagueBatchResponse.ItemResult(
						item.getCountry(),
						item.getLeague(),
						r.getResponseCode(),
						r.getMessage()));
			} catch (Exception e) {
				results.add(new AllLeagueBatchResponse.ItemResult(
						item.getCountry(),
						item.getLeague(),
						"500",
						e.getMessage()));
			}
		}

		int failed = total - success;
		// 全部成功=200、失敗混在=207(マルチステータス)、全部失敗でも 207/500 どちらでもOK
		String code = (failed == 0) ? "200" : "207";
		HttpStatus status = (failed == 0) ? HttpStatus.OK : HttpStatus.MULTI_STATUS;

		return ResponseEntity.status(status).body(
				new AllLeagueBatchResponse(code, total, success, failed, results));
	}

	@GetMapping("/all-league-master")
	public ResponseEntity<List<AllLeagueDTO>> findAll() {
		return ResponseEntity.ok(service.findAll());
	}
}
