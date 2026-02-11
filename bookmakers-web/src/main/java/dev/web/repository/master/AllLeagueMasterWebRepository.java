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

}