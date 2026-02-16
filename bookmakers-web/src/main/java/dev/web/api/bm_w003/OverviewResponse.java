package dev.web.api.bm_w003;

import lombok.Data;

/**
 * OverviewAPI（月次サマリ）(/api/{国}/{リーグ}/{チーム}/overview)
 * @author shiraishitoshio
 *
 */
@Data
public class OverviewResponse {

	/** 年月 */
	private String ym;

	/** ラベル */
	private String label;

	/** 年 */
	private int year;

	/** 月 */
	private int month;

	/** 勝ち点 */
	private int winningPoints;

	/** クリーンシート */
	private int cleanSheets;

	/** 得点数 */
	private int goalsFor;

	/** 失点数 */
	private int goalsAgainst;

	/** 試合数 */
	private int games;

	/** 勝ち */
	private Integer win;

	/** 引き分け */
	private Integer draw;

	/** 負け */
	private Integer lose;
}
