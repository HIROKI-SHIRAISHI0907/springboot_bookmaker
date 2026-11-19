package dev.batch.bm_b008.interf;

// 2026-11-20 bookmakers-app（dev.application.analyze.interf.OriginIF）から移動（B008 を bookmakers-batch で動かすため）

/**
 * 起源データインターフェース
 * @author shiraishitoshio
 *
 */
public interface OriginIF {

	/**
	 * 起源データ処理メソッド
	 */
	public int execute() throws Exception;

}
