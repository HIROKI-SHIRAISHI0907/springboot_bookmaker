package dev.web.repository.master;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.web.api.dashboard.futureDTO.DashboardFutureRow;


/**
 * トップ画面（Dashboard）用 Repository（soccer_master: future_master）
 *
 * <ul>
 *   <li>{@link #findUpcoming}: これからの試合（今〜hours 時間後）。同じ対戦・同じ時刻の重複行は1行にする。</li>
 *   <li>{@link #findUpcomingByTeams}: お気に入りチームの次の試合を探す用（チーム名は NFKC 正規化して比較）。</li>
 * </ul>
 *
 * @author shiraishitoshio
 */
@Repository
public class DashboardFutureRepository {

	private static final String COLUMNS = """
			  game_team_category,
			  future_time,
			  CAST(home_rank AS text) AS home_rank,
			  CAST(away_rank AS text) AS away_rank,
			  home_team_name,
			  away_team_name
			""";

	private final NamedParameterJdbcTemplate masterJdbcTemplate;

	public DashboardFutureRepository(
			@Qualifier("webMasterJdbcTemplate") NamedParameterJdbcTemplate masterJdbcTemplate) {
		this.masterJdbcTemplate = masterJdbcTemplate;
	}

	/**
	 * これからの試合
	 *
	 * @param hours 今から何時間後まで
	 * @param limit 最大件数
	 */
	public List<DashboardFutureRow> findUpcoming(int hours, int limit) {
		String sql = "SELECT * FROM (SELECT DISTINCT ON (home_team_name, away_team_name, future_time)" + COLUMNS
				+ " FROM future_master"
				+ " WHERE future_time > CURRENT_TIMESTAMP"
				+ "   AND future_time <= CURRENT_TIMESTAMP + make_interval(hours => :hours)"
				+ " ORDER BY home_team_name, away_team_name, future_time, seq DESC) t"
				+ " ORDER BY future_time, home_team_name"
				+ " LIMIT :limit";
		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("hours", hours)
				.addValue("limit", limit);
		return masterJdbcTemplate.query(sql, params, (rs, n) -> toRow(rs));
	}

	/**
	 * 指定チームのこれからの試合（ホーム・アウェーどちらでも）
	 *
	 * @param teams チーム名（NFKC 正規化済み）
	 * @param hours 今から何時間後まで
	 */
	public List<DashboardFutureRow> findUpcomingByTeams(List<String> teams, int hours) {
		if (teams == null || teams.isEmpty()) {
			return List.of();
		}
		String sql = "SELECT" + COLUMNS
				+ " FROM future_master"
				+ " WHERE future_time > CURRENT_TIMESTAMP"
				+ "   AND future_time <= CURRENT_TIMESTAMP + make_interval(hours => :hours)"
				+ "   AND (normalize(home_team_name, NFKC) IN (:teams) OR normalize(away_team_name, NFKC) IN (:teams))"
				+ " ORDER BY future_time";
		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("teams", teams)
				.addValue("hours", hours);
		return masterJdbcTemplate.query(sql, params, (rs, n) -> toRow(rs));
	}

	private static DashboardFutureRow toRow(java.sql.ResultSet rs) throws java.sql.SQLException {
		DashboardFutureRow r = new DashboardFutureRow();
		r.setGameTeamCategory(rs.getString("game_team_category"));
		java.sql.Timestamp ts = rs.getTimestamp("future_time");
		r.setFutureTime(ts == null ? null : ts.toInstant().toString());
		r.setHomeRank(rs.getString("home_rank"));
		r.setAwayRank(rs.getString("away_rank"));
		r.setHomeTeamName(rs.getString("home_team_name"));
		r.setAwayTeamName(rs.getString("away_team_name"));
		return r;
	}
}
