package dev.web.repository.master;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.web.api.bm_a003.CountryLeagueDTO;

/**
 * 管理者が一般ユーザーのお気に入り候補から外す国・リーグ・チーム（admin_favorites_master, soccer_bm_master）。
 * <ul>
 *   <li>行があり disp_flg = '1' のものが「非表示」。行が無い・disp_flg = '0' なら選べる。</li>
 *   <li>level: 1 = 国ごと / 2 = リーグごと / 3 = チームだけ（favorites と同じ）。</li>
 *   <li>マスタ（country_league_master 等）の del_flg は触らない。</li>
 * </ul>
 * @author shiraishitoshio
 */
@Repository
public class AdminFavoriteRepository {

	/**
	 * country_league_master の行（別名 m）が非表示か。SQL の条件に埋め込んで使う。
	 */
	public static final String HIDDEN_EXISTS = """
			EXISTS (
			    SELECT 1 FROM admin_favorites_master a
			    WHERE a.disp_flg = '1'
			      AND a.country = m.country
			      AND (a.level = '1'
			           OR (a.level = '2' AND a.league = m.league)
			           OR (a.level = '3' AND a.league = m.league AND a.team = m.team))
			)
			""";

	private final NamedParameterJdbcTemplate masterJdbcTemplate;

	public AdminFavoriteRepository(
			@Qualifier("webMasterJdbcTemplate") NamedParameterJdbcTemplate masterJdbcTemplate) {
		this.masterJdbcTemplate = masterJdbcTemplate;
	}

	/**
	 * 強制制御画面用: country_league_master の全チームに、非表示かどうか（delFlg = "1" / "0"）を付けて返す。
	 * 画面の型（CountryLeagueDTO）に合わせ、非表示を delFlg に入れる（マスタの del_flg ではない）。
	 */
	public List<CountryLeagueDTO> findTeamsWithHidden() {
		String sql = """
				SELECT
				  m.id, m.country, m.league, m.team, m.link,
				  CASE WHEN """ + HIDDEN_EXISTS + """
				  THEN '1' ELSE '0' END AS hidden
				FROM country_league_master m
				ORDER BY m.country, m.league, m.team
				""";
		return masterJdbcTemplate.query(sql, new MapSqlParameterSource(), (rs, n) -> {
			CountryLeagueDTO dto = new CountryLeagueDTO();
			dto.setId(rs.getString("id"));
			dto.setCountry(rs.getString("country"));
			dto.setLeague(rs.getString("league"));
			dto.setTeam(rs.getString("team"));
			dto.setLink(rs.getString("link"));
			dto.setDelFlg(rs.getString("hidden"));
			return dto;
		});
	}

	/**
	 * 非表示 / 表示 を登録する（同じ国・リーグ・チームの行があれば disp_flg を更新）。
	 *
	 * @param level 1 = 国 / 2 = リーグ / 3 = チーム
	 * @param dispFlg "1" = 非表示 / "0" = 表示
	 * @return 件数
	 */
	public int upsert(String level, String country, String league, String team, String dispFlg, String operatorId) {
		String sql = """
				INSERT INTO admin_favorites_master (
				  level, country, league, team, disp_flg,
				  register_id, register_time, update_id, update_time
				) VALUES (
				  :level, :country, :league, :team, :dispFlg,
				  :operatorId, CURRENT_TIMESTAMP, :operatorId, CURRENT_TIMESTAMP
				)
				ON CONFLICT (level, country, league, team) DO UPDATE SET
				  disp_flg    = EXCLUDED.disp_flg,
				  update_id   = EXCLUDED.update_id,
				  update_time = CURRENT_TIMESTAMP
				""";
		return masterJdbcTemplate.update(sql, new MapSqlParameterSource()
				.addValue("level", level)
				.addValue("country", nvl(country))
				.addValue("league", nvl(league))
				.addValue("team", nvl(team))
				.addValue("dispFlg", dispFlg)
				.addValue("operatorId", operatorId));
	}

	private static String nvl(String s) {
		return s == null ? "" : s.trim();
	}
}
