package dev.common.entity;

import java.io.Serializable;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 試合詳細: サマリーの出来事（summary_*.csv）
 * <p>試合詳細スクレイピング（B016: MatchDetailData_aws_S3.py）が aws-s3-detail-data-csv に出力した CSV の 1 行。
 * 値は CSV のまま文字列で持つ（値が無いときの「-」もそのまま）。</p>
 * @author shiraishitoshio
 *
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class MatchDetailSummaryEntity extends MetaEntity implements Serializable {

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

	/** ハーフ */
	private String half;

	/** ハーフ終了時スコア */
	private String halfScore;

	/** ハーフ内順番 */
	private String halfSeq;

	/** 時間 */
	private String timeLabel;

	/** 分(数値) */
	private String minute;

	/** 追加分 */
	private String addedMinute;

	/** 側 */
	private String side;

	/** チーム */
	private String teamName;

	/** 種類 */
	private String incidentType;

	/** 選手名 */
	private String playerName;

	/** 選手URL */
	private String playerUrl;

	/** 交代OUT選手名 */
	private String subOutPlayerName;

	/** 交代OUT選手URL */
	private String subOutPlayerUrl;

	/** その時点スコア */
	private String scoreAtTime;

	/** 補足 */
	private String note;

	/** 補足2(アシスト等) */
	private String note2;

	/** 説明 */
	private String description;

}
