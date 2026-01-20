package dev.web.api.bm_w005;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import dev.web.repository.bm.GameDetailsRepository;

/**
 * GameDetailAPI用サービス
 * @author shiraishitoshio
 *
 */
@Service
public class GameDetailAPIService {

    private final GameDetailsRepository gameDetailsRepository;

    public GameDetailAPIService(GameDetailsRepository gameDetailsRepository) {
        this.gameDetailsRepository = gameDetailsRepository;
    }

    /**
     * 試合詳細取得
     *
     * @param country 国名（デコード済み推奨）
     * @param league  リーグ名（デコード済み推奨）
     * @param seq     public.data.seq
     */
    public Optional<GameDetailDTO> getGameDetail(String country, String league, long seq) {
        // Controller でチェックしていても、サービス側でも最低限ガードしておくと安全
        if (!StringUtils.hasText(country) || !StringUtils.hasText(league) || seq <= 0) {
            return Optional.empty();
        }
        return gameDetailsRepository.findGameDetail(country, league, seq);
    }
}
