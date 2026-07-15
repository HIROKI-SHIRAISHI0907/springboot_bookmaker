package dev.web.api.bm_a011;

import lombok.Data;

@Data
public class DataIngestSummaryDTO {

    /** カテゴリ */
    private String dataCategory;

    /**
     * 同じ試合データ件数
     * （times が違っても同じ対戦データとして数える）
     */
    private Integer sameMatchDataCount;

    /** ホーム */
    private String homeTeamName;

    /** アウェー */
    private String awayTeamName;

    /** ゲームID */
    private String gameId;

    /** ゲームリンク */
    private String gameLink;

}
