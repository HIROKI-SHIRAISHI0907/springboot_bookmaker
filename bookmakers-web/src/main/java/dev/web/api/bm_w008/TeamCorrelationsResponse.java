package dev.web.api.bm_w008;

import java.util.List;

import lombok.Data;

/**
 * 相関係数APIレスポンス
 * /api/{country}/{league}/{team}/correlations
 *
 * @author shiraishitoshio
 */
@Data
public class TeamCorrelationsResponse {

	/** チーム */
    private String team;

    /** 国 */
    private String country;

    /** リーグ */
    private String league;

    // 現在のフィルタ（null 可）
    /** 相手 */
    private String opponent;

    /** 相手（複数） */
    private List<String> opponents;

    /** 相関係数データ */
    private CorrelationsBySideDTO correlations;
}
