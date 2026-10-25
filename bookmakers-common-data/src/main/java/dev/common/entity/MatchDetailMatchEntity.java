package dev.common.entity;

import java.io.Serializable;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 試合詳細: 試合の基本情報（match_*.csv）
 * <p>試合詳細スクレイピング（B016: MatchDetailData_aws_S3.py）が aws-s3-detail-data-csv に出力した CSV の 1 行。
 * 値は CSV のまま文字列で持つ（値が無いときの「-」もそのまま）。</p>
 * @author shiraishitoshio
 *
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class MatchDetailMatchEntity extends MetaEntity implements Serializable {

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

	/** 試合国及びカテゴリ */
	private String category;

	/** ホームスコア */
	private String homeScore;

	/** アウェースコア */
	private String awayScore;

	/** 状態 */
	private String status;

	/** 開始日時 */
	private String startTime;

	/** 試合URL */
	private String matchUrl;

	/** 取得日時(JST) */
	private String scrapedAt;

}
