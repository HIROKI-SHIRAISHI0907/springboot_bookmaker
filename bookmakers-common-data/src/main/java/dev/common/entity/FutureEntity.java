package dev.common.entity;

import java.io.Serializable;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * future_通番.xlsxブックから読み込んだデータをマッピングさせるためのDTOクラス
 * @author shiraishitoshio
 *
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class FutureEntity extends MetaEntity implements Serializable{

	/** シリアライズ化 */
	private static final long serialVersionUID = 1L;

	/** ファイル */
	private String file;

	/** 通番 */
	private String seq;

	/** 対戦チームカテゴリ */
	private String gameTeamCategory;

	/** 試合予定時間 */
	private String futureTime;

	/** ホーム順位 */
	private String homeRank;

	/** アウェー順位 */
	private String awayRank;

	/** ホームチーム */
	private String homeTeamName;

	/** アウェーチーム */
	private String awayTeamName;

	/** ホームチーム最大得点者 */
	private String homeMaxGettingScorer;

	/** アウェーチーム最大得点者 */
	private String awayMaxGettingScorer;

	/** ホームチームホーム得点数 */
	private String homeTeamHomeScore;

	/** ホームチームホーム失点数 */
	private String homeTeamHomeLost;

	/** アウェーチームホーム得点数 */
	private String awayTeamHomeScore;

	/** アウェーチームホーム失点数 */
	private String awayTeamHomeLost;

	/** ホームチームアウェー得点数 */
	private String homeTeamAwayScore;

	/** ホームチームアウェー失点数 */
	private String homeTeamAwayLost;

	/** アウェーチームアウェー得点数 */
	private String awayTeamAwayScore;

	/** アウェーチームアウェー失点数 */
	private String awayTeamAwayLost;

	/** 試合リンク文字列 */
	private String gameLink;

	/** データ取得時間 */
	private String dataTime;

}
