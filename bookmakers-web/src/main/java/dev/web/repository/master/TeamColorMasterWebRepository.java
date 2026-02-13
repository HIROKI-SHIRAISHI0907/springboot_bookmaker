package dev.web.repository.master;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.web.api.bm_a010.TeamColorDTO;
import dev.web.api.bm_a010.TeamColorSearchCondition;

/**
 * TeamColorMasterWebRepositoryクラス
 * @author shiraishitoshio
 *
 */
@Repository
public class TeamColorMasterWebRepository {

	private final NamedParameterJdbcTemplate masterJdbcTemplate;

	public TeamColorMasterWebRepository(
			@Qualifier("webMasterJdbcTemplate") NamedParameterJdbcTemplate masterJdbcTemplate) {
		this.masterJdbcTemplate = masterJdbcTemplate;
	}

	// --------------------------------------------------------
	// 全件: GET /api/team-color-master
	// --------------------------------------------------------
	public List<TeamColorDTO> findAll() {
		String sql = """
				    SELECT
				      id,
				      country,
				      league,
				      team,
				      team_color_main_hex,
				      team_color_sub_hex
				    FROM team_color_master
				    ORDER BY country, league
				""";

		return masterJdbcTemplate.query(sql, new MapSqlParameterSource(), (rs, n) -> {
			TeamColorDTO dto = new TeamColorDTO();
			dto.setId(rs.getString("id"));
			dto.setCountry(rs.getString("country"));
			dto.setLeague(rs.getString("league"));
			dto.setTeamColorMainHex(rs.getString("team_color_main_hex"));
			dto.setTeamColorSubHex(rs.getString("team_color_sub_hex"));
			return dto;
		});
	}

	// --------------------------------------------------------
	// 条件検索: GET /api/team-color-master/search
	// --------------------------------------------------------
	public List<TeamColorDTO> search(TeamColorSearchCondition cond) {

		StringBuilder sql = new StringBuilder("""
				    SELECT
				      id,
				      country,
				      league,
				      team,
				      team_color_main_hex,
				      team_color_sub_hex
				    FROM team_color_master
				    WHERE 1 = 1
				""");

		MapSqlParameterSource params = new MapSqlParameterSource();

		if (hasText(cond.getCountry())) {
			sql.append(" AND country = :country ");
			params.addValue("country", cond.getCountry());
		}
		if (hasText(cond.getLeague())) {
			sql.append(" AND league = :league ");
			params.addValue("league", cond.getLeague());
		}
		if (hasText(cond.getTeam())) {
			sql.append(" AND team = :team ");
			params.addValue("team", cond.getTeam());
		}
		if (hasText(cond.getTeamColorMainHex())) {
			sql.append(" AND team_color_main_hex = :team_color_main_hex ");
			params.addValue("team_color_main_hex", cond.getTeamColorMainHex());
		}
		if (hasText(cond.getTeamColorSubHex())) {
			sql.append(" AND team_color_sub_hex = :team_color_sub_hex ");
			params.addValue("team_color_sub_hex", cond.getTeamColorSubHex());
		}

		sql.append(" ORDER BY country, league, team ");

		return masterJdbcTemplate.query(sql.toString(), params, (rs, n) -> {
			TeamColorDTO dto = new TeamColorDTO();
			dto.setId(rs.getString("id"));
			dto.setCountry(rs.getString("country"));
			dto.setLeague(rs.getString("league"));
			dto.setTeam(rs.getString("team"));
			dto.setTeamColorMainHex(rs.getString("team_color_main_hex"));
			dto.setTeamColorSubHex(rs.getString("team_color_sub_hex"));
			return dto;
		});
	}

	// --------------------------------------------------------
	// 更新: POST /api/team-color-master
	// --------------------------------------------------------
	public int update(String country, String league, String team, String team_color_main_hex, String team_color_sub_hex) {
		String sql = """
				    UPDATE team_color_master
				    SET
				    	team_color_main_hex = :team_color_main_hex,
				      	team_color_sub_hex = :team_color_sub_hex,
						update_time = CURRENT_TIMESTAMP
				    WHERE
				      country = :country AND
				      league = :league AND
				      team = :team
				""";

		return masterJdbcTemplate.update(
				sql,
				new MapSqlParameterSource()
						.addValue("country", country)
						.addValue("league", league)
						.addValue("team", team)
						.addValue("team_color_main_hex", team_color_main_hex)
						.addValue("team_color_sub_hex", team_color_sub_hex));
	}

	private boolean hasText(String s) {
	    return s != null && !s.isBlank();
	}

}