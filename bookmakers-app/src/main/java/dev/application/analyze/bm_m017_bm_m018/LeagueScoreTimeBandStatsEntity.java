package dev.application.analyze.bm_m017_bm_m018;

import lombok.Data;

/**
 * within_data_xminutesデータEntity
 * @author shiraishitoshio
 *
 */
@Data
public class LeagueScoreTimeBandStatsEntity {

	/** ID */
	private String id;

	/** 国 */
	private String country;

	/** リーグ */
	private String league;

	/** 合計スコア */
	private String sumScoreValue;

	/** 試合時間範囲 */
	private String timeRangeArea;

	/** 該当数 */
	private String target;

	/** 探索数 */
	private String search;

	/** 割合 */
	private String ratio;

	/** 登録ID */
	private String registerId;

	/** 登録時間 */
	private String registerTime;

	/** 更新ID */
	private String updateId;

	/** 更新時間 */
	private String updateTime;

}
