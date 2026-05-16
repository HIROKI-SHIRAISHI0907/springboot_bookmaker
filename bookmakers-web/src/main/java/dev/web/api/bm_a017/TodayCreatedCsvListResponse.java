package dev.web.api.bm_a017;

import java.util.ArrayList;
import java.util.List;

public class TodayCreatedCsvListResponse {

	private int count;
	private List<TodayCreatedCsvItemResource> items = new ArrayList<>();

	public int getCount() {
		return count;
	}

	public void setCount(int count) {
		this.count = count;
	}

	public List<TodayCreatedCsvItemResource> getItems() {
		return items;
	}

	public void setItems(List<TodayCreatedCsvItemResource> items) {
		this.items = items;
	}
}
