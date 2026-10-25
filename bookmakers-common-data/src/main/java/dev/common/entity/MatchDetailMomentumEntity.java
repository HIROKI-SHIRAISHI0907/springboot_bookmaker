package dev.common.entity;

import java.io.Serializable;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 試合詳細: マッチモメンタム 1分1行（momentum_*.csv）
 * <p>試合詳細スクレイピング（B016: MatchDetailData_aws_S3.py）が aws-s3-detail-data-csv に出力した CSV の 1 行。
 * 値は CSV のまま文字列で持つ（値が無いときの「-」もそのまま）。</p>
 * @author shiraishitoshio
 *
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class MatchDetailMomentumEntity extends MetaEntity implements Serializable {

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

	/** ハーフ内番号 */
	private String halfSeq;

	/** 分 */
	private String minuteLabel;

	/** 分(数値) */
	private String minute;

	/** 追加分 */
	private String addedMinute;

	/** 優勢側 */
	private String side;

	/** 優勢チーム */
	private String teamName;

	/** モメンタム値 */
	private String momentumValue;

	/** 符号付きモメンタム値(ホーム+/アウェー-) */
	private String signedMomentumValue;

}
