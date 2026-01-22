package dev.web.api.bm_w012;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import dev.web.repository.bm.RankHistoryRepository;
import dev.web.repository.bm.RankHistoryRepository.RankHistoryRow;
import lombok.RequiredArgsConstructor;

/**
 * RankHistoryAPI用サービス
 * @author shiraishitoshio
 *
 */
@Service
@RequiredArgsConstructor
public class RankHistoryAPIService {

    private final RankHistoryRepository repo;

    public RankHistoryResponse getRankHistory(String country, String league) {
        List<RankHistoryRow> rows = repo.findRankHistory(country, league);

        List<RankHistoryPointResponse> items = new ArrayList<>();
        for (RankHistoryRow r : rows) {
            RankHistoryPointResponse p = new RankHistoryPointResponse();
            p.setMatch(r.match != null ? r.match.intValue() : 0);
            p.setTeam(r.team);
            p.setRank(r.rank != null ? r.rank.intValue() : 0);
            items.add(p);
        }

        RankHistoryResponse res = new RankHistoryResponse();
        res.setItems(items);
        return res;
    }
}
