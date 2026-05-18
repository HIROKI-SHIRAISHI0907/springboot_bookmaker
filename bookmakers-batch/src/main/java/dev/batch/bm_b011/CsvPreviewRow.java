package dev.batch.bm_b011;

import lombok.Data;

@Data
public class CsvPreviewRow {

	/** 通番 */
    private Integer seq;

    /** データカテゴリ */
    private String dataCategory;

    /** ホーム */
    private String homeTeamName;

    /** アウェー */
    private String awayTeamName;

    /** 記録時間 */
    private String recordTime;

    /** ホームスコア */
    private String homeScore;

    /** アウェースコア */
    private String awayScore;

    /** マッチID */
    private String matchId;

}
