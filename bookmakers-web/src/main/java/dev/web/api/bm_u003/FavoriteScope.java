package dev.web.api.bm_u003;

import java.util.List;
import java.util.Map;

import lombok.Data;

/**
 * お気に入りフィルタ内部モデル.
 * Repository → Service → 判定ロジック用
 */
@Data
public class FavoriteScope {

    /** お気に入り未登録なら true（全許可） */
    private boolean allowAll;

    /** 許可国（国のみ登録で、配下league/teamが無い国だけ） */
    private List<String> allowedCountries;

    /**
     * 許可国リーグ（league登録で、配下teamが無い country+league だけ）
     * key = country
     */
    private Map<String, List<String>> allowedLeaguesByCountry;

    /**
     * 許可国リーグチーム（team登録）
     * key = country|league
     */
    private Map<String, List<String>> allowedTeamsByCountryLeague;
}
