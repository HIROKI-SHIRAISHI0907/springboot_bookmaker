package dev.application.analyze.bm_m024;

import java.io.Serializable;

import dev.common.entity.MetaEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 相関係数保存特徴量Mapping
 * @author shiraishitoshio
 *
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class CalcCorrelationEntity extends MetaEntity implements Serializable {

	/** シリアライズ化 */
	private static final long serialVersionUID = 1L;

	/** ID */
	private String id;

	/** ファイル */
	private String file;

	/** 国 */
	private String country;

	/** リーグ */
	private String league;

	/** ホーム */
	private String home;

	/** アウェー */
	private String away;

	/** スコア */
	private String score;

	/** 導出内容 */
	private String chkBody;

	/** ホーム期待値のプレースタイル相関係数情報 */
	private String homeExpInfo;

	/** アウェー期待値のプレースタイル相関係数情報 */
	private String awayExpInfo;

	/** ホームポゼッションのプレースタイル相関係数情報 */
	private String homeDonationInfo;

	/** アウェーポゼッションのプレースタイル相関係数情報 */
	private String awayDonationInfo;

	/** ホームシュート数のプレースタイル相関係数情報 */
	private String homeShootAllInfo;

	/** アウェーシュート数のプレースタイル相関係数情報 */
	private String awayShootAllInfo;

	/** ホーム枠内シュートのプレースタイル相関係数情報 */
	private String homeShootInInfo;

	/** アウェー枠内シュートのプレースタイル相関係数情報 */
	private String awayShootInInfo;

	/** ホーム枠外シュートのプレースタイル相関係数情報 */
	private String homeShootOutInfo;

	/** アウェー枠外シュートのプレースタイル相関係数情報 */
	private String awayShootOutInfo;

	/** ホームブロックシュートのプレースタイル相関係数情報 */
	private String homeBlockShootInfo;

	/** アウェーブロックシュートのプレースタイル相関係数情報 */
	private String awayBlockShootInfo;

	/** ホームビッグチャンスのプレースタイル相関係数情報 */
	private String homeBigChanceInfo;

	/** アウェービッグチャンスのプレースタイル相関係数情報 */
	private String awayBigChanceInfo;

	/** ホームコーナーキックのプレースタイル相関係数情報 */
	private String homeCornerInfo;

	/** アウェーコーナーキックのプレースタイル相関係数情報 */
	private String awayCornerInfo;

	/** ホームボックス内シュートのプレースタイル相関係数情報 */
	private String homeBoxShootInInfo;

	/** アウェーボックス内シュートのプレースタイル相関係数情報 */
	private String awayBoxShootInInfo;

	/** ホームボックス外シュートのプレースタイル相関係数情報 */
	private String homeBoxShootOutInfo;

	/** アウェーボックス外シュートのプレースタイル相関係数情報 */
	private String awayBoxShootOutInfo;

	/** ホームゴールポストのプレースタイル相関係数情報 */
	private String homeGoalPostInfo;

	/** アウェーゴールポストのプレースタイル相関係数情報 */
	private String awayGoalPostInfo;

	/** ホームヘディングゴールのプレースタイル相関係数情報 */
	private String homeGoalHeadInfo;

	/** アウェーヘディングゴールのプレースタイル相関係数情報 */
	private String awayGoalHeadInfo;

	/** ホームキーパーセーブのプレースタイル相関係数情報 */
	private String homeKeeperSaveInfo;

	/** アウェーキーパーセーブのプレースタイル相関係数情報 */
	private String awayKeeperSaveInfo;

	/** ホームフリーキックのプレースタイル相関係数情報 */
	private String homeFreeKickInfo;

	/** アウェーフリーキックのプレースタイル相関係数情報 */
	private String awayFreeKickInfo;

	/** ホームオフサイドのプレースタイル相関係数情報 */
	private String homeOffsideInfo;

	/** アウェーオフサイドのプレースタイル相関係数情報 */
	private String awayOffsideInfo;

	/** ホームファウルのプレースタイル相関係数情報 */
	private String homeFoulInfo;

	/** アウェーファウルのプレースタイル相関係数情報 */
	private String awayFoulInfo;

	/** ホームイエローカードのプレースタイル相関係数情報 */
	private String homeYellowCardInfo;

	/** アウェーイエローカードのプレースタイル相関係数情報 */
	private String awayYellowCardInfo;

	/** ホームレッドカードのプレースタイル相関係数情報 */
	private String homeRedCardInfo;

	/** アウェーレッドカードのプレースタイル相関係数情報 */
	private String awayRedCardInfo;

	/** ホームスローインのプレースタイル相関係数情報 */
	private String homeSlowInInfo;

	/** アウェースローインのプレースタイル相関係数情報 */
	private String awaySlowInInfo;

	/** ホームボックスタッチのプレースタイル相関係数情報 */
	private String homeBoxTouchInfo;

	/** アウェーボックスタッチのプレースタイル相関係数情報 */
	private String awayBoxTouchInfo;

	/** ホームパス数のプレースタイル成功率相関係数情報 */
	private String homePassCountInfoOnSuccessRatio;

	/** ホームパス数のプレースタイル成功数相関係数情報 */
	private String homePassCountInfoOnSuccessCount;

	/** ホームパス数のプレースタイル試行数相関係数情報 */
	private String homePassCountInfoOnTryCount;

	/** アウェーパス数のプレースタイル成功率相関係数情報 */
	private String awayPassCountInfoOnSuccessRatio;

	/** アウェーパス数のプレースタイル成功数相関係数情報 */
	private String awayPassCountInfoOnSuccessCount;

	/** アウェーパス数のプレースタイル試行数相関係数情報 */
	private String awayPassCountInfoOnTryCount;

	/** ホームロングパス数のプレースタイル成功率相関係数情報 */
	private String homeLongPassCountInfoOnSuccessRatio;

	/** ホームロングパス数のプレースタイル成功数相関係数情報 */
	private String homeLongPassCountInfoOnSuccessCount;

	/** ホームロングパス数のプレースタイル試行数相関係数情報 */
	private String homeLongPassCountInfoOnTryCount;

	/** アウェーロングパス数のプレースタイル成功率相関係数情報 */
	private String awayLongPassCountInfoOnSuccessRatio;

	/** アウェーロングパス数のプレースタイル成功数相関係数情報 */
	private String awayLongPassCountInfoOnSuccessCount;

	/** アウェーロングパス数のプレースタイル試行数相関係数情報 */
	private String awayLongPassCountInfoOnTryCount;

	/** ホームファイナルサードパス数のプレースタイル成功率相関係数情報 */
	private String homeFinalThirdPassCountInfoOnSuccessRatio;

	/** ホームファイナルサードパス数のプレースタイル成功数相関係数情報 */
	private String homeFinalThirdPassCountInfoOnSuccessCount;

	/** ホームファイナルサードパス数のプレースタイル試行数相関係数情報 */
	private String homeFinalThirdPassCountInfoOnTryCount;

	/** アウェーファイナルサードパス数のプレースタイル成功率相関係数情報 */
	private String awayFinalThirdPassCountInfoOnSuccessRatio;

	/** アウェーファイナルサードパス数のプレースタイル成功数相関係数情報 */
	private String awayFinalThirdPassCountInfoOnSuccessCount;

	/** アウェーファイナルサードパス数のプレースタイル試行数相関係数情報 */
	private String awayFinalThirdPassCountInfoOnTryCount;

	/** ホームクロス数のプレースタイル成功率相関係数情報 */
	private String homeCrossCountInfoOnSuccessRatio;

	/** ホームクロス数のプレースタイル成功数相関係数情報 */
	private String homeCrossCountInfoOnSuccessCount;

	/** ホームクロス数のプレースタイル試行数相関係数情報 */
	private String homeCrossCountInfoOnTryCount;

	/** アウェークロス数のプレースタイル成功率相関係数情報 */
	private String awayCrossCountInfoOnSuccessRatio;

	/** アウェークロス数のプレースタイル成功数相関係数情報 */
	private String awayCrossCountInfoOnSuccessCount;

	/** アウェークロス数のプレースタイル試行数相関係数情報 */
	private String awayCrossCountInfoOnTryCount;

	/** ホームタックル数のプレースタイル成功率相関係数情報 */
	private String homeTackleCountInfoOnSuccessRatio;

	/** ホームタックル数のプレースタイル成功数相関係数情報 */
	private String homeTackleCountInfoOnSuccessCount;

	/** ホームタックル数のプレースタイル試行数相関係数情報 */
	private String homeTackleCountInfoOnTryCount;

	/** アウェータックル数のプレースタイル成功率相関係数情報 */
	private String awayTackleCountInfoOnSuccessRatio;

	/** アウェータックル数のプレースタイル成功数相関係数情報 */
	private String awayTackleCountInfoOnSuccessCount;

	/** アウェータックル数のプレースタイル試行数相関係数情報 */
	private String awayTackleCountInfoOnTryCount;

	/** ホームクリア数のプレースタイル相関係数情報 */
	private String homeClearCountInfo;

	/** アウェークリア数のプレースタイル相関係数情報 */
	private String awayClearCountInfo;

	/** ホームデュエル数のプレースタイル相関係数情報 */
	private String homeDuelCountInfo;

	/** アウェーデュエル数のプレースタイル相関係数情報 */
	private String awayDuelCountInfo;

	/** ホームインターセプト数のプレースタイル相関係数情報 */
	private String homeInterceptCountInfo;

	/** アウェーインターセプト数のプレースタイル相関係数情報 */
	private String awayInterceptCountInfo;

}
