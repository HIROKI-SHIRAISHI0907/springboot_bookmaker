package dev.application.analyze.bm_m025;

import java.util.List;

import lombok.Data;

/**
 * average_statistics_data outputDTO
 * @author shiraishitoshio
 *
 */
@Data
public class CalcCorrelationDetailOutputDTO {

	/**
	 * 相関係数リスト
	 */
	private List<String> corrList;

	/**
	 * indexリスト
	 */
	private List<Integer> inducesList;

	/**
	 * 登録フラグ
	 */
	private boolean registerFlg;

	/**
	 * データ取得リスト
	 */
	private List<List<String>> selectResultList;

	/**
	 * データ取得リスト(2つある場合)
	 */
	private List<List<String>> rangeResultList;

	/**
	 * データ取得リスト(2つある場合)
	 */
	private List<List<String>> rangeResultOtherList;

	/**
	 * 存在フラグ
	 */
	private boolean existFlg;

	/**
	 * 存在フラグ(2つある場合)
	 */
	private boolean existOtherFlg;

	/**
	 * 1回きりフラグ
	 */
	private boolean firstFlg;

	/**
	 * id
	 */
	private String id;

}
