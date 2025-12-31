package dev.batch.interf;

import java.util.List;
import java.util.Map;

import dev.common.entity.TeamMemberMasterEntity;

/**
 * 選手データ用共通インターフェース
 * @author shiraishitoshio
 *
 */
public interface TeamMemberEntityIF {

	/**
	 * 選手データ処理メソッド
	 * @param entities エンティティ
	 */
	public void teamMemberStat(Map<String, List<TeamMemberMasterEntity>> entities) throws Exception;

}
