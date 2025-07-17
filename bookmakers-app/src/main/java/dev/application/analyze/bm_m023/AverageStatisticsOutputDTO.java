package dev.application.analyze.bm_m023;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * average_statistics_data outputDTO
 * @author shiraishitoshio
 *
 */
@Setter
@Getter
public class AverageStatisticsOutputDTO {

	/**
	 * 件数
	 */
	private int counter;

	/**
	 * 合計
	 */
	private String sum;

	/**
	 * 標準偏差合計
	 */
	private String sigmaSum;

	/**
	 * 更新フラグ
	 */
	private boolean updFlg;

	/**
	 * 更新ID
	 */
	private String updId;

	/**
	 * 取得データリスト
	 */
	private List<List<String>> selectList;

	/**
	 * 最小リスト
	 */
	private List<String> minData;

	/**
	 * 最大リスト
	 */
	private List<String> maxData;

	/**
	 * 平均リスト
	 */
	private List<String> aveData;

	/**
	 * 標準偏差リスト
	 */
	private List<String> sigmaData;

	/**
	 * 件数リスト
	 */
	private List<Integer> countData;

}
