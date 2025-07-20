package dev.application.analyze.bm_m021;

import java.io.Serializable;

import lombok.Data;

/**
 * match_classification_result outputDTO
 * @author shiraishitoshio
 *
 */
@Data
public class TeamMatchFinalOutputDTO implements Serializable {

	/** シリアライズ化 */
	private static final long serialVersionUID = 1L;

	/**
	 * 分類モード
	 */
	private String classificationMode;

	/**
	 * 特殊特徴量最終データ
	 */
	private FinalData homeObject;

	/**
	 * 特殊特徴量最終データ
	 */
	private FinalData awayObject;

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
