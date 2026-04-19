package dev.web.api.bm_w005;

import java.util.Optional;

import org.springframework.stereotype.Service;

import dev.web.repository.bm.GameDetailsRepository;
import dev.web.repository.bm.LeaguesRepository;
import dev.web.repository.bm.LeaguesRepository.TeamRow;
import lombok.RequiredArgsConstructor;

/**
 * GameDetailAPI用サービス
 * @author shiraishitoshio
 *
 */
@Service
@RequiredArgsConstructor
public class GameDetailAPIService {

	private final LeaguesRepository leagueRepo;

    private final GameDetailsRepository gameDetailsRepository;

    /**
     * 試合詳細取得
     *
     * @param teamEnglish チーム英語（デコード済み推奨）
     * @param teamHash  チームハッシュ（デコード済み推奨）
     * @param seq     public.data.seq
     */
    public GameDetailResponse getGameDetail(String teamEnglish, String teamHash, long seq) {
    	TeamRow teamInfo = leagueRepo.findTeamDetailByTeamAndHash(teamEnglish, teamHash);
    	if (teamInfo == null) {
    		return new GameDetailResponse(new GameDetailDTO());
    	}

    	Optional<GameDetailDTO> detailOpt = gameDetailsRepository.findGameDetail(
    			teamInfo.getCountry(), teamInfo.getLeague(), seq);

    	return new GameDetailResponse(detailOpt.orElseGet(GameDetailDTO::new));
    }

    /**
     * 試合詳細取得
     *
     * @param seq     public.data.seq
     */
    public GameDetailResponse getGameDetail(long seq) {
    	Optional<GameDetailDTO> detailOpt = gameDetailsRepository.findGameDetail(seq);
    	return new GameDetailResponse(detailOpt.orElseGet(GameDetailDTO::new));
    }
}
