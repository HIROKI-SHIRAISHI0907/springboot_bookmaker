package dev.mng.analyze.bm_c001;

import lombok.Data;

/**
 * StatSizeFinalizeCsvEntity
 * @author shiraishitoshio
 *
 */
@Data
public class StatSizeFinalizeMasterCsvEntity {

	/** 選択肢 */
	private String options;

	/** フラグ(0:有効,1:無効) */
	private String flg;

}
