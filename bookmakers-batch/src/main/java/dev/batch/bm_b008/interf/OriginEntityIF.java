package dev.batch.bm_b008.interf;

// 2026-11-20 bookmakers-app（dev.application.analyze.interf.OriginEntityIF）から移動（B008 を bookmakers-batch で動かすため）

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
