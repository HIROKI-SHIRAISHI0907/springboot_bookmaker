package dev.web.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_a017.TodayCreatedCsvListResponse;
import dev.web.api.bm_a017.TodayCreatedCsvService;

/**
 * 本日作成されたCSV情報を取得
 * @author shiraishitoshio
 *
 */
@RestController
@RequestMapping("/api/admin")
public class AdminTodayCreatedCsvController {

	@Autowired
	private TodayCreatedCsvService todayCreatedCsvAdminService;

	@GetMapping("/csv/today")
	public TodayCreatedCsvListResponse getTodayCreatedCsvs(
			@RequestParam(value = "targetDate", required = false) String targetDate,
            @RequestParam(value = "startOffset", required = false) Integer startOffset,
            @RequestParam(value = "endOffset", required = false) Integer endOffset) {
		return todayCreatedCsvAdminService.getCreatedCsvs(targetDate, startOffset, endOffset);
	}
}
