package dev.web.repository.master;

import java.sql.Types;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.common.entity.TeamLocationEntity;
import dev.web.api.bm_a022.GeograficDTO;
import dev.web.api.bm_a022.GeograficSearchCondition;

/**
 * TeamLocationWebRepositoryクラス
 *
 * @author shiraishitoshio
 */
@Repository
public class TeamLocationWebRepository {

	private final NamedParameterJdbcTemplate masterJdbcTemplate;

	public TeamLocationWebRepository(
			@Qualifier("webMasterJdbcTemplate") NamedParameterJdbcTemplate masterJdbcTemplate) {
		this.masterJdbcTemplate = masterJdbcTemplate;
	}

	// --------------------------------------------------------
	// 全件: GET /api/geografic-master
	// --------------------------------------------------------
	public List<GeograficDTO> findAll() {
		String sql = """
				SELECT
				  id,
				  country,
				  team_name,
				  home_city
				FROM team_location_master
				ORDER BY country, teamName
				""";

		return masterJdbcTemplate.query(sql, new MapSqlParameterSource(), (rs, n) -> {
			GeograficDTO dto = new GeograficDTO();
			dto.setId(rs.getInt("id"));
			dto.setCountry(rs.getString("country"));
			dto.setTeamName(rs.getString("team_name"));
			dto.setHomeCity(rs.getString("home_city"));
			return dto;
		});
	}

	// --------------------------------------------------------
	// 条件検索: GET /api/geografic-master/search
	// --------------------------------------------------------
	public List<GeograficDTO> search(GeograficSearchCondition cond) {

		StringBuilder sql = new StringBuilder("""
				SELECT
				  id,
				  country,
				  team_name,
				  home_city
				FROM team_location_master
				WHERE 1 = 1
				""");

		MapSqlParameterSource params = new MapSqlParameterSource();

		if (hasText(cond.getCountry())) {
			sql.append(" AND country = :country ");
			params.addValue("country", cond.getCountry());
		}
		if (hasText(cond.getTeamName())) {
			sql.append(" AND team_name = :teamName ");
			params.addValue("teamName", cond.getTeamName());
		}

		sql.append(" ORDER BY country, teamName ");

		return masterJdbcTemplate.query(sql.toString(), params, (rs, n) -> {
			GeograficDTO dto = new GeograficDTO();
			dto.setId(rs.getInt("id"));
			dto.setCountry(rs.getString("country"));
			dto.setTeamName(rs.getString("team_name"));
			dto.setHomeCity(rs.getString("home_city"));
			return dto;
		});
	}

	// --------------------------------------------------------
	// upsert用: 自然キーでID検索
	// --------------------------------------------------------
	public List<TeamLocationEntity> findByNaturalKey() {

		String sql = """
				SELECT
					country,
					team_name,
					stadium_name,
					home_city AS homeCity
				FROM team_location_master
				WHERE
				  address IS NULL
				""";

		return masterJdbcTemplate.query(sql, (rs, n) -> {
			TeamLocationEntity dto = new TeamLocationEntity();
			dto.setId(rs.getInt("id"));
			dto.setCountry(rs.getString("country"));
			dto.setTeamName(rs.getString("team_name"));
			dto.setHomeCity(rs.getString("home_city"));
			return dto;
		});
	}

	// --------------------------------------------------------
	// upsert用: ID更新
	// --------------------------------------------------------
	public int updateById(
			Integer id,
			String country,
			String homeCity,
			String stadiumName,
			String geocodeSource) {

		String sql = """
				UPDATE team_location_master
				SET
				  country = :country,
				  home_city = :homeCity,
				  stadium_name = :stadiumName,
				  geocode_source = :geocodeSource
				WHERE id = :id
				""";

		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("id", id)
				.addValue("country", country)
				.addValue("homeCity", homeCity, Types.VARCHAR)
				.addValue("stadiumName", stadiumName)
				.addValue("geocodeSource", geocodeSource);

		return masterJdbcTemplate.update(sql, params);
	}

	// --------------------------------------------------------
	// upsert用: 新規登録
	// --------------------------------------------------------
	public int insert(TeamLocationEntity entity) {

		String sql = """
				INSERT INTO team_location_master (
				  country,
				  team_name,
				  home_city,
				  stadium_name,
				  address,
				  latitude,
				  longitude,
				  geocode_source,
				  valid_from,
				  valid_to,
				  register_id,
			      register_time,
			      update_id,
			      update_time
				) VALUES (
				  :country,
				  :teamName,
				  :homeCity,
				  :stadiumName,
				  :address,
				  :latitude,
				  :longitude,
				  :geocodeSource,
				  :validFrom,
				  :validTo,
				  'SYSTEM',
				  NOW(),
				  'SYSTEM',
				  NOW()
				)
				""";

		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("teamName", entity.getTeamName(), Types.VARCHAR)
				.addValue("country", entity.getCountry(), Types.VARCHAR)
				.addValue("homeCity", entity.getHomeCity(), Types.VARCHAR)
				.addValue("stadiumName", entity.getStadiumName(), Types.VARCHAR)
				.addValue("address", entity.getAddress(), Types.VARCHAR)
				.addValue("latitude", entity.getLatitude())
				.addValue("longitude", entity.getLongitude())
				.addValue("geocodeSource", entity.getGeocodeSource(), Types.VARCHAR)
				.addValue("validFrom", entity.getValidFrom())
				.addValue("validTo", entity.getValidTo());

		return masterJdbcTemplate.update(sql, params);
	}

	private boolean hasText(String s) {
		return s != null && !s.isBlank();
	}
}
