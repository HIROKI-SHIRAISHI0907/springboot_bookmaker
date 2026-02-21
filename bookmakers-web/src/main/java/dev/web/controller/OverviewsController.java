// src/main/java/dev/web/controller/OverviewsController.java
package dev.web.controller;

import java.util.List;

import org.apache.coyote.BadRequestException;
import org.apache.ibatis.javassist.NotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_w003.OverviewAPIService;
import dev.web.api.bm_w003.OverviewResponse;
import dev.web.api.bm_w003.OverviewSummaryDTO;
import dev.web.api.bm_w003.ScheduleOverviewResponse;
import lombok.AllArgsConstructor;

/**
 * OverviewsControllerクラス
 *  - 月次サマリ: GET /api/overview/{teamEnglish}/{teamHash}
 *  - 試合概要:   GET /api/{country}/{league}/match/{seq}
 */
@RestController
@RequestMapping("/api/overview")
@AllArgsConstructor
public class OverviewsController {

	private final OverviewAPIService overviewAPIService;

	@GetMapping("/{teamEnglish}/{teamHash}")
	public ResponseEntity<?> getMonthlyOverview(
			@PathVariable String teamEnglish,
			@PathVariable String teamHash) throws BadRequestException, NotFoundException {

		List<OverviewResponse> items = overviewAPIService.getMonthlyOverview(teamEnglish, teamHash);
		return ResponseEntity.ok(new OverviewListResponse(items));
	}

	@GetMapping("/{teamEnglish}/{teamHash}/match/{seq}")
	public ResponseEntity<?> getScheduleOverview(
			@PathVariable String teamEnglish,
			@PathVariable String teamHash,
			@PathVariable long seq) throws BadRequestException, NotFoundException {

		ScheduleOverviewResponse result = overviewAPIService.getScheduleOverview(teamEnglish, teamHash, seq);
		return ResponseEntity.ok(new ScheduleOverviewResponse(result.getMatch(), result.getSurfaces()));
	}

	@GetMapping("/{teamEnglish}/{teamHash}/stats/summary")
	public ResponseEntity<?> getOverviewStatSummary(
			@PathVariable String teamEnglish,
			@PathVariable String teamHash) throws BadRequestException, NotFoundException {

		List<OverviewSummaryDTO> items = overviewAPIService.getOverviewSummary(teamEnglish, teamHash);
		return ResponseEntity.ok(items);
	}

	static class OverviewListResponse {
		private List<OverviewResponse> items;

		public OverviewListResponse(List<OverviewResponse> items) {
			this.items = items;
		}

		public List<OverviewResponse> getItems() {
			return items;
		}

		public void setItems(List<OverviewResponse> items) {
			this.items = items;
		}
	}
}
