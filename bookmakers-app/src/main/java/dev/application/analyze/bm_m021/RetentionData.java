package dev.application.analyze.bm_m021;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 特殊特徴量保持データ
 * @author shiraishitoshio
 *
 */
@Data
@AllArgsConstructor
public class RetentionData {

	/** 割合 */
	private String ratio;

	/** 成功数 */
	private String success;

	/** 試行数 */
	private String trys;

}
