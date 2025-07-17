package dev.application.analyze.bm_m024;

import lombok.Getter;
import lombok.Setter;

/**
 * average_statistics_data outputDTO
 * @author shiraishitoshio
 *
 */
@Setter
@Getter
public class CalcCorrelationOutputDTO {

	/**
	 * 積
	 */
	private String timesOfStatAndScoreFlg;

	/**
	 * 2乗の和
	 */
	private String summationOfSecondPower;

	/**
	 * 通常の和
	 */
	private String secondPowerOfSummation;

	/**
	 * 件数
	 */
	private int counter;

	/**
	 * 更新フラグ
	 */
	private boolean updFlg;

}
