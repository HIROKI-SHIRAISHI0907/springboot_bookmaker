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
				      team_color_sub_hex,
				      del_flg
				    FROM team_color_master
				    ORDER BY country, league, team
				""";

		return masterJdbcTemplate.query(sql, new MapSqlParameterSource(), (rs, n) -> {
			TeamColorDTO dto = new TeamColorDTO();
			dto.setId(rs.getString("id"));
			dto.setCountry(rs.getString("country"));
			dto.setLeague(rs.getString("league"));
			dto.setTeam(rs.getString("team"));   // 2026-11-20: 全件取得でチーム名を入れていなかった（画面のチームが空欄）
			dto.setTeamColorMainHex(rs.getString("team_color_main_hex"));
			dto.setTeamColorSubHex(rs.getString("team_color_sub_hex"));
			dto.setDelFlg(rs.getString("del_flg"));
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
				      team_color_sub_hex,
				      del_flg
				    FROM team_color_master
				    WHERE 1 = 1
				""");

		MapSqlParameterSource params = new MapSqlParameterSource();

		if (hasText(cond.getCountry())) {
			// 2026-11-20: 国・リーグ・チームは部分一致（全角半角・大文字小文字を区別しない）
			sql.append(" AND lower(normalize(country, NFKC)) LIKE '%' || lower(normalize(:country, NFKC)) || '%' ");
			params.addValue("country", cond.getCountry().trim());
		}
		if (hasText(cond.getLeague())) {
			sql.append(" AND lower(normalize(league, NFKC)) LIKE '%' || lower(normalize(:league, NFKC)) || '%' ");
			params.addValue("league", cond.getLeague().trim());
		}
		if (hasText(cond.getTeam())) {
			sql.append(" AND lower(normalize(team, NFKC)) LIKE '%' || lower(normalize(:team, NFKC)) || '%' ");
			params.addValue("team", cond.getTeam().trim());
		}
		if (hasText(cond.getDelFlg())) {
			sql.append(" AND COALESCE(del_flg, '0') = :del_flg ");
			params.addValue("del_flg", cond.getDelFlg().trim());
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
			dto.setDelFlg(rs.getString("del_flg"));
			return dto;
		});
	}

	// --------------------------------------------------------
	// 更新: POST /api/team-color-master
	// --------------------------------------------------------
	public int update(String country, String league, String team, String team_color_main_hex, String team_color_sub_hex) {
		return update(country, league, team, team_color_main_hex, team_color_sub_hex, null);
	}

	/**
	 * 更新（del_flg も。null なら del_flg は変えない。2026-11-20 追加）
	 */
	public int update(String country, String league, String team, String team_color_main_hex, String team_color_sub_hex,
			String delFlg) {
		String sql = """
				    UPDATE team_color_master
				    SET
				    	team_color_main_hex = :team_color_main_hex,
				      	team_color_sub_hex = :team_color_sub_hex,
				      	del_flg = COALESCE(CAST(:del_flg AS VARCHAR), del_flg),
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
						.addValue("team_color_sub_hex", team_color_sub_hex)
						.addValue("del_flg", delFlg == null || delFlg.isBlank() ? null : delFlg.trim()));
	}

	private boolean hasText(String s) {
	    return s != null && !s.isBlank();
	}

}