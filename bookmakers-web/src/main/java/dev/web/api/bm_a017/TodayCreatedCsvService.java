package dev.web.api.bm_a017;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dev.web.repository.bm.TodayCreatedCsvRepository;

@Service
public class TodayCreatedCsvService {

	@Autowired
	private TodayCreatedCsvRepository todayCreatedCsvRepository;

	public TodayCreatedCsvListResponse getCreatedCsvs(String targetDate) {
        String normalizedDate = normalizeTargetDate(targetDate);

        List<TodayCreatedCsvItemResource> items =
                todayCreatedCsvRepository.findCreatedCsvsByDate(normalizedDate);

        TodayCreatedCsvListResponse response = new TodayCreatedCsvListResponse();
        response.setTargetDate(normalizedDate);
        response.setItems(items);
        response.setCount(items.size());
        return response;
    }

    private String normalizeTargetDate(String targetDate) {
        if (targetDate == null || targetDate.isBlank()) {
            return LocalDate.now().toString();
        }

        try {
            return LocalDate.parse(targetDate.trim()).toString();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("targetDate は yyyy-MM-dd 形式で指定してください。");
        }
    }
}
