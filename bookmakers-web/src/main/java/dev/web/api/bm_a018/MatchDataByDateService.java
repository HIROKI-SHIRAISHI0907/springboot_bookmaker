package dev.web.api.bm_a018;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dev.web.repository.bm.MatchDataRepository;

@Service
public class MatchDataByDateService {
    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 100;

    public static final String CSV_STATUS_CREATED = "CREATED";
    public static final String CSV_STATUS_TARGET = "TARGET";
    public static final String CSV_STATUS_NOT_TARGET = "NOT_TARGET";

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

        applyCsvStatus(items);

        MatchDataByDateListResponse response = new MatchDataByDateListResponse();
        response.setTargetDate(normalizedDate);
        response.setPage(normalizedPage);
        response.setSize(normalizedSize);
        response.setCount(totalCount);
        response.setTotalPages(totalPages);
        response.setItems(items);
        return response;
    }

    /**
     * 一覧の各行に csvStatus を付与する。
     * 判定順序:
     * 1. csv_detail_manage に一致するレコードがあれば CREATED（CSV作成済）
     * 2. 同一matchIdで static_data に「ハーフタイム」「終了済」が両方存在すれば TARGET（CSV作成対象）
     * 3. どちらでもなければ NOT_TARGET（CSV作成非対象）
     */
    private void applyCsvStatus(List<MatchDataByDateItemResource> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        List<String> matchIds = items.stream()
                .map(MatchDataByDateItemResource::getMatchId)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .collect(Collectors.toList());
        Set<String> targetMatchIds = matchDataRepository.findMatchIdsWithHalftimeAndFinished(matchIds);

        for (MatchDataByDateItemResource item : items) {
            if (isAlreadyCreated(item)) {
                item.setCsvStatus(CSV_STATUS_CREATED);
                continue;
            }
            String matchId = item.getMatchId();
            if (matchId != null && !matchId.isBlank() && targetMatchIds.contains(matchId)) {
                item.setCsvStatus(CSV_STATUS_TARGET);
            } else {
                item.setCsvStatus(CSV_STATUS_NOT_TARGET);
            }
        }
    }

    private boolean isAlreadyCreated(MatchDataByDateItemResource item) {
        String dataCategory = item.getDataCategory();
        String home = item.getHomeTeamName();
        String away = item.getAwayTeamName();
        if (isBlank(dataCategory) || isBlank(home) || isBlank(away)) {
            return false;
        }
        return matchDataRepository.existsCsvDetailManage(dataCategory.trim(), home, away);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
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