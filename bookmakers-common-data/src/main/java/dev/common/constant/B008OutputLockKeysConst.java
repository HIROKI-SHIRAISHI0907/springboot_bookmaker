// dev/common/constant/B008OutputLockKeys.java
package dev.common.constant;

/**
 * fin/b008_fin_getting_data_*.json への書き込みに対する排他ロックキー定義。
 * FinGettingService(API)とRealFinDataConvertJsonStat(バッチ)の両方から参照する。
 * 他の用途でadvisory lockを追加する場合は、ここでキーの衝突が無いよう一元管理すること。
 */
public final class B008OutputLockKeysConst {
	private B008OutputLockKeysConst() {}

	/** fin/b008_fin_getting_data_*.json の read-check-write区間を排他するためのキー */
	public static final long B008_FIN_GETTING_JSON = 2008080100L;

	/** b016_game_detail_path_&lt;連番&gt;.json の連番を決めて書く区間を排他するためのキー（2026-11 追加。FinGettingService） */
	public static final long B016_GAME_DETAIL_PATH_JSON = 2016110100L;

}