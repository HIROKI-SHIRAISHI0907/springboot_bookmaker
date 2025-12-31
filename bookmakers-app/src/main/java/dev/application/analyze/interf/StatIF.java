package dev.application.analyze.interf;

import java.util.List;
import java.util.Map;

import dev.common.entity.BookDataEntity;

/**
 * 統計分析用サービスクラスIF
 * @author shiraishitoshio
 *
 */
public interface StatIF {

	/**
	 * 実行共通メソッド
	 * @return
	 * @throws Exception
	 */
	public int execute(Map<String, Map<String, List<BookDataEntity>>> stat) throws Exception;

}
