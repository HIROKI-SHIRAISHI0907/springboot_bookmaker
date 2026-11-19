package dev.application.analyze.bm_b011;

// 2026-11-20 bookmakers-batch（dev.batch.bm_b011.SeqWithKey）から移動（B011 を統計用の bookmakers-app で動かすため）

import lombok.Data;

/**
 * CSVグループ対象の代表行DTO
 */
@Data
public class SeqWithKey {

    /** 代表seq */
    private String seqKey;

    /** データカテゴリ */
    private String dataCategory;

    /** ホームチーム名 */
    private String homeTeamName;

    /** アウェイチーム名 */
    private String awayTeamName;

    /** 代表matchId */
    private String matchId;

    /** times（互換用。現状未使用でも残したければ残して可） */
    private String times;
}
