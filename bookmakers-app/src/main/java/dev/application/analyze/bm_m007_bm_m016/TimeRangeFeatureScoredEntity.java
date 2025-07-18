package dev.application.analyze.bm_m007_bm_m016;

import java.io.Serializable;

import lombok.Data;

/**
 * within_data_xminutes_homeoraway_scoredデータEntity
 * @author shiraishitoshio
 *
 */
@Data
public class TimeRangeFeatureScoredEntity implements Serializable {

	/** シリアライズ化 */
	private static final long serialVersionUID = 1L;

	/** ID */
	private String id;

	/** 国 */
	private String country;

	/** リーグ */
	private String league;

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
