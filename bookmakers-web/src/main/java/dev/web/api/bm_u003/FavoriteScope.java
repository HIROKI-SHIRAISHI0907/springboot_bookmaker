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

    /** 許可国 */
    private List<String> allowedCountries;

    /** 許可国リーグ */
    private Map<String, List<String>> allowedLeaguesByCountry;

    /** 許可国リーグチーム */
    private Map<String, List<String>> allowedTeamsByCountryLeague;
}
