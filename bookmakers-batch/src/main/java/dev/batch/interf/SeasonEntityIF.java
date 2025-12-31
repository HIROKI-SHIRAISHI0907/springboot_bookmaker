package dev.batch.interf;

import java.util.List;

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
	public void seasonStat(List<CountryLeagueSeasonMasterEntity> entities) throws Exception;

}
