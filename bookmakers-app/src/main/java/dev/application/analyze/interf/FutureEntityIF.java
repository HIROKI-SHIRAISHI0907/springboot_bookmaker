package dev.application.analyze.interf;

import java.util.List;
import java.util.Map;

import dev.common.entity.FutureEntity;

/**
 * 未来データ用共通インターフェース
 * @author shiraishitoshio
 *
 */
public interface FutureEntityIF {

	/**
	 * 未来データ処理メソッド
	 * @param entities エンティティ
	 */
	public void futureStat(Map<String, List<FutureEntity>> entities);

}
