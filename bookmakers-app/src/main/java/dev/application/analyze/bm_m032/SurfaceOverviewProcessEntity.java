package dev.application.analyze.bm_m032;

import lombok.Data;

/**
 * SurfaceOverview の累積情報に対して、
 * 同一チームの直前ラウンド時点との差分を保持する Entity。
 *
 * <p>
 * 本 Entity は、更新前の {@code SurfaceOverviewEntity} と
 * 更新後の {@code SurfaceOverviewEntity} を比較し、
 * 今回ラウンドで増減した値のみを保持するために使用する。
 * </p>
 *
 * <p>
 * 主な用途は以下の通り。
 * </p>
 * <ul>
 *   <li>同一チームの1つ前ラウンドとの差分分析</li>
 *   <li>今回試合で増加した勝利数・敗北数・得点数などの記録</li>
 *   <li>ラウンド更新前後の {@code roundConc} 状態比較</li>
 * </ul>
 *
 * <p>
 * 例えば、更新前が {@code A=1,2,4|W=1|L=2,4}、
 * 更新後が {@code A=1,2,4,5|W=1,5|L=2,4} の場合、
 * 本 Entity には「ラウンド5反映分」の差分が格納される。
 * </p>
 *
 * @author shiraishitoshio
 */
@Data
public class SurfaceOverviewProcessEntity {

	/** 国 */
	private String country;

	/** リーグ */
	private String league;

	/** 試合年 */
	private Integer gameYear;

	/** 試合月 */
	private Integer gameMonth;

	/** チーム名 */
	private String team;

	/** 更新前のラウンド履歴文字列 */
	private String beforeRoundConc;

	/** 更新後のラウンド履歴文字列 */
	private String afterRoundConc;

	/** 直前ラウンド番号 */
	private Integer previousRoundNo;

	/** 今回反映対象のラウンド番号 */
	private Integer currentRoundNo;

	/** ラウンド差分 */
	private Integer roundGap;

	/** 消費試合数の差分 */
	private Integer gamesDiff;

	/** 勝利数の差分 */
	private Integer winDiff;

	/** 敗北数の差分 */
	private Integer loseDiff;

	/** 引き分け数の差分 */
	private Integer drawDiff;

	/** 勝ち点の差分 */
	private Integer winningPointsDiff;

	/** ホーム前半得点数の差分 */
	private Integer home1stHalfScoreDiff;

	/** ホーム後半得点数の差分 */
	private Integer home2ndHalfScoreDiff;

	/** ホーム合計得点数の差分 */
	private Integer homeSumScoreDiff;

	/** アウェー前半得点数の差分 */
	private Integer away1stHalfScoreDiff;

	/** アウェー後半得点数の差分 */
	private Integer away2ndHalfScoreDiff;

	/** アウェー合計得点数の差分 */
	private Integer awaySumScoreDiff;

	/** ホーム前半失点数の差分 */
	private Integer home1stHalfLostDiff;

	/** ホーム後半失点数の差分 */
	private Integer home2ndHalfLostDiff;

	/** ホーム合計失点数の差分 */
	private Integer homeSumLostDiff;

	/** アウェー前半失点数の差分 */
	private Integer away1stHalfLostDiff;

	/** アウェー後半失点数の差分 */
	private Integer away2ndHalfLostDiff;

	/** アウェー合計失点数の差分 */
	private Integer awaySumLostDiff;

	/** 無得点試合数の差分 */
	private Integer failToScoreGameCountDiff;

	/** 序盤勝利数の差分 */
	private Integer firstWeekGameWinCountDiff;

	/** 序盤敗北数の差分 */
	private Integer firstWeekGameLostCountDiff;

	/** 中盤勝利数の差分 */
	private Integer midWeekGameWinCountDiff;

	/** 中盤敗北数の差分 */
	private Integer midWeekGameLostCountDiff;

	/** 終盤勝利数の差分 */
	private Integer lastWeekGameWinCountDiff;

	/** 終盤敗北数の差分 */
	private Integer lastWeekGameLostCountDiff;

	/** ホーム勝利数の差分 */
	private Integer homeWinCountDiff;

	/** ホーム敗北数の差分 */
	private Integer homeLoseCountDiff;

	/** ホーム先制回数の差分 */
	private Integer homeFirstGoalCountDiff;

	/** ホーム逆転勝利数の差分 */
	private Integer homeWinBehindCountDiff;

	/** ホーム逆転敗北数の差分 */
	private Integer homeLoseBehindCountDiff;

	/** アウェー勝利数の差分 */
	private Integer awayWinCountDiff;

	/** アウェー敗北数の差分 */
	private Integer awayLoseCountDiff;

	/** アウェー先制回数の差分 */
	private Integer awayFirstGoalCountDiff;

	/** アウェー逆転勝利数の差分 */
	private Integer awayWinBehindCountDiff;

	/** アウェー逆転敗北数の差分 */
	private Integer awayLoseBehindCountDiff;
}
