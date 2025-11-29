package dev.web.api.bm_w004;

import lombok.Data;

/**
 * ScheduledOverviewsAPI（開催予定マッチ情報）
 * /api/{国}/{リーグ}/scheduled-overviews/{seq}
 * の match 要素
 *
 * @author shiraishitoshio
 *
 */
@Data
public class ScheduledOverviewsMatchDTO {

	/** 通番（future_master / surface_overview に紐づく seq） */
	private long seq;

	/** 国名 */
	private String country;

	/** リーグ名 */
	private String league;

	/** ホームチーム名 */
	private String homeTeam;

	/** アウェーチーム名 */
	private String awayTeam;

	/** 開催予定日時（現APIでは null 固定想定。将来拡張用） */
	private String futureTime;

	/** ラウンド番号（現APIでは null 固定想定。将来拡張用） */
	private Integer roundNo;

	/** 集計対象の年度（最新） */
	private Integer gameYear;

	/** 集計対象の月（最新） */
	private Integer gameMonth;

}
