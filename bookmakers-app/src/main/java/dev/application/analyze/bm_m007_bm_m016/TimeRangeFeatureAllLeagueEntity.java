package dev.application.analyze.bm_m007_bm_m016;

import lombok.Data;

/**
 * within_data_xminutes_homeoraway_all_leagueデータEntity
 * @author shiraishitoshio
 *
 */
@Data
public class TimeRangeFeatureAllLeagueEntity {

	/** ID */
	private String id;

	/** 試合時間範囲 */
	private String timeRange;

	/** 特徴量 */
	private String feature;

	/** 閾値 */
	private String thresHold;

	/** 該当数 */
	private String target;

	/** 探索数 */
	private String search;

	/** 割合 */
	private String ratio;

	/** テーブル名 */
	private String tableName;

	/** 登録ID */
	private String registerId;

	/** 登録時間 */
	private String registerTime;

	/** 更新ID */
	private String updateId;

	/** 更新時間 */
	private String updateTime;

}
