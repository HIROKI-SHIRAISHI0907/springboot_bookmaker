package dev.common.entity;

import java.io.Serializable;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 試合詳細: ゴール（goal_*.csv）
 * <p>試合詳細スクレイピング（B016: MatchDetailData_aws_S3.py）が aws-s3-detail-data-csv に出力した CSV の 1 行。
 * 値は CSV のまま文字列で持つ（値が無いときの「-」もそのまま）。</p>
 * @author shiraishitoshio
 *
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class MatchDetailGoalEntity extends MetaEntity implements Serializable {

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

	/** 時間 */
	private String timeLabel;

	/** 分(数値) */
	private String minute;

	/** 追加分 */
	private String addedMinute;

	/** ハーフ */
	private String half;

	/** 得点側 */
	private String scoringSide;

	/** 得点チーム */
	private String scoringTeamName;

	/** 得点者 */
	private String scorerName;

	/** 得点者の所属チーム */
	private String scorerTeamName;

	/** オウンゴール */
	private String ownGoalFlg;

	/** PK */
	private String penaltyFlg;

	/** アシスト */
	private String assistName;

	/** その時点スコア */
	private String scoreAtTime;

	/** 種類 */
	private String incidentType;

	/** 補足 */
	private String note;

	/** 選手URL */
	private String playerUrl;

}
