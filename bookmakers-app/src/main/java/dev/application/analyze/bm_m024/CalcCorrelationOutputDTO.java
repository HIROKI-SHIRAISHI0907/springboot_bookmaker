package dev.application.analyze.bm_m024;

import java.io.Serializable;
import java.util.List;

import lombok.Data;

/**
 * calc_correlation outputDTO
 * @author shiraishitoshio
 *
 */
@Data
public class CalcCorrelationOutputDTO implements Serializable {

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
	private List<CalcCorrelationEntity> list;
}
