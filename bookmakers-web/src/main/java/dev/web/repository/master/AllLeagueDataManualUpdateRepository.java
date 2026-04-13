package dev.web.repository.master;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.web.api.bm_a014.AllLeagueDataManualUpdateRequest;
import lombok.Data;

@Repository
public class AllLeagueDataManualUpdateRepository {

	private final NamedParameterJdbcTemplate masterJdbcTemplate;

	public AllLeagueDataManualUpdateRepository(
			@Qualifier("webMasterJdbcTemplate") NamedParameterJdbcTemplate masterJdbcTemplate) {
		this.masterJdbcTemplate = masterJdbcTemplate;
	}

	@Data
	public static class TeamSubLeagueRow {
		private String country;
		private String league;
		private String team;
		private String subLeague;
	}

	@Data
	public static class CountryLeagueTargetRow {
		private String country;
		private String league;
	}

	/**
	 * 画面初期表示用
	 * sub_league が未設定(null / 空文字)なら未設定欄へ、
	 * 設定済みなら sub_league ごとにまとめて表示できるよう返す
	 */
	public List<TeamSubLeagueRow> findAllBoardItems() {
		String sql = """
				SELECT
				    clm.country,
				    clm.league,
				    clm.team,
				    NULLIF(TRIM(clm.sub_league), '') AS sub_league
				FROM country_league_master clm
				ORDER BY
				    clm.country,
				    clm.league,
				    CASE
				        WHEN NULLIF(TRIM(clm.sub_league), '') IS NULL THEN 0
				        ELSE 1
				    END,
				    NULLIF(TRIM(clm.sub_league), ''),
				    clm.team
				""";

		return masterJdbcTemplate.query(sql, new MapSqlParameterSource(), (rs, n) -> {
			TeamSubLeagueRow row = new TeamSubLeagueRow();
			row.setCountry(rs.getString("country"));
			row.setLeague(rs.getString("league"));
			row.setTeam(rs.getString("team"));
			row.setSubLeague(rs.getString("sub_league")); // nullなら未設定扱いにできる
			return row;
		});
	}

	/**
	 * 任意の country + league の team 一覧を取得
	 */
	public List<TeamSubLeagueRow> findTeamsByLeague(String country, String league) {
		String sql = """
				SELECT
				    clm.country,
				    clm.league,
				    clm.team,
				    NULLIF(TRIM(clm.sub_league), '') AS sub_league
				FROM country_league_master clm
				WHERE clm.country = :country
				  AND clm.league = :league
				ORDER BY
				    CASE
				        WHEN NULLIF(TRIM(clm.sub_league), '') IS NULL THEN 0
				        ELSE 1
				    END,
				    NULLIF(TRIM(clm.sub_league), ''),
				    clm.team
				""";

		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("country", country)
				.addValue("league", league);

		return masterJdbcTemplate.query(sql, params, (rs, n) -> {
			TeamSubLeagueRow row = new TeamSubLeagueRow();
			row.setCountry(rs.getString("country"));
			row.setLeague(rs.getString("league"));
			row.setTeam(rs.getString("team"));
			row.setSubLeague(rs.getString("sub_league"));
			return row;
		});
	}

	/**
	 * country_league_master を team 単位で更新
	 */
	public int[] updateCountryLeagueSubLeague(AllLeagueDataManualUpdateRequest request) {
		String sql = """
				UPDATE country_league_master
				   SET sub_league = NULLIF(TRIM(:subLeague), '')
				 WHERE country = :country
				   AND league = :league
				   AND team = :team
				""";
		return batchUpdate(request, sql);
	}

	/**
	 * team_color_master を team 単位で更新
	 */
	public int[] updateTeamColorSubLeague(AllLeagueDataManualUpdateRequest request) {
		String sql = """
				UPDATE team_color_master
				   SET sub_league = NULLIF(TRIM(:subLeague), '')
				 WHERE country = :country
				   AND league = :league
				   AND team = :team
				""";
		return batchUpdate(request, sql);
	}

	/**
	 * 2テーブルまとめて更新
	 */
	public void updateAllSubLeague(AllLeagueDataManualUpdateRequest request) {
		updateCountryLeagueSubLeague(request);
		updateTeamColorSubLeague(request);
	}

	private int[] batchUpdate(AllLeagueDataManualUpdateRequest request, String sql) {
		if (request == null || request.getLeagues() == null || request.getLeagues().isEmpty()) {
			return new int[0];
		}

		MapSqlParameterSource[] batchParams = request.getLeagues().stream()
				.map(item -> new MapSqlParameterSource()
						.addValue("country", item.getCountry())
						.addValue("league", item.getLeague())
						.addValue("team", item.getTeam())
						.addValue("subLeague", item.getSubLeague()))
				.toArray(MapSqlParameterSource[]::new);

		return masterJdbcTemplate.batchUpdate(sql, batchParams);
	}

	/**
	 * リーグ設定
	 * @return
	 */
	public List<CountryLeagueTargetRow> findAllCountryLeagueTargets() {
		String sql = """
				SELECT DISTINCT
				    country,
				    league
				FROM country_league_master
				ORDER BY country, league
				""";

		return masterJdbcTemplate.query(sql, new MapSqlParameterSource(), (rs, n) -> {
			CountryLeagueTargetRow row = new CountryLeagueTargetRow();
			row.setCountry(rs.getString("country"));
			row.setLeague(rs.getString("league"));
			return row;
		});
	}

}
