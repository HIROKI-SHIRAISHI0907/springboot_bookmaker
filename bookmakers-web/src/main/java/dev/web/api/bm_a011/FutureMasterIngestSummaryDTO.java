package dev.web.api.bm_a011;

import lombok.Data;

@Data
public class FutureMasterIngestSummaryDTO {

	/** 連番 */
    private Long seq;

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

    /** スタートフラグ */
    private String startFlg;

}
