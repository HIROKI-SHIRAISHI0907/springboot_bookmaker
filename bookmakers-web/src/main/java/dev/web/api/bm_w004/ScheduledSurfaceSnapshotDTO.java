package dev.web.api.bm_w004;

import lombok.Data;

/**
 * ScheduledOverviewsAPI（開催予定スナップショット）
 * /api/scheduled-overviews/{国}/{リーグ}/{seq}
 * の surfaces 要素1件分
 *
 * @author shiraishitoshio
 *
 */
@Data
public class ScheduledSurfaceSnapshotDTO {

	/** チーム名 */
	private String team;

	/** 試合年度（集計対象の最新年度） */
	private Integer gameYear;

	/** 試合月（集計対象の最新月） */
	private Integer gameMonth;

	/** 総試合数 */
	private Integer games;

	/** 勝ち数（総計） */
	private Integer win;

	/** 引き分け数（総計） */
	private Integer draw;

	/** 負け数（総計） */
	private Integer lose;

	/** 勝ち点（総計） */
	private Integer winningPoints;

	/** 合計得点（役割に応じて home/away を切り替えて集計） */
	private Integer goalsFor;

	/** クリーンシート数（役割に応じて home/away を切り替えて集計） */
	private Integer cleanSheets;

	/** 前半得点（役割に応じて home/away を切り替えて集計） */
	private Integer firstHalfScore;

	/** 後半得点（役割に応じて home/away を切り替えて集計） */
	private Integer secondHalfScore;

	/** 先制回数（役割に応じて home/away を切り替えて集計） */
	private Integer firstGoalCount;

	/** 逆転勝利数（役割に応じて home/away を切り替えて集計） */
	private Integer winBehindCount;

	/** 逆転敗北数（役割に応じて home/away を切り替えて集計） */
	private Integer loseBehindCount;

	/** 役割側（HOME or AWAY）での勝利数 */
	private Integer winCountRole;

	/** 役割側（HOME or AWAY）での敗北数 */
	private Integer loseCountRole;

	/** 無得点試合数（役割に依存しない集計） */
	private Integer failToScoreGameCount;

	// ▼ 追加: メインデータ（バックエンドが camelCase で返す想定）
	//   現状は null のままで、フロント側では win_count_role / lose_count_role をフォールバック利用
	/** HOME成績としての勝利数（camelCase 用） */
	private Integer homeWinCount;

	/** HOME成績としての敗北数（camelCase 用） */
	private Integer homeLoseCount;

	/** AWAY成績としての勝利数（camelCase 用） */
	private Integer awayWinCount;

	/** AWAY成績としての敗北数（camelCase 用） */
	private Integer awayLoseCount;

	// ------- バッジ表示用（既存と同様に文字列で返却） -------

	/** 連勝表示（例: 3連勝以上） */
	private String consecutiveWinDisp;

	/** 連敗表示（例: 3連敗以上） */
	private String consecutiveLoseDisp;

	/** 無敗記録表示（無敗が一定試合数以上続いた場合） */
	private String unbeatenStreakDisp;

	/** 得点継続表示（一定試合連続で得点している場合） */
	private String consecutiveScoreCountDisp;

	/** 序盤好調（シーズン序盤の高勝率） */
	private String firstWeekGameWinDisp;

	/** 中盤好調（シーズン中盤の高勝率） */
	private String midWeekGameWinDisp;

	/** 終盤好調（シーズン終盤の高勝率） */
	private String lastWeekGameWinDisp;

	/** 初勝利表示（長期未勝利後の初勝利など） */
	private String firstWinDisp;

	/** 連敗警告表示 */
	private String loseStreakDisp;

	/** 昇格（昇格組などの表示） */
	private String promoteDisp;

	/** 降格（降格組などの表示） */
	private String descendDisp;

	/** ホームでの逆境に打ち勝つ傾向表示 */
	private String homeAdversityDisp;

	/** アウェーでの逆境に打ち勝つ傾向表示 */
	private String awayAdversityDisp;

}
