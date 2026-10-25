package dev.web.repository.master;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.Data;

/**
 * country_league_master から「チーム → 国・リーグ」の対応を取得する（dataCategory の照合用）
 * <p>表示対象の国リーグ（all_league_scrape_master の disp_flg = '0'）に紐づく行だけを使う。
 * 降格などで今は表示しないリーグに残っているチームの行は、候補に出さない。</p>
 * @author shiraishitoshio
 *
 */
@Repository
public class TeamLeagueMasterRepository {

	private final NamedParameterJdbcTemplate webMasterJdbcTemplate;

	public TeamLeagueMasterRepository(
			@Qualifier("webMasterJdbcTemplate") NamedParameterJdbcTemplate webMasterJdbcTemplate) {
		this.webMasterJdbcTemplate = webMasterJdbcTemplate;
	}

	/**
	 * チーム × 国 × リーグの一覧（削除済みの行も含む。active はその組み合わせに有効行があるか）。
	 * マスタは数千行程度なので、画面1回につき1回まとめて取得して Java 側で照合する。
	 * <p>国・リーグが all_league_scrape_master の disp_flg = '0'（表示する）のものだけ。</p>
	 */
	public List<TeamLeagueRow> findAllTeamLeagues() {
		String sql = """
				SELECT
				    team,
				    BTRIM(country) AS country,
				    BTRIM(league)  AS league,
				    BOOL_OR(del_flg = '0') AS active
				FROM country_league_master clm
				WHERE team    IS NOT NULL AND BTRIM(team)    <> ''
				  AND country IS NOT NULL AND BTRIM(country) <> ''
				  AND league  IS NOT NULL AND BTRIM(league)  <> ''
				  -- 表示する国リーグだけ（all_league_scrape_master.disp_flg = '0'）
				  AND EXISTS (
				      SELECT 1
				      FROM all_league_scrape_master alm
				      WHERE BTRIM(alm.country) = BTRIM(clm.country)
				        AND BTRIM(alm.league)  = BTRIM(clm.league)
				        AND alm.disp_flg = '0'
				  )
				GROUP BY team, BTRIM(country), BTRIM(league)
				""";
		return webMasterJdbcTemplate.query(sql, new MapSqlParameterSource(), (rs, rowNum) -> {
			TeamLeagueRow r = new TeamLeagueRow();
			r.setTeam(rs.getString("team"));
			r.setCountry(rs.getString("country"));
			r.setLeague(rs.getString("league"));
			r.setActive(rs.getBoolean("active"));
			return r;
		});
	}

	@Data
	public static class TeamLeagueRow {
		private String team;
		private String country;
		private String league;
		private boolean active;
	}
}
