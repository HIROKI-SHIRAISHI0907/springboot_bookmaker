package dev.web.api.bm_w002;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.web.repository.bm.HistoriesRepository;

/**
 * HistoriesAPI用サービス
 * @author shiraishitoshio
 *
 */
@Service
public class HistoriesAPIService {

    private final HistoriesRepository historiesRepository;

    public HistoriesAPIService(HistoriesRepository historiesRepository) {
        this.historiesRepository = historiesRepository;
    }

    /**
     * 過去試合一覧（teamSlug→teamJa解決込み）
     */
    @Transactional(readOnly = true)
    public List<HistoryResponseDTO> listHistory(String country, String league, String teamSlug) {
        String teamJa = historiesRepository.findTeamJa(country, league, teamSlug);
        return historiesRepository.findPastMatches(country, league, teamJa);
    }

    /**
     * 試合詳細（seqで取得）
     */
    @Transactional(readOnly = true)
    public Optional<HistoryDetailResponseDTO> getHistoryDetail(String country, String league, long seq) {
        return historiesRepository.findHistoryDetail(country, league, seq);
    }
}
