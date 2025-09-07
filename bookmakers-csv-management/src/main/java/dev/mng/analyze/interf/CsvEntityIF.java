package dev.mng.analyze.interf;

import dev.mng.dto.CsvCommonInputDTO;

/**
 * CSV用共通インターフェース
 * @author shiraishitoshio
 *
 */
public interface CsvEntityIF {

	/**
	 * CSV用処理メソッド
	 * @param CsvCommonInputDTO
	 */
	public void calcCsv(CsvCommonInputDTO input);

}
