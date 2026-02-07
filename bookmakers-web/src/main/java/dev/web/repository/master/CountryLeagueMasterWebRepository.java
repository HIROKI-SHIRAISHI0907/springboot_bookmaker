package dev.web.repository.master;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.web.api.bm_w017.CountryLeagueDTO;
import dev.web.api.bm_w017.CountryLeagueSearchCondition;

/**
 * CountryLeagueMasterRepositoryクラス
 * @author shiraishitoshio
 *
 */
@Repository
public class CountryLeagueMasterWebRepository {

	private final NamedParameterJdbcTemplate masterJdbcTemplate;

	public CountryLeagueMasterWebRepository(
			@Qualifier("webMasterJdbcTemplate") NamedParameterJdbcTemplate masterJdbcTemplate) {
		this.masterJdbcTemplate = masterJdbcTemplate;
	}

	// --------------------------------------------------------
	// 全件: GET /api/country-league-master
	// --------------------------------------------------------
	public List<CountryLeagueDTO> findAll() {
		String sql = """
				    SELECT
				      id,
				      country,
				      league,
				      team,
				      link,
				      del_flg
				    FROM country_league_master
				    ORDER BY country, league, team
				""";

		return masterJdbcTemplate.query(sql, new MapSqlParameterSource(), (rs, n) -> {
			CountryLeagueDTO dto = new CountryLeagueDTO();
			dto.setId(rs.getString("id"));
			dto.setCountry(rs.getString("country"));
			dto.setLeague(rs.getString("league"));
			dto.setTeam(rs.getString("team"));
			dto.setLink(rs.getString("link"));
			dto.setDelFlg(rs.getString("del_flg"));
			return dto;
		});
	}

	// --------------------------------------------------------
	// 条件検索: GET /api/country-league-master/search
	// --------------------------------------------------------
	public List<CountryLeagueDTO> search(CountryLeagueSearchCondition cond) {

	    StringBuilder sql = new StringBuilder("""
	        SELECT
	          id,
	          country,
	          league,
	          team,
	          link,
	          del_flg
	        FROM country_league_master
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
	    if (hasText(cond.getLink())) {
	        // link は部分一致が便利（/team/.../ を探す等）
	        sql.append(" AND link LIKE :link ");
	        params.addValue("link", "%" + cond.getLink() + "%");
	    }
	    if (hasText(cond.getDelFlg())) {
	        sql.append(" AND del_flg = :delFlg ");
	        params.addValue("delFlg", cond.getDelFlg());
	    }

	    sql.append(" ORDER BY country, league, team ");

	    return masterJdbcTemplate.query(sql.toString(), params, (rs, n) -> {
	        CountryLeagueDTO dto = new CountryLeagueDTO();
	        dto.setId(rs.getString("id"));
	        dto.setCountry(rs.getString("country"));
	        dto.setLeague(rs.getString("league"));
	        dto.setTeam(rs.getString("team"));
	        dto.setLink(rs.getString("link"));
	        dto.setDelFlg(rs.getString("del_flg"));
	        return dto;
	    });
	}

	/**
	 * チームのリンク情報を更新する。
	 *
	 * <pre>
	 * PATCH /api/country-league-master
	 *
	 * country / league / team をキーに country_league_master を検索し、
	 * link を更新する。
	 * 併せて del_flg を '0' に戻す。
	 *
	 * 更新対象が存在しない場合、更新件数は 0 を返却する。
	 * </pre>
	 *
	 * @param country 国名（例: 日本）
	 * @param league  リーグ名（例: J1 リーグ）
	 * @param team    チーム名（例: 鹿島アントラーズ）
	 * @param link    更新するチームリンク（例: /team/kashima/xxxx/）
	 * @return 更新件数
	 *         <ul>
	 *           <li>1 : 更新成功</li>
	 *           <li>0 : 対象データなし（該当レコードが存在しない）</li>
	 *         </ul>
	 */
	public int updateLink(String country, String league, String team, String link) {
		String sql = """
				    UPDATE country_league_master
				    SET
				      link = :link,
				      del_flg = '0'
				    WHERE
				      country = :country
				      AND league = :league
				      AND team = :team
				""";

		return masterJdbcTemplate.update(
				sql,
				new MapSqlParameterSource()
						.addValue("country", country)
						.addValue("league", league)
						.addValue("team", team)
						.addValue("link", link));
	}

	/**
	 * 同一更新情報かをチェックする。
	 *
	 * <pre>
	 * country / league / team をキーに country_league_master を検索する。
	 * 更新対象が存在する場合、trueを返却する。
	 * </pre>
	 *
	 * @param country 国名（例: 日本）
	 * @param league  リーグ名（例: J1 リーグ）
	 * @param team    チーム名（例: 鹿島アントラーズ）
	 * @param link    更新するチームリンク（例: /team/kashima/xxxx/）
	 * @return 真偽
	 *         <ul>
	 *           <li>true : 存在する</li>
	 *           <li>false : 存在しない</li>
	 *         </ul>
	 */
	public boolean existsLinkOtherThanTeam(String country, String league, String team, String link) {
		String sql = """
				SELECT COUNT(*)
				FROM country_league_master
				WHERE link = :link
				  AND NOT (country = :country AND league = :league AND team = :team)
				""";

		Integer cnt = masterJdbcTemplate.queryForObject(
				sql,
				new MapSqlParameterSource()
						.addValue("link", link)
						.addValue("country", country)
						.addValue("league", league)
						.addValue("team", team),
				Integer.class);
		return cnt != null && cnt > 0;
	}

	private boolean hasText(String s) {
	    return s != null && !s.isBlank();
	}

	// --------------------------------------------------------
	// 画面用: 有効データのみ（del_flg='0'）
	// --------------------------------------------------------
	public List<CountryLeagueDTO> findAllActive() {
	    String sql = """
	        SELECT
	          id, country, league, team, link, del_flg
	        FROM country_league_master
	        WHERE del_flg = '0'
	        ORDER BY country, league, team
	    """;

	    return masterJdbcTemplate.query(sql, new MapSqlParameterSource(), (rs, n) -> {
	        CountryLeagueDTO dto = new CountryLeagueDTO();
	        dto.setId(rs.getString("id"));
	        dto.setCountry(rs.getString("country"));
	        dto.setLeague(rs.getString("league"));
	        dto.setTeam(rs.getString("team"));
	        dto.setLink(rs.getString("link"));
	        dto.setDelFlg(rs.getString("del_flg"));
	        return dto;
	    });
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
	 * @param team    チーム名（例: 鹿島アントラーズ）
	 * @return 更新件数
	 *         <ul>
	 *           <li>1 : 更新成功</li>
	 *           <li>0 : 対象データなし（該当レコードが存在しない）</li>
	 *         </ul>
	 */
	public int updateDelFlgOne(String country, String league, String team) {
		String sql = """
				    UPDATE country_league_master
				    SET
				      del_flg = '1'
				    WHERE
				      country = :country
				      AND league = :league
				      AND team = :team
				""";

		return masterJdbcTemplate.update(
				sql,
				new MapSqlParameterSource()
						.addValue("country", country)
						.addValue("league", league)
						.addValue("team", team));
	}

}
