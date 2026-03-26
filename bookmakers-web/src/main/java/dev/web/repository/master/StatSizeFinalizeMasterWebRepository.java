package dev.web.repository.master;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.common.entity.StatSizeFinalizeEntity;

/**
 * stat_size_finalize_master 操作用リポジトリ.
 *
 * MyBatis版（@Mapper）を NamedParameterJdbcTemplate 版に移植。
 *
 * @author shiraishitoshio
 */
@Repository
public class StatSizeFinalizeMasterWebRepository {

	private final NamedParameterJdbcTemplate masterJdbcTemplate;

	public StatSizeFinalizeMasterWebRepository(
			@Qualifier("webMasterJdbcTemplate") NamedParameterJdbcTemplate masterJdbcTemplate) {
		this.masterJdbcTemplate = masterJdbcTemplate;
	}

	public int insert(StatSizeFinalizeEntity entity) {

		String sql = """
				INSERT INTO stat_size_finalize_master (
				  option_num, options, valid_flg, register_id, register_time, update_id, update_time
				) VALUES (
				  :optionNum, :options, :validFlg,
				  'SYSTEM', NOW(), 'SYSTEM', NOW()
				)
				""";

		Map<String, Object> params = new HashMap<>();
		params.put("optionNum", entity.getOptionNum());
		params.put("options", entity.getOptions());
		params.put("validFlg", entity.getValidFlg());

		return masterJdbcTemplate.update(sql, params);
	}

	public List<StatSizeFinalizeEntity> findData(String optionNum, String options) {

		String sql = """
				SELECT id
				FROM stat_size_finalize_master
				WHERE option_num = :optionNum
				  AND options    = :options
				""";

		Map<String, Object> params = Map.of(
				"optionNum", optionNum,
				"options", options);

		return masterJdbcTemplate.query(
				sql,
				params,
				(rs, rowNum) -> {
					StatSizeFinalizeEntity e = new StatSizeFinalizeEntity();
					e.setId(rs.getString("id"));
					return e;
				});
	}

	public int update(StatSizeFinalizeEntity entity) {

		String sql = """
				UPDATE stat_size_finalize_master
				SET option_num = :optionNum,
				    options    = :options,
				    valid_flg        = :validFlg
				WHERE id = :id
				""";

		Map<String, Object> params = new HashMap<>();
		params.put("id", entity.getId());
		params.put("optionNum", entity.getOptionNum());
		params.put("options", entity.getOptions());
		params.put("validFlg", entity.getValidFlg());

		return masterJdbcTemplate.update(sql, params);
	}

}
