package dev.web.repository.master;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.web.api.bm_u001.StatSizeFinalizeDTO;

/**
 * stat_size_finalize_master 操作用リポジトリ.
 *
 * MyBatis版（@Mapper）を NamedParameterJdbcTemplate 版に移植。
 *
 * @author shiraishitoshio
 */
@Repository
public class StatSizeFinalizeMasterRepository {

	private final NamedParameterJdbcTemplate masterJdbcTemplate;

	public StatSizeFinalizeMasterRepository(
			@Qualifier("webMasterJdbcTemplate") NamedParameterJdbcTemplate masterJdbcTemplate) {
		this.masterJdbcTemplate = masterJdbcTemplate;
	}

	public int insert(StatSizeFinalizeDTO entity) {

		String sql = """
				INSERT INTO stat_size_finalize_master (
				  option_num, options, valid_flg, register_id, register_time, update_id, update_time
				) VALUES (
				  :optionNum, :options, :validFlg,
				  :registerId, :registerTime, :updateId, :updateTime
				)
				""";

		Map<String, Object> params = new HashMap<>();
		params.put("optionNum", entity.getOptionNum());
		params.put("options", entity.getOptions());
		params.put("valid_flg", entity.getValidFlg());
		params.put("registerId", entity.getRegisterId());
		params.put("registerTime", entity.getRegisterTime());
		params.put("updateId", entity.getUpdateId());
		params.put("updateTime", entity.getUpdateTime());

		return masterJdbcTemplate.update(sql, params);
	}

	public List<StatSizeFinalizeDTO> findFlgData(String flg) {

		String sql = """
				SELECT id, option_num, options, valid_flg
				FROM stat_size_finalize_master
				WHERE valid_flg = :validFlg
				ORDER BY option_num, id
				""";

		Map<String, Object> params = Map.of("validFlg", flg);

		return masterJdbcTemplate.query(
				sql,
				params,
				(rs, rowNum) -> {
					StatSizeFinalizeDTO e = new StatSizeFinalizeDTO();
					e.setId(rs.getString("id"));
					e.setOptionNum(rs.getString("option_num"));
					e.setOptions(rs.getString("options"));
					e.setValidFlg(rs.getString("valid_flg"));
					return e;
				});
	}

	public List<StatSizeFinalizeDTO> findData(String optionNum, String options) {

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
					StatSizeFinalizeDTO e = new StatSizeFinalizeDTO();
					e.setId(rs.getString("id"));
					return e;
				});
	}

	public int update(StatSizeFinalizeDTO entity) {

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
		params.put("valid_flg", entity.getValidFlg());

		return masterJdbcTemplate.update(sql, params);
	}

}
