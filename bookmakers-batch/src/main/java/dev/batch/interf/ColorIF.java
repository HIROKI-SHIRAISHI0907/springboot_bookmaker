package dev.batch.interf;

import java.util.List;

import dev.batch.bm_b006.TeamColorMasterEntity;

/**
 * 色データ用共通インターフェース
 * @author shiraishitoshio
 *
 */
public interface ColorIF {

	/**
	 * 色データ処理メソッド
	 * @param entities エンティティ
	 */
	public void colorStat(List<List<TeamColorMasterEntity>> entities) throws Exception;

}
