package dev.application.analyze.bm_m021;

import lombok.Data;

/**
 * データ用格納クラス(3分割データはRetentionDataクラスを経由して保持する)
 * @author shiraishitoshio
 *
 */
@Data
public class FinalData {

	/** 保持率 */
	private String possession;

	/** 3分割データ(パス) */
	private RetentionData pass;

	/** 3分割データ(ロングパス) */
	private RetentionData longPass;

	/** 3分割データ(ファイナルサードパス) */
	private RetentionData finalThirdPass;

	/** 3分割データ(クロス) */
	private RetentionData cross;

	/** 3分割データ(タックル) */
	private RetentionData tackle;

}
