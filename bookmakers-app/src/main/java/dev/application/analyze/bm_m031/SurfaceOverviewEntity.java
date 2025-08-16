package dev.application.analyze.bm_m031;

import java.io.Serializable;

import dev.common.entity.MetaEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * チーム単位もしくは国リーグ単位の表面データを導出するEntity
 * @author shiraishitoshio
 *
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class SurfaceOverviewEntity extends MetaEntity implements Serializable {

	/** シリアライズ化 */
	private static final long serialVersionUID = 1L;

	/** 保管フラグ */
	private boolean upd;

	/** 勝ちフラグ */
	private boolean winFlg;

	/** 負けフラグ */
	private boolean loseFlg;

	/** 得点フラグ */
	private boolean scoreFlg;

	/** ID */
	private String id;

	/** 国 */
	private String country;

	/** リーグ */
	private String league;

	/** 試合年(X年) */
	private String gameYear;

	/** 試合月(X月) */
	private String gameMonth;

	/** チーム */
	private String team;

	/** 消費試合数 */
	private String games;

	/** 順位 */
	private String rank;

	/** 勝利 */
	private String win;

	/** 敗北 */
	private String lose;

	/** 引き分け */
	private String draw;

	/** 勝ち点 */
	private String winningPoints;

	/** ホーム前半得点数 */
	private String home1stHalfScore;

	/** ホーム後半得点数 */
	private String home2ndHalfScore;

	/** ホーム得点数 */
	private String homeSumScore;

	/** ホーム前半得点割合 */
	private String home1stHalfScoreRatio;

	/** ホーム後半得点割合 */
	private String home2ndHalfScoreRatio;

	/** ホーム無失点 */
	private String homeCleanSheet;

	/** アウェー前半得点数 */
	private String away1stHalfScore;

	/** アウェー後半得点数 */
	private String away2ndHalfScore;

	/** アウェー得点数 */
	private String awaySumScore;

	/** アウェー前半得点割合 */
	private String away1stHalfScoreRatio;

	/** アウェー後半得点割合 */
	private String away2ndHalfScoreRatio;

	/** アウェー無失点 */
	private String awayCleanSheet;

	/** 無得点試合数 */
	private String failToScoreGameCount;

	/** 直近連勝表示用(3連勝以上が対象「3連勝中」) */
	private String consecutiveWinDisp;

	/** 直近連敗表示用(3連敗以上が対象「3連敗中」) */
	private String consecutiveLoseDisp;

	/** 無敗記録数 */
	private String unbeatenStreakCount;

	/** 無敗記録表示用(無敗が3回連続続くと対象「無敗継続中」) */
	private String unbeatenStreakDisp;

	/** 得点継続数 */
	private String consecutiveScoreCount;

	/** 得点継続表示用(得点した試合が3回連続続くと対象「得点試合継続中」) */
	private String consecutiveScoreCountDisp;

	/** 序盤勝利数 */
	private String firstWeekGameWinCount;

	/** 序盤敗北数 */
	private String firstWeekGameLostCount;

	/** 序盤好調表示用(勝率7割以上が対象「序盤好調」) */
	private String firstWeekGameWinDisp;

	/** 中盤勝利数 */
	private String midWeekGameWinCount;

	/** 中盤敗北数 */
	private String midWeekGameLostCount;

	/** 中盤好調表示用(勝率7割以上が対象「中盤好調」) */
	private String midWeekGameWinDisp;

	/** 終盤勝利数 */
	private String lastWeekGameWinCount;

	/** 終盤敗北数 */
	private String lastWeekGameLostCount;

	/** 終盤好調表示用(勝率7割以上が対象「終盤好調」) */
	private String lastWeekGameWinDisp;

	/** ホーム勝利数 */
	private String homeWinCount;

	/** ホーム敗北数 */
	private String homeLoseCount;

	/** ホーム先制回数 */
	private String homeFirstGoalCount;

	/** ホーム逆転勝利数 */
	private String homeWinBehindCount;

	/** ホーム逆転敗北数 */
	private String homeLoseBehindCount;

	/** ホーム逆転勝利(0-1)数 */
	private String homeWinBehind0vs1Count;

	/** ホーム逆転敗北(1-0)数 */
	private String homeLoseBehind1vs0Count;

	/** ホーム逆転勝利(0-2)数 */
	private String homeWinBehind0vs2Count;

	/** ホーム逆転敗北(2-0)数 */
	private String homeLoseBehind2vs0Count;

	/** ホーム逆転勝利(その他)数 */
	private String homeWinBehindOtherCount;

	/** ホーム逆転敗北(その他)数 */
	private String homeLoseBehindOtherCount;

	/** ホーム逆境表示用(3割以上逆転勝利が対象「ホーム逆境」) */
	private String homeAdversityDisp;

	/** アウェー勝利数 */
	private String awayWinCount;

	/** アウェー敗北数 */
	private String awayLoseCount;

	/** アウェー先制回数 */
	private String awayFirstGoalCount;

	/** アウェー逆転勝利数 */
	private String awayWinBehindCount;

	/** アウェー逆転敗北数 */
	private String awayLoseBehindCount;

	/** アウェー逆転勝利(1-0)数 */
	private String awayWinBehind1vs0Count;

	/** アウェー逆転敗北(0-1)数 */
	private String awayLoseBehind0vs1Count;

	/** アウェー逆転勝利(2-0)数 */
	private String awayWinBehind2vs0Count;

	/** アウェー逆転敗北(0-2)数 */
	private String awayLoseBehind0vs2Count;

	/** アウェー逆転勝利(その他)数 */
	private String awayWinBehindOtherCount;

	/** アウェー逆転敗北(その他)数 */
	private String awayLoseBehindOtherCount;

	/** アウェー逆境表示用(3割以上逆転勝利が対象「アウェー逆境」) */
	private String awayAdversityDisp;

	/** 昇格表示用(去年下部リーグだったチームが対象「昇格組」) */
	private String promoteDisp;

	/** 降格表示用(去年上位リーグだったチームが対象「降格組」) */
	private String descendDisp;

	/** 初勝利表示用(5試合以上未勝利のチームが対象「初勝利モチベ」) */
	private String firstWinDisp;

	/** 負けが混んだ時表示用(4連敗以上のチームが対象「負け続き」) */
	private String loseStreakDisp;

}
