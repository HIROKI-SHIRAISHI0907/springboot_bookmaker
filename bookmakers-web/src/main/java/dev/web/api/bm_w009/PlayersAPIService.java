package dev.web.api.bm_w009;

import java.util.List;

import org.springframework.stereotype.Service;

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

    private final PlayersRepository repo;

    public PlayersResponse getPlayers(String country, String league, String teamSlug) {

        // スラッグ → 日本語チーム名
        String teamJa = repo.findTeamName(country, league, teamSlug);

        List<PlayerDTO> players = repo.findPlayers(country, league, teamJa);

        PlayersResponse res = new PlayersResponse();
        res.setPlayers(players);
        return res;
    }
}
