package dev.mng.analyze.interf;

import dev.mng.analyze.bm_c002.CsvMngInputDTO;

/**
 * 未来統計用サービスクラスIF
 * @author shiraishitoshio
 *
 */
public interface TargetMngIF {

	/**
	 * 実行共通メソッド
	 * @return
	 * @throws Exception
	 */
	public void execute(CsvMngInputDTO input) throws Exception;

}
