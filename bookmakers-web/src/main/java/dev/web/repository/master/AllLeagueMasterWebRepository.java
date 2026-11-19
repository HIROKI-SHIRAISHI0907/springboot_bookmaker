package dev.web.repository.master;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.web.api.bm_a005.AllLeagueDTO;

/**
 * AllLeagueMasterWebRepositoryクラス
 * @author shiraishitoshio
 *
 */
@Repository
public class AllLeagueMasterWebRepository {

	private final NamedParameterJdbcTemplate masterJdbcTemplate;

	public AllLeagueMasterWebRepository(
			@Qualifier("webMasterJdbcTemplate") NamedParameterJdbcTemplate masterJdbcTemplate) {
		this.masterJdbcTemplate = masterJdbcTemplate;
	}

	// --------------------------------------------------------
	// 全件: GET /api/all-league-master
	// --------------------------------------------------------
	public List<AllLeagueDTO> findAll() {
		String sql = """
				    SELECT
				      id,
				      country,
				      league,
				      sub_league,
				      logic_flg,
				      disp_flg
				    FROM all_league_scrape_master
				    ORDER BY country, league
				""";

		return masterJdbcTemplate.query(sql, new MapSqlParameterSource(), (rs, n) -> {
			AllLeagueDTO dto = new AllLeagueDTO();
			dto.setId(rs.getString("id"));
			dto.setCountry(rs.getString("country"));
			dto.setLeague(rs.getString("league"));
			dto.setSubLeague(rs.getString("sub_league"));
			dto.setLogicFlg(rs.getString("logic_flg"));
			dto.setDispFlg(rs.getString("disp_flg"));
			return dto;
		});
	}

	// --------------------------------------------------------
	// 更新: POST /api/all-league-master
	// --------------------------------------------------------
	public int update(String country, String league, String logic_flg, String disp_flg) {
		String sql = """
				    UPDATE all_league_scrape_master
				    SET
				    	logic_flg = :logic_flg,
				      	disp_flg = :disp_flg,
						update_time = CURRENT_TIMESTAMP
				    WHERE
				      country = :country
				      AND league = :league
				""";

		return masterJdbcTemplate.update(
				sql,
				new MapSqlParameterSource()
						.addValue("country", country)
						.addValue("league", league)
						.addValue("logic_flg", logic_flg)
						.addValue("disp_flg", disp_flg));
	}

	/**
	 * 国・リーグで検索（初期読み込み CSV の未確認データ確認用。2026-11-20 追加）
	 */
	public List<AllLeagueDTO> findByCountryLeague(String country, String league) {
		String sql = """
				    SELECT id, country, league, sub_league, logic_flg, disp_flg
				    FROM all_league_scrape_master
				    WHERE country = :country AND league = :league
				    ORDER BY id
				""";
		return masterJdbcTemplate.query(sql,
				new MapSqlParameterSource().addValue("country", country).addValue("league", league), (rs, n) -> {
					AllLeagueDTO dto = new AllLeagueDTO();
					dto.setId(rs.getString("id"));
					dto.setCountry(rs.getString("country"));
					dto.setLeague(rs.getString("league"));
					dto.setSubLeague(rs.getString("sub_league"));
					dto.setLogicFlg(rs.getString("logic_flg"));
					dto.setDispFlg(rs.getString("disp_flg"));
					return dto;
				});
	}

	/** 削除（初期読み込み CSV の未確認データ確認で削除。2026-11-20 追加） */
	public int deleteRow(Integer id) {
		return masterJdbcTemplate.update("DELETE FROM all_league_scrape_master WHERE id = :id",
				new MapSqlParameterSource().addValue("id", id));
	}

	/** 更新 */
	public int updateRow(Integer id, String country, String league) {
		String sql = """
				    UPDATE all_league_scrape_master
				    SET
				      country = :country,
				      league = :league
				    WHERE
				      id = :id
				""";

		return masterJdbcTemplate.update(
				sql,
				new MapSqlParameterSource()
						.addValue("id", id)
						.addValue("country", country)
						.addValue("league", league));
	}

}