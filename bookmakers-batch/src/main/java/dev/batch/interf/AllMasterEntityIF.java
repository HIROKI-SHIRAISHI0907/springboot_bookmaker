package dev.batch.interf;

import java.util.List;

import dev.common.entity.AllLeagueMasterEntity;


/**
 * マスタデータ用共通インターフェース
 * @author shiraishitoshio
 *
 */
public interface AllMasterEntityIF {

	/**
	 * マスタデータ処理メソッド
	 * @param entities エンティティ
	 */
	public void masterStat(String file,
			List<AllLeagueMasterEntity> entities) throws Exception;

}
