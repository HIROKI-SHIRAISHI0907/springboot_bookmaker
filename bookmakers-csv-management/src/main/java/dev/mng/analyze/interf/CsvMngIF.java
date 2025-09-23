package dev.mng.analyze.interf;

import dev.mng.dto.CsvTargetCommonInputDTO;

/**
 * 未来統計用サービスクラスIF
 * @author shiraishitoshio
 *
 */
public interface CsvMngIF {

	/**
	 * 実行共通メソッド
	 * @return
	 * @throws Exception
	 */
	public void execute(CsvTargetCommonInputDTO input) throws Exception;

}
