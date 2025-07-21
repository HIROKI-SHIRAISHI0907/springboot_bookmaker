package dev.application.analyze.bm_m023;

import java.io.Serializable;
import java.util.List;

import lombok.Data;

/**
 * match_classification_result outputDTO
 * @author shiraishitoshio
 *
 */
@Data
public class ScoreBasedFeatureOutputDTO implements Serializable {

	/** シリアライズ化 */
	private static final long serialVersionUID = 1L;

	/**
	 * ID
	 */
	private String id;

	/**
	 * 件数
	 */
	private String cnt;

	/**
	 * 更新フラグ
	 */
	private boolean updFlg;

	/**
	 * リスト
	 */
	private List<ScoreBasedFeatureStatsEntity> list;

}
