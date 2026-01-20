package dev.web.api.bm_w002;

import lombok.Data;

/**
 * HistoriesAPI(/api/history/{国}/{リーグ}/{チーム})
 * @author shiraishitoshio
 *
 */
@Data
public class HistoryResponseDTO {

	/** 通番 */
    private long seq;

    /** 該当試合時間 */
    private String matchTime;          // ISO-like "YYYY-MM-DDTHH:MM:SS"

    /** 国およびチームカテゴリ */
    private String gameTeamCategory;

    /** ホームチーム */
    private String homeTeam;

    /** アウェーチーム */
    private String awayTeam;

    /** ホームスコア */
    private int homeScore;

    /** アウェースコア */
    private int awayScore;

    /** ラウンドNo. */
    private Integer roundNo;

    /** リンク */
    private String link;
}
