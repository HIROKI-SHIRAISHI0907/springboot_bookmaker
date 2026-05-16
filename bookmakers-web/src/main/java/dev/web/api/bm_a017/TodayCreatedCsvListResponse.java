package dev.web.api.bm_a017;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class TodayCreatedCsvListResponse {

	/** 対象日 */
	private String targetDate;

	/** 件数 */
	private int count;

	/** item */
	private List<TodayCreatedCsvItemResource> items = new ArrayList<>();

}
