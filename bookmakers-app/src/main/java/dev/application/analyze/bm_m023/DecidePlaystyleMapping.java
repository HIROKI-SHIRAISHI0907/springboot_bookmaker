package dev.application.analyze.bm_m023;

import lombok.Data;

/**
 * プレースタイル特徴量Mapping
 * @author shiraishitoshio
 *
 */
@Data
public class DecidePlaystyleMapping {

	/** ホーム期待値のプレースタイル閾値情報 */
	private DecidePlaystyleSummary homeExpInfo;

	/** アウェー期待値のプレースタイル閾値情報 */
	private DecidePlaystyleSummary awayExpInfo;

	/** ホームポゼッションのプレースタイル閾値情報 */
	private DecidePlaystyleSummary homeDonationInfo;

	/** アウェーポゼッションのプレースタイル閾値情報 */
	private DecidePlaystyleSummary awayDonationInfo;

	/** ホームシュート数のプレースタイル閾値情報 */
	private DecidePlaystyleSummary homeShootAllInfo;

	/** アウェーシュート数のプレースタイル閾値情報 */
	private DecidePlaystyleSummary awayShootAllInfo;

	/** ホーム枠内シュートのプレースタイル閾値情報 */
	private DecidePlaystyleSummary homeShootInInfo;

	/** アウェー枠内シュートのプレースタイル閾値情報 */
	private DecidePlaystyleSummary awayShootInInfo;

	/** ホーム枠外シュートのプレースタイル閾値情報 */
	private DecidePlaystyleSummary homeShootOutInfo;

	/** アウェー枠外シュートのプレースタイル閾値情報 */
	private DecidePlaystyleSummary awayShootOutInfo;

	/** ホームブロックシュートのプレースタイル閾値情報 */
	private DecidePlaystyleSummary homeBlockShootInfo;

	/** アウェーブロックシュートのプレースタイル閾値情報 */
	private DecidePlaystyleSummary awayBlockShootInfo;

	/** ホームビッグチャンスのプレースタイル閾値情報 */
	private DecidePlaystyleSummary homeBigChanceInfo;

	/** アウェービッグチャンスのプレースタイル閾値情報 */
	private DecidePlaystyleSummary awayBigChanceInfo;

	/** ホームコーナーキックのプレースタイル閾値情報 */
	private DecidePlaystyleSummary homeCornerInfo;

	/** アウェーコーナーキックのプレースタイル閾値情報 */
	private DecidePlaystyleSummary awayCornerInfo;

	/** ホームボックス内シュートのプレースタイル閾値情報 */
	private DecidePlaystyleSummary homeBoxShootInInfo;

	/** アウェーボックス内シュートのプレースタイル閾値情報 */
	private DecidePlaystyleSummary awayBoxShootInInfo;

	/** ホームボックス外シュートのプレースタイル閾値情報 */
	private DecidePlaystyleSummary homeBoxShootOutInfo;

	/** アウェーボックス外シュートのプレースタイル閾値情報 */
	private DecidePlaystyleSummary awayBoxShootOutInfo;

	/** ホームゴールポストのプレースタイル閾値情報 */
	private DecidePlaystyleSummary homeGoalPostInfo;

	/** アウェーゴールポストのプレースタイル閾値情報 */
	private DecidePlaystyleSummary awayGoalPostInfo;

	/** ホームヘディングゴールのプレースタイル閾値情報 */
	private DecidePlaystyleSummary homeGoalHeadInfo;

	/** アウェーヘディングゴールのプレースタイル閾値情報 */
	private DecidePlaystyleSummary awayGoalHeadInfo;

	/** ホームキーパーセーブのプレースタイル閾値情報 */
	private DecidePlaystyleSummary homeKeeperSaveInfo;

	/** アウェーキーパーセーブのプレースタイル閾値情報 */
	private DecidePlaystyleSummary awayKeeperSaveInfo;

	/** ホームフリーキックのプレースタイル閾値情報 */
	private DecidePlaystyleSummary homeFreeKickInfo;

	/** アウェーフリーキックのプレースタイル閾値情報 */
	private DecidePlaystyleSummary awayFreeKickInfo;

	/** ホームオフサイドのプレースタイル閾値情報 */
	private DecidePlaystyleSummary homeOffsideInfo;

	/** アウェーオフサイドのプレースタイル閾値情報 */
	private DecidePlaystyleSummary awayOffsideInfo;

	/** ホームファウルのプレースタイル閾値情報 */
	private DecidePlaystyleSummary homeFoulInfo;

	/** アウェーファウルのプレースタイル閾値情報 */
	private DecidePlaystyleSummary awayFoulInfo;

	/** ホームイエローカードのプレースタイル閾値情報 */
	private DecidePlaystyleSummary homeYellowCardInfo;

	/** アウェーイエローカードのプレースタイル閾値情報 */
	private DecidePlaystyleSummary awayYellowCardInfo;

	/** ホームレッドカードのプレースタイル閾値情報 */
	private DecidePlaystyleSummary homeRedCardInfo;

	/** アウェーレッドカードのプレースタイル閾値情報 */
	private DecidePlaystyleSummary awayRedCardInfo;

	/** ホームスローインのプレースタイル閾値情報 */
	private DecidePlaystyleSummary homeSlowInInfo;

	/** アウェースローインのプレースタイル閾値情報 */
	private DecidePlaystyleSummary awaySlowInInfo;

	/** ホームボックスタッチのプレースタイル閾値情報 */
	private DecidePlaystyleSummary homeBoxTouchInfo;

	/** アウェーボックスタッチのプレースタイル閾値情報 */
	private DecidePlaystyleSummary awayBoxTouchInfo;

	/** ホームパス数のプレースタイル閾値情報 */
	private DecidePlaystyleSummary homePassCountInfo;

	/** アウェーパス数のプレースタイル閾値情報 */
	private DecidePlaystyleSummary awayPassCountInfo;

	/** ホームファイナルサードパス数のプレースタイル閾値情報 */
	private DecidePlaystyleSummary homeFinalThirdPassCountInfo;

	/** アウェーファイナルサードパス数のプレースタイル閾値情報 */
	private DecidePlaystyleSummary awayFinalThirdPassCountInfo;

	/** ホームクロス数のプレースタイル閾値情報 */
	private DecidePlaystyleSummary homeCrossCountInfo;

	/** アウェークロス数のプレースタイル閾値情報 */
	private DecidePlaystyleSummary awayCrossCountInfo;

	/** ホームタックル数のプレースタイル閾値情報 */
	private DecidePlaystyleSummary homeTackleCountInfo;

	/** アウェータックル数のプレースタイル閾値情報 */
	private DecidePlaystyleSummary awayTackleCountInfo;

	/** ホームクリア数のプレースタイル閾値情報 */
	private DecidePlaystyleSummary homeClearCountInfo;

	/** アウェークリア数のプレースタイル閾値情報 */
	private DecidePlaystyleSummary awayClearCountInfo;

	/** ホームインターセプト数のプレースタイル閾値情報 */
	private DecidePlaystyleSummary homeInterceptCountInfo;

	/** アウェーインターセプト数のプレースタイル閾値情報 */
	private DecidePlaystyleSummary awayInterceptCountInfo;

}
