package dev.web.api.bm_a018;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dev.web.repository.bm.MatchDataRepository;

@Service
public class MatchDataByDateService {

    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 100;

    @Autowired
    private MatchDataRepository matchDataRepository;

    public MatchDataByDateListResponse getMatchDataByDate(String targetDate, Integer page, Integer size) {
        String normalizedDate = normalizeTargetDate(targetDate);
        int normalizedPage = normalizePage(page);
        int normalizedSize = normalizeSize(size);
        int offset = (normalizedPage - 1) * normalizedSize;

        int totalCount = matchDataRepository.countMatchDataByDate(normalizedDate);
        int totalPages = (totalCount == 0) ? 0 : (int) Math.ceil((double) totalCount / normalizedSize);

        if (totalPages > 0 && normalizedPage > totalPages) {
            normalizedPage = totalPages;
            offset = (normalizedPage - 1) * normalizedSize;
        }

        List<MatchDataByDateItemResource> items =
                matchDataRepository.findMatchDataByDate(normalizedDate, normalizedSize, offset);

        MatchDataByDateListResponse response = new MatchDataByDateListResponse();
        response.setTargetDate(normalizedDate);
        response.setPage(normalizedPage);
        response.setSize(normalizedSize);
        response.setCount(totalCount);
        response.setTotalPages(totalPages);
        response.setItems(items);
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

    private int normalizePage(Integer page) {
        if (page == null || page < 1) {
            return 1;
        }
        return page;
    }

    private int normalizeSize(Integer size) {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }
}
