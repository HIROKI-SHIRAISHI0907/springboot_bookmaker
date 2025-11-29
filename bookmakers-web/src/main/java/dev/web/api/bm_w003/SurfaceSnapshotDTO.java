package dev.web.api.bm_w003;

import lombok.Data;

/**
 * OvierviewsAPI（スナップショット）(/api/{国}/{リーグ}/{チーム}/history)
 * @author shiraishitoshio
 *
 */
@Data
public class SurfaceSnapshotDTO {

	/** チーム */
	private String team;

	/** 試合 */
	private Integer games;

	/** 勝ち */
	private Integer win;

	/** 引き分け */
	private Integer draw;

	/** 負け */
	private Integer lose;

	/** 勝ち点 */
	private Integer winningPoints;

	/** その他表示用 */
	private String consecutiveWinDisp;
	private String consecutiveLoseDisp;
	private String unbeatenStreakDisp;
	private String consecutiveScoreCountDisp;
	private String firstWeekGameWinDisp;
	private String midWeekGameWinDisp;
	private String lastWeekGameWinDisp;
	private String firstWinDisp;
	private String loseStreakDisp;
	private String promoteDisp;
	private String descendDisp;
	private String homeAdversityDisp;
	private String awayAdversityDisp;
}
