package dev.web.api.bm_a017;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dev.web.repository.bm.TodayCreatedCsvRepository;

@Service
public class TodayCreatedCsvService {

	private static final int DEFAULT_START_OFFSET = 0;
    private static final int DEFAULT_END_OFFSET = 100;

	@Autowired
	private TodayCreatedCsvRepository todayCreatedCsvRepository;

	public TodayCreatedCsvListResponse getCreatedCsvs(String targetDate, Integer startOffset, Integer endOffset) {
        String normalizedDate = normalizeTargetDate(targetDate);

        int normalizedStartOffset = normalizeStartOffset(startOffset);
        int normalizedEndOffset = normalizeEndOffset(endOffset, normalizedStartOffset);

        List<TodayCreatedCsvItemResource> items =
                todayCreatedCsvRepository.findCreatedCsvsByDate(
                        normalizedDate,
                        normalizedStartOffset,
                        normalizedEndOffset
                );

        int totalCount = todayCreatedCsvRepository.countCreatedCsvsByDate(normalizedDate);

        TodayCreatedCsvListResponse response = new TodayCreatedCsvListResponse();
        response.setTargetDate(normalizedDate);
        response.setItems(items);
        response.setTotalCount(totalCount);
        response.setCount(items.size());
        response.setStartOffset(startOffset);
        response.setEndOffset(endOffset);
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

    private int normalizeStartOffset(Integer startOffset) {
        if (startOffset == null) {
            return DEFAULT_START_OFFSET;
        }

        if (startOffset < 0) {
            throw new IllegalArgumentException("startOffset は 0 以上で指定してください。");
        }

        return startOffset;
    }

    private int normalizeEndOffset(Integer endOffset, int startOffset) {
        if (endOffset == null) {
            return DEFAULT_END_OFFSET;
        }

        if (endOffset <= startOffset) {
            throw new IllegalArgumentException("endOffset は startOffset より大きい値を指定してください。");
        }

        return endOffset;
    }
}
