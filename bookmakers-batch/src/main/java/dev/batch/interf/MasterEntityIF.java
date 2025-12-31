package dev.batch.interf;

import java.util.List;

import dev.common.entity.CountryLeagueMasterEntity;


/**
 * マスタデータ用共通インターフェース
 * @author shiraishitoshio
 *
 */
public interface MasterEntityIF {

	/**
	 * マスタデータ処理メソッド
	 * @param entities エンティティ
	 */
	public void masterStat(List<List<CountryLeagueMasterEntity>> entities) throws Exception;

}
