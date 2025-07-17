package dev.application.analyze.bm_m026;

import dev.application.common.mapping.StatSummary;
import lombok.Data;

/**
 * 各スコア状況における平均必要特徴量(標準偏差含む)を導出するEntity
 * @author shiraishitoshio
 *
 */
@Data
public class AverageStatisticsDetailEntity {

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

	/** チーム */
	private String team;

	/** ホーム期待値の統計情報 */
	private StatSummary homeExpStat;

	/** アウェー期待値の統計情報 */
	private StatSummary awayExpStat;

	/** ホームポゼッションの統計情報 */
	private StatSummary homeDonationStat;

	/** アウェーポゼッションの統計情報 */
	private StatSummary awayDonationStat;

	/** ホームシュート数の統計情報 */
	private StatSummary homeShootAllStat;

	/** アウェーシュート数の統計情報 */
	private StatSummary awayShootAllStat;

	/** ホーム枠内シュートの統計情報 */
	private StatSummary homeShootInStat;

	/** アウェー枠内シュートの統計情報 */
	private StatSummary awayShootInStat;

	/** ホーム枠外シュートの統計情報 */
	private StatSummary homeShootOutStat;

	/** アウェー枠外シュートの統計情報 */
	private StatSummary awayShootOutStat;

	/** ホームブロックシュートの統計情報 */
	private StatSummary homeBlockShootStat;

	/** アウェーブロックシュートの統計情報 */
	private StatSummary awayBlockShootStat;

	/** ホームビッグチャンスの統計情報 */
	private StatSummary homeBigChanceStat;

	/** アウェービッグチャンスの統計情報 */
	private StatSummary awayBigChanceStat;

	/** ホームコーナーキックの統計情報 */
	private StatSummary homeCornerStat;

	/** アウェーコーナーキックの統計情報 */
	private StatSummary awayCornerStat;

	/** ホームボックス内シュートの統計情報 */
	private StatSummary homeBoxShootInStat;

	/** アウェーボックス内シュートの統計情報 */
	private StatSummary awayBoxShootInStat;

	/** ホームボックス外シュートの統計情報 */
	private StatSummary homeBoxShootOutStat;

	/** アウェーボックス外シュートの統計情報 */
	private StatSummary awayBoxShootOutStat;

	/** ホームゴールポストの統計情報 */
	private StatSummary homeGoalPostStat;

	/** アウェーゴールポストの統計情報 */
	private StatSummary awayGoalPostStat;

	/** ホームヘディングゴールの統計情報 */
	private StatSummary homeGoalHeadStat;

	/** アウェーヘディングゴールの統計情報 */
	private StatSummary awayGoalHeadStat;

	/** ホームキーパーセーブの統計情報 */
	private StatSummary homeKeeperSaveStat;

	/** アウェーキーパーセーブの統計情報 */
	private StatSummary awayKeeperSaveStat;

	/** ホームフリーキックの統計情報 */
	private StatSummary homeFreeKickStat;

	/** アウェーフリーキックの統計情報 */
	private StatSummary awayFreeKickStat;

	/** ホームオフサイドの統計情報 */
	private StatSummary homeOffsideStat;

	/** アウェーオフサイドの統計情報 */
	private StatSummary awayOffsideStat;

	/** ホームファウルの統計情報 */
	private StatSummary homeFoulStat;

	/** アウェーファウルの統計情報 */
	private StatSummary awayFoulStat;

	/** ホームイエローカードの統計情報 */
	private StatSummary homeYellowCardStat;

	/** アウェーイエローカードの統計情報 */
	private StatSummary awayYellowCardStat;

	/** ホームレッドカードの統計情報 */
	private StatSummary homeRedCardStat;

	/** アウェーレッドカードの統計情報 */
	private StatSummary awayRedCardStat;

	/** ホームスローインの統計情報 */
	private StatSummary homeSlowInStat;

	/** アウェースローインの統計情報 */
	private StatSummary awaySlowInStat;

	/** ホームボックスタッチの統計情報 */
	private StatSummary homeBoxTouchStat;

	/** アウェーボックスタッチの統計情報 */
	private StatSummary awayBoxTouchStat;

	/** ホームパス数の統計情報 */
	private StatSummary homePassCountStat;

	/** アウェーパス数の統計情報 */
	private StatSummary awayPassCountStat;

	/** ホームファイナルサードパス数の統計情報 */
	private StatSummary homeFinalThirdPassCountStat;

	/** アウェーファイナルサードパス数の統計情報 */
	private StatSummary awayFinalThirdPassCountStat;

	/** ホームクロス数の統計情報 */
	private StatSummary homeCrossCountStat;

	/** アウェークロス数の統計情報 */
	private StatSummary awayCrossCountStat;

	/** ホームタックル数の統計情報 */
	private StatSummary homeTackleCountStat;

	/** アウェータックル数の統計情報 */
	private StatSummary awayTackleCountStat;

	/** ホームクリア数の統計情報 */
	private StatSummary homeClearCountStat;

	/** アウェークリア数の統計情報 */
	private StatSummary awayClearCountStat;

	/** ホームインターセプト数の統計情報 */
	private StatSummary homeInterceptCountStat;

	/** アウェーインターセプト数の統計情報 */
	private StatSummary awayInterceptCountStat;

}
