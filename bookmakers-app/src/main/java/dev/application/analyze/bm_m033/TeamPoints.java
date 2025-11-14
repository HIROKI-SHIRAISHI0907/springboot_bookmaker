package dev.application.analyze.bm_m033;

import lombok.Data;

/**
 * チームポイント
 * @author shiraishitoshio
 *
 */
@Data
public class TeamPoints {

	/** チーム */
	private String team;

	/** 勝ち点 */
    private Integer points;

    /** 総得点*/
    private Integer gf;     // 総得点

    /** 総失点*/
    private Integer ga;     // 総失点

    /** 試合数 */
    private Integer played; // 試合数

}
