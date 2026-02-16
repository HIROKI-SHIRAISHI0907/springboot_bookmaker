package dev.web.api.bm_w009;

import java.util.List;

import org.springframework.stereotype.Service;

import dev.web.repository.bm.LeaguesRepository;
import dev.web.repository.bm.LeaguesRepository.TeamRow;
import dev.web.repository.bm.PlayersRepository;
import lombok.RequiredArgsConstructor;

/**
 * PlayersAPI用サービス
 * @author shiraishitoshio
 *
 */
@Service
@RequiredArgsConstructor
public class PlayersAPIService {

	private final LeaguesRepository leagueRepo;

    private final PlayersRepository repo;

    public PlayersResponse getPlayers(String teamEnglish, String teamHash) {

        // スラッグ → 日本語チーム名
    	TeamRow row = leagueRepo.findTeamDetailByTeamAndHash(teamEnglish, teamHash);
    	if (row == null) return null;
        List<PlayerDTO> players = repo.findPlayers(row.getCountry(), row.getLeague(),
        		row.getTeam());

        PlayersResponse res = new PlayersResponse();
        res.setPlayers(players);
        return res;
    }

}
