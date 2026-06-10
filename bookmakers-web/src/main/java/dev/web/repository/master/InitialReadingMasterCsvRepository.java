package dev.web.repository.master;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.common.entity.InitialReadingMasterCsvEntity;


/**
 * initial_reading_csv_master 操作用リポジトリ
 *
 * マスタ初回登録確認の登録・更新・取得を行う。
 */
@Repository
public class InitialReadingMasterCsvRepository {

	private final NamedParameterJdbcTemplate masterJdbcTemplate;

	public InitialReadingMasterCsvRepository(
			@Qualifier("webMasterJdbcTemplate") NamedParameterJdbcTemplate masterJdbcTemplate) {
		this.masterJdbcTemplate = masterJdbcTemplate;
	}

	/**
	 * 新規登録
	 */
	public int insert(InitialReadingMasterCsvEntity entity) {

		String sql = """
				INSERT INTO initial_reading_csv_master (
				  master_name,
				  country,
				  league,
				  initial_flg,
				  register_id,
				  register_time,
				  update_id,
				  update_time
				) VALUES (
				  :masterName,
				  :country,
				  :league,
				  :initialFlg,
				  'SYSTEM',
				  NOW(),
				  'SYSTEM',
				  NOW()
				)
				""";

		Map<String, Object> params = new HashMap<>();
		params.put("masterName", entity.getMasterName());
		params.put("country", entity.getCountry());
		params.put("league", entity.getLeague());
		params.put("initialFlg", entity.getInitialFlg());

		return masterJdbcTemplate.update(sql, params);
	}

	/**
	 * 同一 business key の既存データ取得
	 */
	public List<InitialReadingMasterCsvEntity> findData(String master_name,
			String country, String league) {

		String sql = """
				SELECT
				  id,
				  master_name,
				  country,
				  league,
				  initial_flg
				FROM initial_reading_csv_master
				WHERE master_name = :masterName
				  AND country = :country
				  AND league = :league
				  AND initial_flg = '0'
				""";

		Map<String, Object> params = Map.of(
				"masterName", master_name,
				"country", country,
				"league", league);

		return masterJdbcTemplate.query(
				sql,
				params,
				(rs, rowNum) -> {
					InitialReadingMasterCsvEntity e = new InitialReadingMasterCsvEntity();
					e.setId(rs.getString("id"));
					e.setMasterName(rs.getString("master_name"));
					e.setCountry(rs.getString("country"));
					e.setLeague(rs.getString("league"));
					e.setInitialFlg(rs.getString("initial_flg"));
					return e;
				});
	}

	/**
	 * 更新
	 */
	public int update(InitialReadingMasterCsvEntity entity) {

		String sql = """
				UPDATE initial_reading_csv_master
				SET initial_flg     = '1',
				    update_id   = 'SYSTEM',
				    update_time = NOW()
				WHERE master_name = :masterName
				  AND country = :country
				  AND league = :league
				""";

		Map<String, Object> params = new HashMap<>();
		params.put("master_name", entity.getMasterName());
		params.put("country", entity.getCountry());
		params.put("league", entity.getLeague());

		return masterJdbcTemplate.update(sql, params);
	}

}
