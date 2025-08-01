package dev.application.analyze.bm_m030;

import java.io.Serializable;

import dev.common.entity.MetaEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * データ暗号化エンティティ
 * @author shiraishitoshio
 *
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class StatEncryptionEntity extends MetaEntity implements Serializable {

	/** シリアライズ化 */
	private static final long serialVersionUID = 1L;

	/** ID */
	private String id;

	/** 国 */
	private String country;

	/** リーグ */
	private String league;

	/** ホーム */
	private String home;

	/** アウェー */
	private String away;

	/** チーム単体 */
	private String team;

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

	/** ホームパス数のプレースタイル成功数相関係数情報 */
	private String homePassCountInfoOnSuccessCount;

	/** アウェーパス数のプレースタイル成功数相関係数情報 */
	private String awayPassCountInfoOnSuccessCount;

	/** ホームファイナルサードパス数のプレースタイル成功数相関係数情報 */
	private String homeFinalThirdPassCountInfoOnSuccessCount;

	/** アウェーファイナルサードパス数のプレースタイル成功数相関係数情報 */
	private String awayFinalThirdPassCountInfoOnSuccessCount;

	/** ホームクロス数のプレースタイル成功数相関係数情報 */
	private String homeCrossCountInfoOnSuccessCount;

	/** アウェークロス数のプレースタイル成功数相関係数情報 */
	private String awayCrossCountInfoOnSuccessCount;

	/** ホームタックル数のプレースタイル成功数相関係数情報 */
	private String homeTackleCountInfoOnSuccessCount;

	/** アウェータックル数のプレースタイル成功数相関係数情報 */
	private String awayTackleCountInfoOnSuccessCount;

	/** ホームクリア数のプレースタイル相関係数情報 */
	private String homeClearCountInfo;

	/** アウェークリア数のプレースタイル相関係数情報 */
	private String awayClearCountInfo;

	/** ホームインターセプト数のプレースタイル相関係数情報 */
	private String homeInterceptCountInfo;

	/** アウェーインターセプト数のプレースタイル相関係数情報 */
	private String awayInterceptCountInfo;

}
