package dev.web.api.bm_a011;

import lombok.Data;

@Data
public class FutureMasterIngestSummaryDTO {

    /** カテゴリ */
    private String gameTeamCategory;

    /** 試合予定時間 */
    private String futureTime;

    /** ホーム */
    private String homeTeamName;

    /** アウェー */
    private String awayTeamName;

    /** ゲームリンク */
    private String gameLink;

}
