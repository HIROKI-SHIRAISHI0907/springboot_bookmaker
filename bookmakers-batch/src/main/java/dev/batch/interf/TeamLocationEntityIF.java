package dev.batch.interf;

import java.util.List;

import dev.common.entity.TeamLocationEntity;

/**
 * TeamLocation共通インターフェース
 * @author shiraishitoshio
 *
 */
public interface TeamLocationEntityIF {

	/**
	 * TeamLocation処理メソッド
	 * @param map 地理データ
	 * @param readyFlg 事前準備フラグ
	 */
	public void teamLocationStat(List<TeamLocationEntity> map, boolean readyFlg) throws Exception;

}
