package dev.web.api.bm_a017;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dev.web.repository.bm.TodayCreatedCsvRepository;

@Service
public class TodayCreatedCsvService {

	@Autowired
	private TodayCreatedCsvRepository todayCreatedCsvRepository;

	public TodayCreatedCsvListResponse getTodayCreatedCsvs() {
		List<TodayCreatedCsvItemResource> rows = todayCreatedCsvRepository.findTodayCreatedCsvs();

		TodayCreatedCsvListResponse response = new TodayCreatedCsvListResponse();
		List<TodayCreatedCsvItemResource> items = new ArrayList<>();

		for (TodayCreatedCsvItemResource row : rows) {
			TodayCreatedCsvItemResource item = new TodayCreatedCsvItemResource();
			item.setCsvId(row.getCsvId());
			item.setDataCategory(row.getDataCategory());
			item.setSeason(row.getSeason());
			item.setHomeTeamName(row.getHomeTeamName());
			item.setAwayTeamName(row.getAwayTeamName());
			item.setCheckFinFlg(row.getCheckFinFlg());
			item.setRegisterTime(row.getRegisterTime());
			items.add(item);
		}

		response.setItems(items);
		response.setCount(items.size());
		return response;
	}
}
