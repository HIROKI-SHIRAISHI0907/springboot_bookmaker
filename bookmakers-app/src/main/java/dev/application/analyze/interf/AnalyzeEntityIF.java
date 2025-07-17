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
	 * @param country 国
	 * @param league リーグ
	 * @param entities エンティティ
	 */
	public void calcStat(Map<String, Map<String, List<BookDataEntity>>> entities);

}
