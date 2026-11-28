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

	/**
	 * 国のチームカラー（有効な行だけ。ランキングの変遷グラフ用。2026-11-28 追加）。
	 * チームがリーグを移っても使えるよう、リーグでは絞らない（呼び出し側で同じリーグを優先する）。
	 */
	public List<TeamColorDTO> findActiveByCountry(String country) {
		String sql = """
				    SELECT id, country, league, team, team_color_main_hex, team_color_sub_hex, del_flg
				    FROM team_color_master
				    WHERE COALESCE(del_flg, '0') = '0'
				      AND normalize(BTRIM(country), NFKC) = normalize(BTRIM(CAST(:country AS text)), NFKC)
				""";
		return masterJdbcTemplate.query(sql, new MapSqlParameterSource().addValue("country", country == null ? "" : country),
				(rs, n) -> {
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
				    ORDER BY country COLLATE "ja-x-icu", league COLLATE "ja-x-icu", team COLLATE "ja-x-icu"
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

		sql.append(" ORDER BY country COLLATE \"ja-x-icu\", league COLLATE \"ja-x-icu\", team COLLATE \"ja-x-icu\" ");

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
				      team = :team AND
				      COALESCE(del_flg, '0') = '0'
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

	// --------------------------------------------------------
	// チームマスタとの同期（2026-11-24 追加）
	// --------------------------------------------------------
	/**
	 * チームカラーをチームマスタ（country_league_master の del_flg = '0'）に合わせる。何度実行しても同じ結果になる。
	 * <ol>
	 *   <li>fillLinkExact: リンクを入れる（同じ国・リーグ・チーム）</li>
	 *   <li>fillLinkByTeam: リンクを入れる（リーグが変わった古い行。同じ国・チーム名でリンクが 1 つだけ）</li>
	 *   <li>deactivateNotInMaster: チームマスタで有効でない行を無効にする（履歴として残す）</li>
	 *   <li>deactivateDuplicates: 同じ国・リーグ・チームの有効な行が複数あれば、新しい id だけ残す</li>
	 *   <li>reactivate: チームマスタで有効に戻ったチームは、無効にしていた行を有効に戻す</li>
	 *   <li>insertMissing: チームマスタで有効なのにカラーの行が無いチームの行を作る（色は空）</li>
	 *   <li>carryColors: 色が空の有効な行に、同じチーム（リンク）の前のリーグの色を引き継ぐ</li>
	 * </ol>
	 * リーグから外れた・昇格降格前のリーグの行は消さずに無効（del_flg = '1'）にして履歴として残す。
	 * 同じチームかどうかは link（チームページの URL。チームマスタと同じ）で判断する。
	 *
	 * @return 手順ごとの件数（"手順名=件数"）
	 */
	public java.util.List<String> syncWithTeamMaster() {
		java.util.List<String> result = new java.util.ArrayList<>();
		for (String[] step : SYNC_SQLS) {
			int n = masterJdbcTemplate.getJdbcTemplate().update(step[1]);
			result.add(step[0] + "=" + n);
		}
		return result;
	}

	/** {手順名, SQL}（bookmakers-batch の TeamColorMasterRepository と同じ SQL） */
	private static final String[][] SYNC_SQLS = {
		{ "fillLinkExact", """
				UPDATE team_color_master c
				   SET link = m.link
				  FROM country_league_master m
				 WHERE c.link IS NULL
				   AND m.country = c.country AND m.league = c.league AND m.team = c.team
				   AND NULLIF(BTRIM(m.link), '') IS NOT NULL
				""" },
		{ "fillLinkByTeam", """
				UPDATE team_color_master c
				   SET link = u.link
				  FROM (SELECT country, team, MIN(link) AS link
				          FROM country_league_master
				         WHERE NULLIF(BTRIM(link), '') IS NOT NULL
				         GROUP BY country, team
				        HAVING COUNT(DISTINCT link) = 1) u
				 WHERE c.link IS NULL
				   AND u.country = c.country AND u.team = c.team
				""" },
		{ "deactivateNotInMaster", """
				UPDATE team_color_master c
				   SET del_flg = '1', update_id = 'SYNC', update_time = CURRENT_TIMESTAMP
				 WHERE COALESCE(c.del_flg, '0') = '0'
				   AND NOT EXISTS (SELECT 1 FROM country_league_master m
				                    WHERE m.del_flg = '0'
				                      AND m.country = c.country AND m.league = c.league AND m.team = c.team)
				""" },
		{ "deactivateDuplicates", """
				UPDATE team_color_master c
				   SET del_flg = '1', update_id = 'SYNC', update_time = CURRENT_TIMESTAMP
				 WHERE COALESCE(c.del_flg, '0') = '0'
				   AND EXISTS (SELECT 1 FROM team_color_master x
				                WHERE COALESCE(x.del_flg, '0') = '0'
				                  AND x.country = c.country AND x.league = c.league AND x.team = c.team
				                  AND x.id > c.id)
				""" },
		{ "reactivate", """
				UPDATE team_color_master c
				   SET del_flg = '0', update_id = 'SYNC', update_time = CURRENT_TIMESTAMP
				 WHERE c.del_flg = '1'
				   AND EXISTS (SELECT 1 FROM country_league_master m
				                WHERE m.del_flg = '0'
				                  AND m.country = c.country AND m.league = c.league AND m.team = c.team)
				   AND NOT EXISTS (SELECT 1 FROM team_color_master v
				                    WHERE COALESCE(v.del_flg, '0') = '0'
				                      AND v.country = c.country AND v.league = c.league AND v.team = c.team)
				   AND c.id = (SELECT MAX(x.id) FROM team_color_master x
				                WHERE x.country = c.country AND x.league = c.league AND x.team = c.team)
				""" },
		{ "insertMissing", """
				INSERT INTO team_color_master (
				    country, league, sub_league, team, link,
				    team_color_main_hex, team_color_sub_hex, del_flg,
				    register_id, register_time, update_id, update_time)
				SELECT DISTINCT ON (m.country, m.league, m.team)
				       m.country, m.league, COALESCE(m.sub_league, ''), m.team, m.link,
				       NULL, NULL, '0',
				       'SYNC', CURRENT_TIMESTAMP, 'SYNC', CURRENT_TIMESTAMP
				  FROM country_league_master m
				 WHERE m.del_flg = '0'
				   AND NOT EXISTS (SELECT 1 FROM team_color_master v
				                    WHERE COALESCE(v.del_flg, '0') = '0'
				                      AND v.country = m.country AND v.league = m.league AND v.team = m.team)
				 ORDER BY m.country, m.league, m.team, m.id DESC
				""" },
		{ "carryColors", """
				UPDATE team_color_master c
				   SET team_color_main_hex = p.team_color_main_hex,
				       team_color_sub_hex  = COALESCE(NULLIF(BTRIM(c.team_color_sub_hex), ''), p.team_color_sub_hex),
				       update_id = 'SYNC', update_time = CURRENT_TIMESTAMP
				  FROM (SELECT DISTINCT ON (link) link, team_color_main_hex, team_color_sub_hex
				          FROM team_color_master
				         WHERE link IS NOT NULL
				           AND NULLIF(BTRIM(team_color_main_hex), '') IS NOT NULL
				         ORDER BY link, update_time DESC NULLS LAST, id DESC) p
				 WHERE COALESCE(c.del_flg, '0') = '0'
				   AND NULLIF(BTRIM(c.team_color_main_hex), '') IS NULL
				   AND c.link = p.link
				""" },
	};

	private boolean hasText(String s) {
	    return s != null && !s.isBlank();
	}

}