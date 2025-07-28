package dev.application.analyze.interf;

import java.util.List;
import java.util.Map;

import dev.common.entity.CountryLeagueSeasonMasterEntity;

/**
 * シーズンデータ用共通インターフェース
 * @author shiraishitoshio
 *
 */
public interface SeasonEntityIF {

	/**
	 * シーズンデータ処理メソッド
	 * @param entities エンティティ
	 */
	public void seasonStat(Map<String, List<CountryLeagueSeasonMasterEntity>> entities) throws Exception;

}
