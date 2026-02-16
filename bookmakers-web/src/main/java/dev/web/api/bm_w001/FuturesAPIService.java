package dev.web.api.bm_w001;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.web.repository.bm.LeaguesRepository;
import dev.web.repository.bm.LeaguesRepository.TeamRow;
import dev.web.repository.master.FuturesRepository;
import lombok.AllArgsConstructor;

/**
 * FuturesAPI用サービス
 * @author shiraishitoshio
 *
 */
@Service
@AllArgsConstructor
public class FuturesAPIService {

	private final LeaguesRepository leagueRepo;

	private final FuturesRepository futuresRepository;

    /**
     * 国・リーグ・チーム(slug)から、予定試合（SCHEDULED）一覧を返す
     */
    @Transactional(readOnly = true)
    public List<FuturesResponseDTO> getFutureMatches(String teamEnglish, String teamHash) {
    	TeamRow teamInfo = leagueRepo.findTeamDetailByTeamAndHash(teamEnglish, teamHash);
        if (teamInfo == null) return null;
        return futuresRepository.findFutureMatches(teamInfo.getCountry(), teamInfo.getLeague(),
        		teamInfo.getTeam());
    }

    /**
     * 管理画面用
     * @param country
     * @param league
     * @param limit
     * @return
     */
    @Transactional(readOnly = true)
    public List<FuturesResponseDTO> getFutureMatchesForAdmin(String country, String league, int limit) {
        return futuresRepository.findFutureMatchesFromNextDay(country, league, limit);
    }

}
