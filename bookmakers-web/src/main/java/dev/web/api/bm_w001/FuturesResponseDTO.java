package dev.web.api.bm_w001;

import lombok.Data;

/**
 * FuturesAPI(/api/{国}/{リーグ}/{チーム}/future)
 * @author shiraishitoshio
 *
 */
@Data
public class FuturesResponseDTO {

	/** 通番 */
	private long seq;

	/** 国およびカテゴリ */
    private String gameTeamCategory;

    /** 試合予定時間 */
    private String futureTime;   // ISO string

    /** ホームチーム */
    private String homeTeam;

    /** アウェーチーム */
    private String awayTeam;

    /** リンク */
    private String link;

    /** ラウンドNo. */
    private Integer roundNo;

    /** 試合ステータス（予定orライブ）*/
    private String status;       // "SCHEDULED" / "LIVE"

}
