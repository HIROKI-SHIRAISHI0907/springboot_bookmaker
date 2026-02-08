package dev.web.api.bm_w001;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.web.repository.master.FuturesRepository;

/**
 * FuturesAPI用サービス
 * @author shiraishitoshio
 *
 */
@Service
public class FuturesAPIService {

	private final FuturesRepository futuresRepository;

    public FuturesAPIService(FuturesRepository futuresRepository) {
        this.futuresRepository = futuresRepository;
    }

    /**
     * 国・リーグ・チーム(slug)から、予定試合（SCHEDULED）一覧を返す
     */
    @Transactional(readOnly = true)
    public List<FuturesResponseDTO> getFutureMatches(String country, String league, String teamSlug) {
        String teamJa = futuresRepository.findTeamJa(country, league, teamSlug);
        return futuresRepository.findFutureMatches(teamJa, country, league);
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
