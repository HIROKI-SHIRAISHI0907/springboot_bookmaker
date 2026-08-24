package dev.web.api.bm_a018;

import lombok.Data;

@Data
public class MatchDataByDateItemResource {

	/** マッチキー */
    private String matchKey;

    /** 試合ID */
    private String matchId;

    /** ゲームID */
    private String gameId;

    /** データカテゴリ */
    private String dataCategory;

    /** ホーム */
    private String homeTeamName;

    /** アウェー */
    private String awayTeamName;

    /** 手動フラグ */
    private String addManualFlg;

    /** 記録時間 */
    private String recordTime;

    /** CSV作成ステータス: CREATED(作成済) / TARGET(作成対象) / NOT_TARGET(作成非対象) */
    private String csvStatus;

}
