package dev.web.api.bm_w005;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import dev.web.repository.bm.GamesRepository;

/**
 * GameAPI用サービス
 * @author shiraishitoshio
 *
 */
@Service
public class GameAPIService {

    private final GamesRepository gamesRepository;

    public GameAPIService(GamesRepository gamesRepository) {
        this.gamesRepository = gamesRepository;
    }

    /**
     * チームの試合一覧を取得し、LIVE / FINISHED に振り分けて返す
     *
     * @param country 国名（デコード済み推奨）
     * @param league  リーグ名（デコード済み推奨）
     * @param teamSlug チームスラッグ
     */
    public GameMatchesResponse getTeamGames(String country, String league, String teamSlug) {

        // Controller でもチェックする前提でも、最低限のガード
        if (!StringUtils.hasText(country) || !StringUtils.hasText(league) || !StringUtils.hasText(teamSlug)) {
            return new GameMatchesResponse(List.of(), List.of());
        }

        // スラッグ -> 日本語名
        String teamJa = gamesRepository.findTeamJa(country, league, teamSlug);

        // 試合一覧（LIVE/FINISHED 混在）
        List<GameMatchDTO> all = gamesRepository.findGamesForTeam(country, league, teamJa);

        List<GameMatchDTO> live = new ArrayList<>();
        List<GameMatchDTO> finished = new ArrayList<>();

        for (GameMatchDTO dto : all) {
            if ("FINISHED".equalsIgnoreCase(dto.getStatus())) {
                finished.add(dto);
            } else {
                live.add(dto);
            }
        }

        return new GameMatchesResponse(live, finished);
    }
}
