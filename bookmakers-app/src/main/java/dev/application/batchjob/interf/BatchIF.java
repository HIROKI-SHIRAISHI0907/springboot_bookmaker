package dev.application.batchjob.interf;

// 2026-11-20 bookmakers-batch（dev.batch.interf.BatchIF）から移動（B011 を統計用の bookmakers-app で動かすため）

/**
 * バッチInterface
 * @author shiraishitoshio
 *
 */
public interface BatchIF {

	/**
	 * バッチ実行
	 * @return
	 */
	public int execute(boolean readyFlg) throws Exception;

}
