package dev.web.api.bm_a011;

import lombok.Data;

@Data
public class DataIngestSummaryDTO {

	/** 連番 */
    private String seq;

    /** カテゴリ */
    private String dataCategory;

    /** リアルタイム時間 */
    private String times;

    /** ホーム */
    private String homeTeamName;

    /** アウェー */
    private String awayTeamName;

    /** 記録時間 */
    private String recordTime;

    /** ゲームID */
    private String gameId;

    /** ゲームリンク */
    private String gameLink;

}
