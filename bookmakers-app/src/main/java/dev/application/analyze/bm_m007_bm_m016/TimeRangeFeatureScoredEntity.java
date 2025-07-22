package dev.application.analyze.bm_m007_bm_m016;

import java.io.Serializable;

import dev.common.entity.MetaEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * within_data_xminutes_homeoraway_scoredデータEntity
 * @author shiraishitoshio
 *
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class TimeRangeFeatureScoredEntity extends MetaEntity implements Serializable {

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

}
