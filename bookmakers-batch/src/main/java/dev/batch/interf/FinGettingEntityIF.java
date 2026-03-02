package dev.batch.interf;

import java.util.List;
import java.util.Map;

import dev.common.entity.DataEntity;

/**
 * FinGetting共通インターフェース
 * @author shiraishitoshio
 *
 */
public interface FinGettingEntityIF {

	/**
	 * FinGetting処理メソッド
	 * @param entities エンティティ
	 */
	public void finGettingStat(Map<String, List<DataEntity>> entities) throws Exception;

}
