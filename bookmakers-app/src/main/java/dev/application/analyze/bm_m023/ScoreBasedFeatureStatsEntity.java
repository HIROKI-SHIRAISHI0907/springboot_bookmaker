package dev.application.analyze.bm_m023;

import java.io.Serializable;

import lombok.Data;

/**
 * 各スコア状況における平均必要特徴量(標準偏差含む)を導出するEntity
 * @author shiraishitoshio
 *
 */
@Data
public class ScoreBasedFeatureStatsEntity implements Serializable {

	/** シリアライズ化 */
	private static final long serialVersionUID = 1L;

	/** 保管フラグ */
	private boolean upd;

	/** ID */
	private String id;

	/** データ導出状況(得点あり,なし,全体) */
	private String situation;

	/** スコア(X-Xの方式) */
	private String score;

	/** 国 */
	private String country;

	/** リーグ */
	private String league;

	/** ホーム期待値の統計情報 */
	private String homeExpStat;

	/** アウェー期待値の統計情報 */
	private String awayExpStat;

	/** ホームポゼッションの統計情報 */
	private String homeDonationStat;

	/** アウェーポゼッションの統計情報 */
	private String awayDonationStat;

	/** ホームシュート数の統計情報 */
	private String homeShootAllStat;

	/** アウェーシュート数の統計情報 */
	private String awayShootAllStat;

	/** ホーム枠内シュートの統計情報 */
	private String homeShootInStat;

	/** アウェー枠内シュートの統計情報 */
	private String awayShootInStat;

	/** ホーム枠外シュートの統計情報 */
	private String homeShootOutStat;

	/** アウェー枠外シュートの統計情報 */
	private String awayShootOutStat;

	/** ホームブロックシュートの統計情報 */
	private String homeBlockShootStat;

	/** アウェーブロックシュートの統計情報 */
	private String awayBlockShootStat;

	/** ホームビッグチャンスの統計情報 */
	private String homeBigChanceStat;

	/** アウェービッグチャンスの統計情報 */
	private String awayBigChanceStat;

	/** ホームコーナーキックの統計情報 */
	private String homeCornerStat;

	/** アウェーコーナーキックの統計情報 */
	private String awayCornerStat;

	/** ホームボックス内シュートの統計情報 */
	private String homeBoxShootInStat;

	/** アウェーボックス内シュートの統計情報 */
	private String awayBoxShootInStat;

	/** ホームボックス外シュートの統計情報 */
	private String homeBoxShootOutStat;

	/** アウェーボックス外シュートの統計情報 */
	private String awayBoxShootOutStat;

	/** ホームゴールポストの統計情報 */
	private String homeGoalPostStat;

	/** アウェーゴールポストの統計情報 */
	private String awayGoalPostStat;

	/** ホームヘディングゴールの統計情報 */
	private String homeGoalHeadStat;

	/** アウェーヘディングゴールの統計情報 */
	private String awayGoalHeadStat;

	/** ホームキーパーセーブの統計情報 */
	private String homeKeeperSaveStat;

	/** アウェーキーパーセーブの統計情報 */
	private String awayKeeperSaveStat;

	/** ホームフリーキックの統計情報 */
	private String homeFreeKickStat;

	/** アウェーフリーキックの統計情報 */
	private String awayFreeKickStat;

	/** ホームオフサイドの統計情報 */
	private String homeOffsideStat;

	/** アウェーオフサイドの統計情報 */
	private String awayOffsideStat;

	/** ホームファウルの統計情報 */
	private String homeFoulStat;

	/** アウェーファウルの統計情報 */
	private String awayFoulStat;

	/** ホームイエローカードの統計情報 */
	private String homeYellowCardStat;

	/** アウェーイエローカードの統計情報 */
	private String awayYellowCardStat;

	/** ホームレッドカードの統計情報 */
	private String homeRedCardStat;

	/** アウェーレッドカードの統計情報 */
	private String awayRedCardStat;

	/** ホームスローインの統計情報 */
	private String homeSlowInStat;

	/** アウェースローインの統計情報 */
	private String awaySlowInStat;

	/** ホームボックスタッチの統計情報 */
	private String homeBoxTouchStat;

	/** アウェーボックスタッチの統計情報 */
	private String awayBoxTouchStat;

	/** ホームパス数の統計情報 */
	private String homePassCountStat;

	/** アウェーパス数の統計情報 */
	private String awayPassCountStat;

	/** ホームファイナルサードパス数の統計情報 */
	private String homeFinalThirdPassCountStat;

	/** アウェーファイナルサードパス数の統計情報 */
	private String awayFinalThirdPassCountStat;

	/** ホームクロス数の統計情報 */
	private String homeCrossCountStat;

	/** アウェークロス数の統計情報 */
	private String awayCrossCountStat;

	/** ホームタックル数の統計情報 */
	private String homeTackleCountStat;

	/** アウェータックル数の統計情報 */
	private String awayTackleCountStat;

	/** ホームクリア数の統計情報 */
	private String homeClearCountStat;

	/** アウェークリア数の統計情報 */
	private String awayClearCountStat;

	/** ホームインターセプト数の統計情報 */
	private String homeInterceptCountStat;

	/** アウェーインターセプト数の統計情報 */
	private String awayInterceptCountStat;

	/** 登録ID */
	private String registerId;

	/** 登録時間 */
	private String registerTime;

	/** 更新ID */
	private String updateId;

	/** 更新時間 */
	private String updateTime;

}
