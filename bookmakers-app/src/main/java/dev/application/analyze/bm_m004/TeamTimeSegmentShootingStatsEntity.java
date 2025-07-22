package dev.application.analyze.bm_m004;

import dev.common.entity.MetaEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 読み込んだデータから結果マスタにマッピングさせるためのDTOクラス
 * @author shiraishitoshio
 *
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class TeamTimeSegmentShootingStatsEntity extends MetaEntity {

	/** データカテゴリ */
	private String dataCategory;

	/** ホームチーム */
	private String teamName;

	/** アウェーチーム */
	private String awayTeamName;

	/** 自チーム0-10分シュート平均数 */
	private String team0to10MeanShootCount;

	/** 自チーム11-20分シュート平均数 */
	private String team11to20MeanShootCount;

	/** 自チーム21-30分シュート平均数 */
	private String team21to30MeanShootCount;

	/** 自チーム31-40分シュート平均数 */
	private String team31to40MeanShootCount;

	/** 自チーム41-45分シュート平均数 */
	private String team41to45MeanShootCount;

	/** 自チーム46-50分シュート平均数 */
	private String team46to50MeanShootCount;

	/** 自チーム51-60分シュート平均数 */
	private String team51to60MeanShootCount;

	/** 自チーム61-70分シュート平均数 */
	private String team61to70MeanShootCount;

	/** 自チーム71-80分シュート平均数 */
	private String team71to80MeanShootCount;

	/** 自チーム81-90分シュート平均数 */
	private String team81to90MeanShootCount;

	/** 自チームアディショナルタイムシュート平均数 */
	private String teamAddiMeanShootCount;

	/** 自チーム0-10分枠内シュート平均数 */
	private String team0to10MeanShootInCount;

	/** 自チーム11-20分枠内シュート平均数 */
	private String team11to20MeanShootInCount;

	/** 自チーム21-30分枠内シュート平均数 */
	private String team21to30MeanShootInCount;

	/** 自チーム31-40分枠内シュート平均数 */
	private String team31to40MeanShootInCount;

	/** 自チーム41-45分枠内シュート平均数 */
	private String team41to45MeanShootInCount;

	/** 自チーム46-50分枠内シュート平均数 */
	private String team46to50MeanShootInCount;

	/** 自チーム51-60分枠内シュート平均数 */
	private String team51to60MeanShootInCount;

	/** 自チーム61-70分枠内シュート平均数 */
	private String team61to70MeanShootInCount;

	/** 自チーム71-80分枠内シュート平均数 */
	private String team71to80MeanShootInCount;

	/** 自チーム81-90分枠内シュート平均数 */
	private String team81to90MeanShootInCount;

	/** 自チームアディショナルタイム枠内シュート平均数 */
	private String teamAddiMeanShootInCount;

	/** 自チーム0-10分ビッグチャンス平均数 */
	private String team0to10MeanBigChanceCount;

	/** 自チーム11-20分ビッグチャンス平均数 */
	private String team11to20MeanBigChanceCount;

	/** 自チーム21-30分ビッグチャンス平均数 */
	private String team21to30MeanBigChanceCount;

	/** 自チーム31-40分ビッグチャンス平均数 */
	private String team31to40MeanBigChanceCount;

	/** 自チーム41-45分ビッグチャンス平均数 */
	private String team41to45MeanBigChanceCount;

	/** 自チーム46-50分ビッグチャンス平均数 */
	private String team46to50MeanBigChanceCount;

	/** 自チーム51-60分ビッグチャンス平均数 */
	private String team51to60MeanBigChanceCount;

	/** 自チーム61-70分ビッグチャンス平均数 */
	private String team61to70MeanBigChanceCount;

	/** 自チーム71-80分ビッグチャンス平均数 */
	private String team71to80MeanBigChanceCount;

	/** 自チーム81-90分ビッグチャンス平均数 */
	private String team81to90MeanBigChanceCount;

	/** 自チームアディショナルタイムビッグチャンス平均数 */
	private String teamAddiMeanBigChanceCount;

	/** 自チーム0-10分フリーキック平均数 */
	private String team0to10MeanFreeKickCount;

	/** 自チーム11-20分フリーキック平均数 */
	private String team11to20MeanFreeKickCount;

	/** 自チーム21-30分フリーキック平均数 */
	private String team21to30MeanFreeKickCount;

	/** 自チーム31-40分フリーキック平均数 */
	private String team31to40MeanFreeKickCount;

	/** 自チーム41-45分フリーキック平均数 */
	private String team41to45MeanFreeKickCount;

	/** 自チーム46-50分フリーキック平均数 */
	private String team46to50MeanFreeKickCount;

	/** 自チーム51-60分フリーキック平均数 */
	private String team51to60MeanFreeKickCount;

	/** 自チーム61-70分フリーキック平均数 */
	private String team61to70MeanFreeKickCount;

	/** 自チーム71-80分フリーキック平均数 */
	private String team71to80MeanFreeKickCount;

	/** 自チーム81-90分フリーキック平均数 */
	private String team81to90MeanFreeKickCount;

	/** 自チームアディショナルタイムフリーキック平均数 */
	private String teamAddiMeanFreeKickCount;

	/** 自チーム0-10分オフサイド平均数 */
	private String team0to10MeanOffsideCount;

	/** 自チーム11-20分オフサイド平均数 */
	private String team11to20MeanOffsideCount;

	/** 自チーム21-30分オフサイド平均数 */
	private String team21to30MeanOffsideCount;

	/** 自チーム31-40分オフサイド平均数 */
	private String team31to40MeanOffsideCount;

	/** 自チーム41-45分オフサイド平均数 */
	private String team41to45MeanOffsideCount;

	/** 自チーム46-50分オフサイド平均数 */
	private String team46to50MeanOffsideCount;

	/** 自チーム51-60分オフサイド平均数 */
	private String team51to60MeanOffsideCount;

	/** 自チーム61-70分オフサイド平均数 */
	private String team61to70MeanOffsideCount;

	/** 自チーム71-80分オフサイド平均数 */
	private String team71to80MeanOffsideCount;

	/** 自チーム81-90分オフサイド平均数 */
	private String team81to90MeanOffsideCount;

	/** 自チームアディショナルタイムオフサイド平均数 */
	private String teamAddiMeanOffsideCount;

	/** 自チーム0-10分ファウル平均数 */
	private String team0to10MeanFoulCount;

	/** 自チーム11-20分ファウル平均数 */
	private String team11to20MeanFoulCount;

	/** 自チーム21-30分ファウル平均数 */
	private String team21to30MeanFoulCount;

	/** 自チーム31-40分ファウル平均数 */
	private String team31to40MeanFoulCount;

	/** 自チーム41-45分ファウル平均数 */
	private String team41to45MeanFoulCount;

	/** 自チーム46-50分ファウル平均数 */
	private String team46to50MeanFoulCount;

	/** 自チーム51-60分ファウル平均数 */
	private String team51to60MeanFoulCount;

	/** 自チーム61-70分ファウル平均数 */
	private String team61to70MeanFoulCount;

	/** 自チーム71-80分ファウル平均数 */
	private String team71to80MeanFoulCount;

	/** 自チーム81-90分ファウル平均数 */
	private String team81to90MeanFoulCount;

	/** 自チームアディショナルタイムファウル平均数 */
	private String teamAddiMeanFoulCount;

	/** 自チーム0-10分イエローカード平均数 */
	private String team0to10MeanYellowCardCount;

	/** 自チーム11-20分イエローカード平均数 */
	private String team11to20MeanYellowCardCount;

	/** 自チーム21-30分イエローカード平均数 */
	private String team21to30MeanYellowCardCount;

	/** 自チーム31-40分イエローカード平均数 */
	private String team31to40MeanYellowCardCount;

	/** 自チーム41-45分イエローカード平均数 */
	private String team41to45MeanYellowCardCount;

	/** 自チーム46-50分イエローカード平均数 */
	private String team46to50MeanYellowCardCount;

	/** 自チーム51-60分イエローカード平均数 */
	private String team51to60MeanYellowCardCount;

	/** 自チーム61-70分イエローカード平均数 */
	private String team61to70MeanYellowCardCount;

	/** 自チーム71-80分イエローカード平均数 */
	private String team71to80MeanYellowCardCount;

	/** 自チーム81-90分イエローカード平均数 */
	private String team81to90MeanYellowCardCount;

	/** 自チームアディショナルタイムイエローカード平均数 */
	private String teamAddiMeanYellowCardCount;

	/** 自チーム0-10分レッドカード平均数 */
	private String team0to10MeanRedCardCount;

	/** 自チーム11-20分レッドカード平均数 */
	private String team11to20MeanRedCardCount;

	/** 自チーム21-30分レッドカード平均数 */
	private String team21to30MeanRedCardCount;

	/** 自チーム31-40分レッドカード平均数 */
	private String team31to40MeanRedCardCount;

	/** 自チーム41-45分レッドカード平均数 */
	private String team41to45MeanRedCardCount;

	/** 自チーム46-50分レッドカード平均数 */
	private String team46to50MeanRedCardCount;

	/** 自チーム51-60分レッドカード平均数 */
	private String team51to60MeanRedCardCount;

	/** 自チーム61-70分レッドカード平均数 */
	private String team61to70MeanRedCardCount;

	/** 自チーム71-80分レッドカード平均数 */
	private String team71to80MeanRedCardCount;

	/** 自チーム81-90分レッドカード平均数 */
	private String team81to90MeanRedCardCount;

	/** 自チームアディショナルタイムレッドカード平均数 */
	private String teamAddiMeanRedCardCount;

}
