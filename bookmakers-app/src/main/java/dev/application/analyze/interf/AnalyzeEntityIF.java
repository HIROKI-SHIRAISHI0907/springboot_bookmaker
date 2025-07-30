package dev.application.analyze.interf;

import java.util.List;
import java.util.Map;

import dev.common.entity.BookDataEntity;

/**
 * 統計分析用共通インターフェース
 * @author shiraishitoshio
 *
 */
public interface AnalyzeEntityIF {

	/**
	 * 統計分析処理メソッド
	 * @param entities エンティティ(国-リーグ, ホーム-アウェー, エンティティリスト)
	 */
	public void calcStat(Map<String, Map<String, List<BookDataEntity>>> entities);

}
