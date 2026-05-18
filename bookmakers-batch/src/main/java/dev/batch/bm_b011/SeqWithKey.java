package dev.batch.bm_b011;

import lombok.Data;

/**
 * CSVグループ対象の代表行DTO
 */
@Data
public class SeqWithKey {

    /** 代表seq */
    private Integer seq;

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
