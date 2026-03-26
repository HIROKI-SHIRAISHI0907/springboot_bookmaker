package dev.web.api.bm_w015;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.web.repository.bm.EachTeamScoreRepository;
import dev.web.repository.bm.LeaguesRepository;
import dev.web.repository.bm.LeaguesRepository.TeamRow;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EachTeamScoreService {

    private final LeaguesRepository leagueRepo;
    private final EachTeamScoreRepository eachStatsRepository;

    @Transactional(readOnly = true)
    public List<EachTeamScoreResponseDTO> getEachTeamScore(String teamEnglish, String teamHash) {
        TeamRow teamInfo = leagueRepo.findTeamDetailByTeamAndHash(teamEnglish, teamHash);
        if (teamInfo == null) {
            return null;
        }

        return eachStatsRepository.findEachTeamScore(
                teamInfo.getCountry(),
                teamInfo.getLeague(),
                teamEnglish
        );
    }
}
