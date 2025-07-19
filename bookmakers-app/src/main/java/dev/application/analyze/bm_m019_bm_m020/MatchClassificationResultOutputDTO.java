package dev.application.analyze.bm_m019_bm_m020;

import java.io.Serializable;
import java.util.List;

import lombok.Data;

/**
 * match_classification_result outputDTO
 * @author shiraishitoshio
 *
 */
@Data
public class MatchClassificationResultOutputDTO implements Serializable {

	/** シリアライズ化 */
	private static final long serialVersionUID = 1L;

	/**
	 * 分類モード
	 */
	private String classificationMode;

	/**
	 * エンティティリスト
	 */
	private List<MatchClassificationResultEntity> entityList;

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

}
