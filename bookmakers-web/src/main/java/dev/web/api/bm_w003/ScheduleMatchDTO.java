package dev.web.api.bm_w003;

import lombok.Data;

/**
 * HistoriesAPI(/api/{国}/{リーグ}/{チーム}/history)
 * @author shiraishitoshio
 *
 */
@Data
public class ScheduleMatchDTO {

	/** 通番 */
    private long seq;

    /** ラウンドNo. */
    private Integer roundNo;

    /** 未来時間 */
    private String futureTime;  // ISO

    /** 年度 */
    private int gameYear;

    /** 月 */
    private int gameMonth;

    /** ホームチーム */
    private String homeTeam;

    /** アウェーチーム */
    private String awayTeam;

    /** リンク */
    private String link;

}
