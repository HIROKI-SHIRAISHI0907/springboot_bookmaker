package dev.application.analyze.bm_m007_bm_m016;

import java.io.Serializable;

import lombok.Data;

/**
 * time_range_feature outputDTO
 * @author shiraishitoshio
 *
 */
@Data
public class TimeRangeFeatureOutputDTO implements Serializable {

	/** シリアライズ化 */
	private static final long serialVersionUID = 1L;

	/**
	 * 国
	 */
	private String country;

	/**
	 * リーグ
	 */
	private String league;

	/**
	 * ホーム
	 */
	private String home;

	/**
	 * アウェー
	 */
	private String away;

	/**
	 * 連番1
	 */
	private String seq1;

	/**
	 * ID
	 */
	private String id;

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
