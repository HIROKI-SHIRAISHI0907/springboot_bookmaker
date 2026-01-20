package dev.web.api.bm_w006;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import dev.web.repository.bm.StandingsRepository;

/**
 * StandingsAPI用サービス
 * @author shiraishitoshio
 *
 */
@Service
public class StandingsAPIService {

    private final StandingsRepository standingsRepository;

    public StandingsAPIService(StandingsRepository standingsRepository) {
        this.standingsRepository = standingsRepository;
    }

    /**
     * リーグ順位表を取得してレスポンスに詰める
     *
     * @param country 国名（デコード済み推奨）
     * @param league  リーグ名（デコード済み推奨）
     * @return LeagueStandingResponse（rows入り。season/updatedAtは現状null）
     */
    public LeagueStandingResponse getStandings(String country, String league) {

        // Controller でチェックしていても、サービス側でも最低限ガード
        if (!StringUtils.hasText(country) || !StringUtils.hasText(league)) {
            LeagueStandingResponse empty = new LeagueStandingResponse(List.of());
            empty.setSeason(null);
            empty.setUpdatedAt(null);
            return empty;
        }

        List<StandingRowDTO> rows = standingsRepository.findStandings(country, league);

        LeagueStandingResponse body = new LeagueStandingResponse(rows);
        // season / updatedAt は現状設定なし（null）
        body.setSeason(null);
        body.setUpdatedAt(null);

        return body;
    }
}
