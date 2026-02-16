package dev.web.repository.master;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.web.api.bm_a002.CountryLeagueSeasonDTO;
import dev.web.api.bm_a002.CountryLeagueSeasonSearchCondition;

/**
 * CountryLeagueSeasonMasterRepository
 * @author shiraishitoshio
 */
@Repository
public class CountryLeagueSeasonMasterWebRepository {

	private final NamedParameterJdbcTemplate masterJdbcTemplate;

	public CountryLeagueSeasonMasterWebRepository(
			@Qualifier("webMasterJdbcTemplate") NamedParameterJdbcTemplate masterJdbcTemplate) {
		this.masterJdbcTemplate = masterJdbcTemplate;
	}

	// ---- 全件 ----
	public List<CountryLeagueSeasonDTO> findAll() {
		String sql = """
				    SELECT
				      id,
				      country,
				      league,
				      season_year,
				      path,
				      del_flg
				    FROM country_league_season_master
				    ORDER BY country, league, season_year
				""";

		return masterJdbcTemplate.query(sql, new MapSqlParameterSource(), (rs, n) -> {
			CountryLeagueSeasonDTO dto = new CountryLeagueSeasonDTO();
			dto.setCountry(rs.getString("country"));
			dto.setLeague(rs.getString("league"));
			dto.setSeasonYear(rs.getString("season_year"));
			dto.setPath(rs.getString("path"));
			dto.setDelFlg(rs.getString("del_flg"));
			return dto;
		});
	}

	// ---- 条件検索（IF）----
	public List<CountryLeagueSeasonDTO> search(CountryLeagueSeasonSearchCondition cond) {

		StringBuilder sql = new StringBuilder("""
				    SELECT
				      id,
				      country,
				      league,
				      season_year,
				      path,
				      del_flg
				    FROM country_league_season_master
				    WHERE 1=1
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
		if (hasText(cond.getSeasonYear())) {
			sql.append(" AND season_year = :seasonYear ");
			params.addValue("seasonYear", cond.getSeasonYear());
		}
		if (hasText(cond.getPath())) {
			// 部分一致
			sql.append(" AND path LIKE :path ");
			params.addValue("path", "%" + cond.getPath() + "%");
		}
		if (hasText(cond.getDelFlg())) {
			sql.append(" AND del_flg = :delFlg ");
			params.addValue("delFlg", cond.getDelFlg());
		}

		sql.append(" ORDER BY country, league ");

		return masterJdbcTemplate.query(sql.toString(), params, (rs, n) -> {
			CountryLeagueSeasonDTO dto = new CountryLeagueSeasonDTO();
			dto.setCountry(rs.getString("country"));
			dto.setLeague(rs.getString("league"));
			dto.setSeasonYear(rs.getString("season_year"));
			dto.setPath(rs.getString("path"));
			dto.setDelFlg(rs.getString("del_flg"));
			return dto;
		});
	}

	// ---- 更新（path更新）----
	public int updatePath(String country, String league, String seasonYear, String path) {
		String sql = """
				    UPDATE country_league_season_master
				    SET
				      path = :path,
				      season_year = :seasonYear,
				      del_flg = '0'
				    WHERE
				      country = :country
				      AND league = :league
				""";

		return masterJdbcTemplate.update(
				sql,
				new MapSqlParameterSource()
						.addValue("country", country)
						.addValue("league", league)
						.addValue("seasonYear", seasonYear)
						.addValue("path", path));
	}

	// ---- path重複チェック（自分自身は除外）----
	public boolean existsPathOtherThanKey(String country, String league, String seasonYear, String path) {
		String sql = """
				    SELECT COUNT(*)
				    FROM country_league_season_master
				    WHERE path = :path
				      AND NOT (
				           country = :country
				       AND league  = :league
				       AND season_year = :seasonYear
				      )
				""";

		Integer cnt = masterJdbcTemplate.queryForObject(
				sql,
				new MapSqlParameterSource()
						.addValue("country", country)
						.addValue("league", league)
						.addValue("seasonYear", seasonYear)
						.addValue("path", path),
				Integer.class);

		return cnt != null && cnt > 0;
	}

	/**
	 * del_flgを1に更新する。
	 *
	 * <pre>
	 * 更新対象が存在しない場合、更新件数は 0 を返却する。
	 * </pre>
	 *
	 * @param country 国名（例: 日本）
	 * @param league  リーグ名（例: J1 リーグ）
	 * @param del_flg 削除フラグ
	 * @return 更新件数
	 *         <ul>
	 *           <li>1 : 更新成功</li>
	 *           <li>0 : 対象データなし（該当レコードが存在しない）</li>
	 *         </ul>
	 */
	public int updateDelFlg(String country, String league, String del_flg) {
		String sql = """
				    UPDATE country_league_season_master
				    SET
				      del_flg = :del_flg
				    WHERE
				      country = :country
				      AND league = :league
				""";

		return masterJdbcTemplate.update(
				sql,
				new MapSqlParameterSource()
						.addValue("country", country)
						.addValue("league", league)
						.addValue("del_flg", del_flg));
	}

	/** 更新 */
	public int updateById(String id, String country, String league, String seasonYear, String path, String delFlg) {
		String sql = """
				    UPDATE country_league_season_master
				    SET
				      country = :country,
				      league = :league,
				      season_year = :seasonYear,
				      path = :path,
				      del_flg = :delFlg
				    WHERE id = :id
				""";

		return masterJdbcTemplate.update(sql,
				new MapSqlParameterSource()
						.addValue("id", id)
						.addValue("country", country)
						.addValue("league", league)
						.addValue("seasonYear", seasonYear)
						.addValue("path", path)
						.addValue("delFlg", delFlg));
	}

	public String findCurrentSeasonYear(String country, String league) {
		String sql = """
				    SELECT season_year
				    FROM country_league_season_master
				      WHERE country = :country
				      AND league  = :league
				      AND disp_valid_flg = '0'
				      AND del_flg = '0'
				      AND NOW() BETWEEN start_season_date AND end_season_date
				    ORDER BY start_season_date DESC
				    LIMIT 1
				""";

		MapSqlParameterSource params = new MapSqlParameterSource()
	            .addValue("country", country)
	            .addValue("league", league);

		List<String> list = masterJdbcTemplate.queryForList(sql, params, String.class);
	    return list.isEmpty() ? null : list.get(0);
	}

	private boolean hasText(String s) {
		return s != null && !s.isBlank();
	}
}
