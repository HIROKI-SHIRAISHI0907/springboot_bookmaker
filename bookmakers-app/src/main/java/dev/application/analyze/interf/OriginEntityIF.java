package dev.application.analyze.interf;

import java.util.List;
import java.util.Map;

import dev.common.entity.DataEntity;

/**
 * 起源データ用共通インターフェース
 * @author shiraishitoshio
 *
 */
public interface OriginEntityIF {

	/**
	 * 起源データ処理メソッド
	 * @param entities エンティティ
	 */
	public void originStat(Map<String, List<DataEntity>> entities) throws Exception;

}
