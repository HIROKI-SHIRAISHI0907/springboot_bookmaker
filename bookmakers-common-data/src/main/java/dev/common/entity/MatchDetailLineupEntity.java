package dev.common.entity;

import java.io.Serializable;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 試合詳細: ラインアップ 選手1人1行（lineup_*.csv）
 * <p>試合詳細スクレイピング（B016: MatchDetailData_aws_S3.py）が aws-s3-detail-data-csv に出力した CSV の 1 行。
 * 値は CSV のまま文字列で持つ（値が無いときの「-」もそのまま）。</p>
 * @author shiraishitoshio
 *
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class MatchDetailLineupEntity extends MetaEntity implements Serializable {

	/** シリアライズ化 */
	private static final long serialVersionUID = 1L;

	/** ファイル（S3 キー） */
	private String file;

	/** 試合ID */
	private String matchId;

	/** 試合日 */
	private String matchDate;

	/** ホームチーム */
	private String homeTeamName;

	/** アウェーチーム */
	private String awayTeamName;

	/** 側 */
	private String side;

	/** チーム */
	private String teamName;

	/** 区分 */
	private String lineupType;

	/** 選手ID */
	private String playerId;

	/** 背番号 */
	private String jerseyNumber;

	/** 選手名 */
	private String playerName;

	/** 国籍 */
	private String nationality;

	/** 役割 */
	private String role;

	/** 出場 */
	private String playedFlg;

	/** 途中出場の分 */
	private String subInMinute;

	/** 途中出場で代わった選手 */
	private String subInForPlayerName;

	/** 途中交代の分 */
	private String subOutMinute;

	/** 途中交代で入った選手 */
	private String subOutForPlayerName;

	/** 終了時レーティング */
	private String finalRating;

	/** ピッチ行 */
	private String pitchRow;

	/** ピッチ列 */
	private String pitchCol;

	/** 得点数 */
	private String goalCount;

	/** オウンゴール数 */
	private String ownGoalCount;

	/** イベント */
	private String events;

}
