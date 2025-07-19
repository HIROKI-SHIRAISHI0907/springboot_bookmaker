package dev.application.analyze.bm_m017_bm_m018;

import java.io.Serializable;

import lombok.Data;

/**
 * time_range_feature outputDTO
 * @author shiraishitoshio
 *
 */
@Data
public class LeagueScoreTimeBandOutputDTO implements Serializable {

	/** シリアライズ化 */
	private static final long serialVersionUID = 1L;

	/**
	 * ID
	 */
	private String id;

	/**
	 * 時間範囲
	 */
	private String timeRangeArea;

	/**
	 * ホーム時間範囲
	 */
	private String homeTimeRangeArea;

	/**
	 * アウェー時間範囲
	 */
	private String awayTimeRangeArea;

	/**
	 * 対象数
	 */
	private String target;

	/**
	 * 探索数
	 */
	private String search;

	/**
	 * 更新フラグ
	 */
	private boolean updFlg;

}
