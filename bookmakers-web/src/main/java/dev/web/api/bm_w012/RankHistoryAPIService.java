package dev.web.api.bm_w012;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import dev.web.repository.bm.LeaguesRepository;
import dev.web.repository.bm.LeaguesRepository.TeamRow;
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

	private final LeaguesRepository leagueRepo;

    private final RankHistoryRepository repo;

    public RankHistoryResponse getRankHistory(String teamEnglish, String teamHash) {
    	TeamRow teamInfo = leagueRepo.findTeamDetailByTeamAndHash(teamEnglish, teamHash);
    	if (teamInfo == null) return null;
        List<RankHistoryRow> rows = repo.findRankHistory(teamInfo.getCountry(), teamInfo.getLeague());

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
