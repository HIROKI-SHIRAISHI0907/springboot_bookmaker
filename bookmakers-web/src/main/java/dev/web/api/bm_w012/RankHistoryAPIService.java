package dev.web.api.bm_w012;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import dev.web.repository.bm.LeaguesRepository;
import dev.web.repository.bm.LeaguesRepository.TeamRow;
import dev.web.repository.bm.RankHistoryRepository;
import dev.web.repository.bm.RankHistoryRepository.RankHistoryRow;
import dev.web.repository.master.CountryLeagueSeasonMasterWebRepository;
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

    private final CountryLeagueSeasonMasterWebRepository countryLeagueSeasonRepository;

    public RankHistoryResponse getRankHistory(String teamEnglish, String teamHash, boolean includePast) {
    	TeamRow teamInfo = leagueRepo.findTeamDetailByTeamAndHash(teamEnglish, teamHash);
    	if (teamInfo == null) return null;

    	String seasonYear = null;
        if (!includePast) {
            seasonYear = countryLeagueSeasonRepository.findCurrentSeasonYear(teamInfo.getCountry(), teamInfo.getLeague());
            if (seasonYear == null) return null; // 今シーズンが取れないなら 404 扱いなど
        }

        List<RankHistoryRow> rows = repo.findRankHistory(
        		teamInfo.getCountry(), teamInfo.getLeague(), seasonYear);

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
