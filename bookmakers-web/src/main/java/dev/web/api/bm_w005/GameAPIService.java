package dev.web.api.bm_w005;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import dev.web.repository.bm.GamesRepository;
import dev.web.repository.bm.LeaguesRepository;
import dev.web.repository.bm.LeaguesRepository.TeamRow;
import lombok.RequiredArgsConstructor;

/**
 * GameAPI用サービス
 * @author shiraishitoshio
 *
 */
@Service
@RequiredArgsConstructor
public class GameAPIService {

	private final LeaguesRepository leagueRepo;

    private final GamesRepository gamesRepository;

    /**
     * チームの試合一覧を取得し、LIVE / FINISHED に振り分けて返す
     *
     * @param teamEnglish チーム名英語
     * @param teamHash  チーム名ハッシュ
     * @param teamSlug チームスラッグ
     */
    public GameMatchesResponse getTeamGames(String teamEnglish, String teamHash) {

        // Controller でもチェックする前提でも、最低限のガード
        if (!StringUtils.hasText(teamEnglish) || !StringUtils.hasText(teamHash)) {
            return new GameMatchesResponse(List.of(), List.of());
        }

        TeamRow teamInfo = leagueRepo.findTeamDetailByTeamAndHash(teamEnglish, teamHash);
        if (teamInfo == null) return null;

        // 試合一覧（LIVE/FINISHED 混在）
        List<GameMatchDTO> all = gamesRepository.findGamesForTeam(
        		teamInfo.getCountry(), teamInfo.getLeague(),
        		teamInfo.getTeam());

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

    /**
     * フロント側の { message: string } と互換の簡易 DTO
     */
    public static class SimpleMessage {
        private final String message;

        public SimpleMessage(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }
}
