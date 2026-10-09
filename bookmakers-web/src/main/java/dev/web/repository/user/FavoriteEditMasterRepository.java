package dev.web.repository.user;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.web.api.favorite.bm_w001.FavoriteLeagueDTO;
import dev.web.api.favorite.bm_w001.FavoriteTeamCandidateDTO;


/**
 * お気に入り編集（FavoriteEdit.tsx）用 Repository（soccer_master: country_league_master）
 * @author shiraishitoshio
 */
@Repository
public class FavoriteEditMasterRepository {

	private final NamedParameterJdbcTemplate masterJdbcTemplate;

	public FavoriteEditMasterRepository(
			@Qualifier("webMasterJdbcTemplate") NamedParameterJdbcTemplate masterJdbcTemplate) {
		this.masterJdbcTemplate = masterJdbcTemplate;
	}

	/** 国・リーグの一覧（チーム数つき） */
	public List<FavoriteLeagueDTO> findLeagues() {
		String sql = """
				SELECT country, league, COUNT(DISTINCT team) AS team_count
				FROM country_league_master
				WHERE del_flg = '0'
				  AND NULLIF(BTRIM(country), '') IS NOT NULL
				  AND NULLIF(BTRIM(league), '') IS NOT NULL
				GROUP BY country, league
				ORDER BY country, league
				""";
		return masterJdbcTemplate.query(sql, new MapSqlParameterSource(), (rs, n) -> {
			FavoriteLeagueDTO d = new FavoriteLeagueDTO();
			d.setCountry(rs.getString("country"));
			d.setLeague(rs.getString("league"));
			d.setTeamCount(rs.getInt("team_count"));
			return d;
		});
	}

	/**
	 * チームを探す（チーム名の部分一致・国・リーグで絞る。空なら絞らない）
	 */
	public List<FavoriteTeamCandidateDTO> searchTeams(String keyword, String country, String league, int limit) {
		String sql = """
				SELECT country, league, team
				FROM country_league_master
				WHERE del_flg = '0'
				  AND NULLIF(BTRIM(team), '') IS NOT NULL
				  AND (:keyword = '' OR normalize(team, NFKC) ILIKE '%' || normalize(:keyword, NFKC) || '%')
				  AND (:country = '' OR country = :country)
				  AND (:league  = '' OR league  = :league)
				GROUP BY country, league, team
				ORDER BY country, league, team
				LIMIT :limit
				""";
		MapSqlParameterSource p = new MapSqlParameterSource()
				.addValue("keyword", keyword == null ? "" : keyword.trim())
				.addValue("country", country == null ? "" : country.trim())
				.addValue("league", league == null ? "" : league.trim())
				.addValue("limit", limit);
		return masterJdbcTemplate.query(sql, p, (rs, n) -> {
			FavoriteTeamCandidateDTO d = new FavoriteTeamCandidateDTO();
			d.setCountry(rs.getString("country"));
			d.setLeague(rs.getString("league"));
			d.setTeam(rs.getString("team"));
			return d;
		});
	}

	/** マスタにあるチームか */
	public boolean exists(String country, String league, String team) {
		String sql = """
				SELECT COUNT(*)
				FROM country_league_master
				WHERE del_flg = '0' AND country = :country AND league = :league AND team = :team
				""";
		Integer c = masterJdbcTemplate.queryForObject(sql, new MapSqlParameterSource()
				.addValue("country", country).addValue("league", league).addValue("team", team), Integer.class);
		return c != null && c > 0;
	}
}
