package dev.application.analyze.interf;

/**
 * 手動バッチ共通インターフェース
 * @author shiraishitoshio
 *
 */
public interface BatchIF {

	/**
	 * 手動バッチ処理メソッド
	 */
	public int execute() throws Exception;

}
