package dev.mng.analyze.interf;

import dev.mng.dto.CsvTargetCommonInputDTO;

/**
 * CSV用共通インターフェース
 * @author shiraishitoshio
 *
 */
public interface CsvEntityIF {

	/**
	 * CSV用処理メソッド
	 * @param CsvTargetCommonInputDTO
	 */
	public void calcCsv(CsvTargetCommonInputDTO input);

}
