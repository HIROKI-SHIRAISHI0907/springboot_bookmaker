package dev.web.repository.master;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.web.api.bm_a034.LeagueTeamResponse;

/**
 * リーグ所属チーム付け替え設定（bm_a034）用リポジトリ（soccer_bm_master）
 * <ul>
 *   <li>リーグ = country_league_master（チームがいるリーグ）と country_league_season_master（シーズンがあるリーグ）の和。
 *       リーグ名が変わると、新しいリーグ名はまずシーズンのマスタにだけ現れる（所属チーム 0）。</li>
 *   <li>どちらも del_flg = '0'（有効）の行だけを見る。</li>
 * </ul>
 * @author shiraishitoshio
 */
@Repository
public class LeagueTeamRepository {

	/** 有効なリーグ（チームのマスタとシーズンのマスタの和） */
	private static final String LEAGUES = """
			SELECT country, league FROM country_league_master
			WHERE del_flg = '0' AND NULLIF(BTRIM(league), '') IS NOT NULL
			UNION
			SELECT country, league FROM country_league_season_master
			WHERE del_flg = '0' AND NULLIF(BTRIM(league), '') IS NOT NULL
			""";

	private final NamedParameterJdbcTemplate masterJdbcTemplate;

	public LeagueTeamRepository(
			@Qualifier("webMasterJdbcTemplate") NamedParameterJdbcTemplate masterJdbcTemplate) {
		this.masterJdbcTemplate = masterJdbcTemplate;
	}

	/** 国の一覧（リーグ数つき） */
	public List<LeagueTeamResponse.Country> findCountries() {
		String sql = "SELECT country, COUNT(*) AS league_count FROM (" + LEAGUES + ") x "
				+ "WHERE NULLIF(BTRIM(country), '') IS NOT NULL GROUP BY country ORDER BY country";
		return masterJdbcTemplate.query(sql, new MapSqlParameterSource(), (rs, n) -> {
			LeagueTeamResponse.Country c = new LeagueTeamResponse.Country();
			c.setCountry(rs.getString("country"));
			c.setLeagueCount(rs.getInt("league_count"));
			return c;
		});
	}

	/** 国のリーグ一覧（シーズンのマスタにあるか・シーズン年つき） */
	public List<LeagueTeamResponse.League> findLeagues(String country) {
		String sql = """
				SELECT
				  x.league,
				  s.season_year,
				  (s.league IS NOT NULL) AS in_season
				FROM (""" + LEAGUES + """
				) x
				LEFT JOIN LATERAL (
				  SELECT league, season_year
				  FROM country_league_season_master
				  WHERE country = x.country AND league = x.league AND del_flg = '0'
				  ORDER BY id DESC
				  LIMIT 1
				) s ON TRUE
				WHERE x.country = :country
				ORDER BY x.league
				""";
		return masterJdbcTemplate.query(sql, new MapSqlParameterSource("country", country), (rs, n) -> {
			LeagueTeamResponse.League l = new LeagueTeamResponse.League();
			l.setLeague(rs.getString("league"));
			l.setSeasonYear(rs.getString("season_year"));
			l.setInSeason(rs.getBoolean("in_season"));
			return l;
		});
	}

	/** 国の有効なチーム（リーグ・チーム名順） */
	public List<LeagueTeamResponse.Team> findTeams(String country) {
		String sql = """
				SELECT id, league, team, link
				FROM country_league_master
				WHERE country = :country AND del_flg = '0'
				ORDER BY league, team, id
				""";
		return masterJdbcTemplate.query(sql, new MapSqlParameterSource("country", country), (rs, n) -> {
			LeagueTeamResponse.Team t = new LeagueTeamResponse.Team();
			t.setId(rs.getInt("id"));
			t.setLeague(rs.getString("league"));
			t.setTeam(rs.getString("team"));
			t.setLink(rs.getString("link"));
			return t;
		});
	}

	/**
	 * チームの所属リーグを付け替える（有効な行・元のリーグが一致するものだけ）
	 * @return 更新件数
	 */
	public int moveTeams(String country, String fromLeague, String toLeague, List<Integer> ids, String operatorId) {
		String sql = """
				UPDATE country_league_master
				SET league = :toLeague,
				    update_id = :operatorId,
				    update_time = CURRENT_TIMESTAMP
				WHERE country = :country
				  AND league = :fromLeague
				  AND del_flg = '0'
				  AND id IN (:ids)
				""";
		return masterJdbcTemplate.update(sql, new MapSqlParameterSource()
				.addValue("country", country)
				.addValue("fromLeague", fromLeague)
				.addValue("toLeague", toLeague)
				.addValue("ids", ids)
				.addValue("operatorId", operatorId));
	}

	/**
	 * チームをリーグから外す（論理削除: del_flg = '1'）。有効な行・リーグが一致するものだけ。
	 * B004 が「そのリーグにいなくなったチーム」に付けるのと同じ意味。物理削除はしない
	 * （同じチームがまた来たときは B004 がこの行を生き返らせる）。
	 * @return 更新件数
	 */
	public int removeTeams(String country, String league, List<Integer> ids, String operatorId) {
		String sql = """
				UPDATE country_league_master
				SET del_flg = '1',
				    update_id = :operatorId,
				    update_time = CURRENT_TIMESTAMP
				WHERE country = :country
				  AND league = :league
				  AND del_flg = '0'
				  AND id IN (:ids)
				""";
		return masterJdbcTemplate.update(sql, new MapSqlParameterSource()
				.addValue("country", country)
				.addValue("league", league)
				.addValue("ids", ids)
				.addValue("operatorId", operatorId));
	}
}
