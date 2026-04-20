package dev.web.repository.master;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.web.api.bm_a015.PointSettingEntity;

/**
 * point_setting_master 操作用リポジトリ
 *
 * 勝ち点設定の登録・更新・取得を行う。
 */
@Repository
public class PointSettingRepository {

	private final NamedParameterJdbcTemplate masterJdbcTemplate;

	public PointSettingRepository(
			@Qualifier("webMasterJdbcTemplate") NamedParameterJdbcTemplate masterJdbcTemplate) {
		this.masterJdbcTemplate = masterJdbcTemplate;
	}

	/**
	 * 新規登録
	 */
	public int insert(PointSettingEntity entity) {

		String sql = """
				INSERT INTO point_setting_master (
				  country,
				  league,
				  win,
				  lose,
				  draw,
				  remarks,
				  del_flg,
				  register_id,
				  register_time,
				  update_id,
				  update_time
				) VALUES (
				  :country,
				  :league,
				  :win,
				  :lose,
				  :draw,
				  :remarks,
				  :delFlg,
				  'SYSTEM',
				  NOW(),
				  'SYSTEM',
				  NOW()
				)
				""";

		Map<String, Object> params = new HashMap<>();
		params.put("country", entity.getCountry());
		params.put("league", entity.getLeague());
		params.put("win", entity.getWin());
		params.put("lose", entity.getLose());
		params.put("draw", entity.getDraw());
		params.put("remarks", normalizeRemarks(entity.getRemarks()));
		params.put("delFlg", normalizeDelFlg(entity.getDelFlg()));

		return masterJdbcTemplate.update(sql, params);
	}

	/**
	 * 全件取得（論理削除除く）
	 */
	public List<PointSettingEntity> findAll() {

		String sql = """
				SELECT
				  id,
				  country,
				  league,
				  win,
				  lose,
				  draw,
				  remarks,
				  del_flg
				FROM point_setting_master
				WHERE del_flg = '0'
				ORDER BY country, league, COALESCE(remarks, '')
				""";

		return masterJdbcTemplate.query(
				sql,
				new HashMap<>(),
				(rs, rowNum) -> {
					PointSettingEntity e = new PointSettingEntity();
					e.setId(rs.getString("id"));
					e.setCountry(rs.getString("country"));
					e.setLeague(rs.getString("league"));
					e.setWin((Integer) rs.getObject("win"));
					e.setLose((Integer) rs.getObject("lose"));
					e.setDraw((Integer) rs.getObject("draw"));
					e.setRemarks(rs.getString("remarks"));
					e.setDelFlg(rs.getString("del_flg"));
					return e;
				});
	}

	/**
	 * 同一 business key の既存データ取得
	 * 条件: country + league + remarks
	 */
	public List<PointSettingEntity> findData(String country, String league, String remarks) {

		String sql = """
				SELECT
				  id,
				  country,
				  league,
				  win,
				  lose,
				  draw,
				  remarks,
				  del_flg
				FROM point_setting_master
				WHERE country = :country
				  AND league  = :league
				  AND COALESCE(remarks, '') = COALESCE(:remarks, '')
				  AND del_flg = '0'
				""";

		Map<String, Object> params = Map.of(
				"country", country,
				"league", league,
				"remarks", normalizeRemarks(remarks));

		return masterJdbcTemplate.query(
				sql,
				params,
				(rs, rowNum) -> {
					PointSettingEntity e = new PointSettingEntity();
					e.setId(rs.getString("id"));
					e.setCountry(rs.getString("country"));
					e.setLeague(rs.getString("league"));
					e.setWin((Integer) rs.getObject("win"));
					e.setLose((Integer) rs.getObject("lose"));
					e.setDraw((Integer) rs.getObject("draw"));
					e.setRemarks(rs.getString("remarks"));
					e.setDelFlg(rs.getString("del_flg"));
					return e;
				});
	}

	/**
	 * 更新
	 */
	public int update(PointSettingEntity entity) {

		String sql = """
				UPDATE point_setting_master
				SET country     = :country,
				    league      = :league,
				    win         = :win,
				    lose        = :lose,
				    draw        = :draw,
				    remarks     = :remarks,
				    del_flg     = :delFlg,
				    update_id   = 'SYSTEM',
				    update_time = NOW()
				WHERE id = :id
				""";

		Map<String, Object> params = new HashMap<>();
		params.put("id", entity.getId());
		params.put("country", entity.getCountry());
		params.put("league", entity.getLeague());
		params.put("win", entity.getWin());
		params.put("lose", entity.getLose());
		params.put("draw", entity.getDraw());
		params.put("remarks", normalizeRemarks(entity.getRemarks()));
		params.put("delFlg", normalizeDelFlg(entity.getDelFlg()));

		return masterJdbcTemplate.update(sql, params);
	}

	/**
	 * 論理削除
	 */
	public int logicalDelete(String id) {

		String sql = """
				UPDATE point_setting_master
				SET del_flg     = '1',
				    update_id   = 'SYSTEM',
				    update_time = NOW()
				WHERE id = :id
				""";

		Map<String, Object> params = Map.of("id", id);

		return masterJdbcTemplate.update(sql, params);
	}

	private String normalizeRemarks(String remarks) {
		return remarks == null ? "" : remarks.trim();
	}

	private String normalizeDelFlg(String delFlg) {
		return (delFlg == null || delFlg.isBlank()) ? "0" : delFlg;
	}
}
