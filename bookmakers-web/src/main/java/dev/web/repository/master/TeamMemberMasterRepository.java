package dev.web.repository.master;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.web.api.dashboard.teamMemberDTO.TeamMemberRow;


/**
 * チームメンバー（TeamResults.tsx）用 Repository（soccer_bm_master: team_member_master）
 * @author shiraishitoshio
 */
@Repository
public class TeamMemberMasterRepository {

	private final NamedParameterJdbcTemplate masterJdbcTemplate;

	public TeamMemberMasterRepository(
			@Qualifier("webMasterJdbcTemplate") NamedParameterJdbcTemplate masterJdbcTemplate) {
		this.masterJdbcTemplate = masterJdbcTemplate;
	}

	/**
	 * チームの現役メンバー（削除・引退を除く）。
	 * 同じ名前が複数行ある場合は情報日・更新日時が新しい行を使う。
	 */
	public List<TeamMemberRow> findMembers(String country, String league, String team) {
		String sql = """
				SELECT DISTINCT ON (BTRIM(member))
				       NULLIF(BTRIM(jersey), '')            AS jersey,
				       BTRIM(member)                        AS member,
				       NULLIF(BTRIM(position), '')          AS position,
				       NULLIF(BTRIM(age), '')               AS age,
				       NULLIF(BTRIM(height), '')            AS height,
				       NULLIF(BTRIM(market_value), '')      AS market_value,
				       NULLIF(BTRIM(injury), '')            AS injury,
				       NULLIF(BTRIM(loan_belong), '')       AS loan_belong,
				       NULLIF(BTRIM(face_pic_path), '')     AS face_pic_path,
				       NULLIF(BTRIM(latest_info_date::text), '') AS latest_info_date
				  FROM team_member_master
				 WHERE country = :country
				   AND league  = :league
				   AND team    = :team
				   AND COALESCE(del_flg, '0')    = '0'
				   AND COALESCE(retire_flg, '0') = '0'
				   AND NULLIF(BTRIM(member), '') IS NOT NULL
				 ORDER BY BTRIM(member), latest_info_date DESC NULLS LAST, update_time DESC NULLS LAST, id DESC
				""";
		MapSqlParameterSource p = new MapSqlParameterSource()
				.addValue("country", country)
				.addValue("league", league)
				.addValue("team", team);
		return masterJdbcTemplate.query(sql, p, (rs, n) -> {
			TeamMemberRow r = new TeamMemberRow();
			r.setJersey(rs.getString("jersey"));
			r.setMember(rs.getString("member"));
			r.setPosition(rs.getString("position"));
			r.setAge(rs.getString("age"));
			r.setHeight(rs.getString("height"));
			r.setMarketValue(rs.getString("market_value"));
			r.setInjury(rs.getString("injury"));
			r.setLoanBelong(rs.getString("loan_belong"));
			r.setFacePicPath(rs.getString("face_pic_path"));
			r.setLatestInfoDate(rs.getString("latest_info_date"));
			return r;
		});
	}
}
