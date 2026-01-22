package dev.web.api.bm_w012;

import lombok.Data;

/**
 * RankHistoryPointResponse
 * @author shiraishitoshio
 *
 */
@Data
public class RankHistoryPointResponse {

    /** ラウンド番号（節） */
    private int match;

    /** チームラベル */
    private String team;

    /** このラウンド終了時点の順位（1位, 2位, ...） */
    private int rank;
}
