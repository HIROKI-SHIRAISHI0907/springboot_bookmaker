package dev.web.repository.bm;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.web.api.ranking.bm_w001.RankingRow;

/**
 * ランキング・変遷グラフ用（soccer_bm。2026-11-28 追加）
 * <ul>
 *   <li>{@link #findLatest}: 統計（surface_overview_match）の最新シーズンと、テーブルに入っている国・リーグの表記</li>
 *   <li>{@link #findResultRows}: 試合の結果（統計、無ければ終了済の試合から作ったデータ {@link StaticOverviewSource}）</li>
 *   <li>{@link #findStatRows}: 試合のスタッツ（match_team_snapshot_last の試合終了の時点。相手の値もつける）</li>
 * </ul>
 * 注意: SQL に「:数字」（配列の [1:5] など）を書かない（NamedParameterJdbcTemplate がパラメータと読む）。
 * @author shiraishitoshio
 */
@Repository
public class RankingRepository {

	private static String key(String expr) {
		return "normalize(BTRIM(" + expr + "), NFKC)";
	}

	private final NamedParameterJdbcTemplate bmJdbcTemplate;

	public RankingRepository(@Qualifier("bmJdbcTemplate") NamedParameterJdbcTemplate bmJdbcTemplate) {
		this.bmJdbcTemplate = bmJdbcTemplate;
	}

	/** {シーズン, 国, リーグ}（統計が無ければ null） */
	public String[] findLatest(String country, String league) {
		String sql = "SELECT season, country, league FROM surface_overview_match"
				+ " WHERE " + key("country") + " = " + key("CAST(:country AS text)")
				+ "   AND " + key("league") + " = " + key("CAST(:league AS text)")
				+ " ORDER BY season DESC LIMIT 1";
		List<String[]> l = bmJdbcTemplate.query(sql, params(country, league, null),
				(rs, n) -> new String[] { rs.getString("season"), rs.getString("country"), rs.getString("league") });
		return l.isEmpty() ? null : l.get(0);
	}

	/** 試合の結果（ラウンド番号のある試合だけ） */
	public List<RankingRow> findResultRows(String country, String league, String season) {
		String sql = "SELECT team, round_no, goals_for, goals_against, result, points,"
				+ " goals_for_1st, goals_for_2nd, goals_against_1st, goals_against_2nd,"
				+ " first_goal, flow_known, ever_trailed"
				+ " FROM " + StaticOverviewSource.from(season)
				+ " WHERE season = :season"
				+ "   AND " + key("country") + " = " + key("CAST(:country AS text)")
				+ "   AND " + key("league") + " = " + key("CAST(:league AS text)")
				+ "   AND round_no IS NOT NULL";
		return bmJdbcTemplate.query(sql, params(country, league, season), (rs, n) -> {
			RankingRow r = new RankingRow();
			r.setTeam(rs.getString("team"));
			r.setRoundNo(rs.getInt("round_no"));
			r.setGoalsFor(intOrNull(rs, "goals_for"));
			r.setGoalsAgainst(intOrNull(rs, "goals_against"));
			r.setResult(rs.getString("result"));
			r.setPoints(intOrNull(rs, "points"));
			r.setGoalsFor1st(intOrNull(rs, "goals_for_1st"));
			r.setGoalsFor2nd(intOrNull(rs, "goals_for_2nd"));
			r.setGoalsAgainst1st(intOrNull(rs, "goals_against_1st"));
			r.setGoalsAgainst2nd(intOrNull(rs, "goals_against_2nd"));
			r.setFirstGoal(rs.getString("first_goal"));
			r.setFlowKnown(rs.getBoolean("flow_known"));
			r.setEverTrailed(rs.getBoolean("ever_trailed"));
			return r;
		});
	}

	/**
	 * 試合のスタッツ（試合終了まで取れた試合だけ）。国・リーグは {@link #findLatest} が返したテーブルの表記で渡すこと
	 * （ビューの絞り込みをインデックスに効かせるため、そのまま = で比べる）。
	 */
	public List<RankingRow> findStatRows(String country, String league, String season) {
		String sql = "SELECT l.team, l.round_no::INTEGER AS round_no,"
				+ " l.possession, l.exp, l.shoot_all, l.shoot_in, l.big_chance, l.corner,"
				+ " l.pass_count_success, l.pass_count_try, l.box_touch, l.tackle_count_success, l.tackle_count_try,"
				+ " l.intercept_count, l.clear_count, l.foul, l.yellow_card, l.keeper_save, l.offside,"
				+ " o.exp AS opp_exp, o.shoot_all AS opp_shoot_all, o.shoot_in AS opp_shoot_in"
				+ " FROM match_team_snapshot_last l"
				+ " LEFT JOIN match_team_snapshot_last o"
				+ "   ON o.season = l.season AND o.country = l.country AND o.league = l.league"
				+ "  AND o.round_no = l.round_no AND o.team = l.opponent AND o.opponent = l.team AND o.ha <> l.ha"
				+ " WHERE l.season = :season AND l.country = :country AND l.league = :league"
				+ "   AND l.finished"
				+ "   AND (o.season IS NULL OR o.season = :season)";
		return bmJdbcTemplate.query(sql, params(country, league, season), (rs, n) -> {
			RankingRow r = new RankingRow();
			r.setTeam(rs.getString("team"));
			r.setRoundNo(rs.getInt("round_no"));
			r.setPossession(dblOrNull(rs, "possession"));
			r.setXg(dblOrNull(rs, "exp"));
			r.setShots(intOrNull(rs, "shoot_all"));
			r.setShotsOnTarget(intOrNull(rs, "shoot_in"));
			r.setBigChances(intOrNull(rs, "big_chance"));
			r.setCorners(intOrNull(rs, "corner"));
			r.setPassSuccess(intOrNull(rs, "pass_count_success"));
			r.setPassTry(intOrNull(rs, "pass_count_try"));
			r.setBoxTouches(intOrNull(rs, "box_touch"));
			r.setTackleSuccess(intOrNull(rs, "tackle_count_success"));
			r.setTackleTry(intOrNull(rs, "tackle_count_try"));
			r.setInterceptions(intOrNull(rs, "intercept_count"));
			r.setClears(intOrNull(rs, "clear_count"));
			r.setFouls(intOrNull(rs, "foul"));
			r.setYellowCards(intOrNull(rs, "yellow_card"));
			r.setKeeperSaves(intOrNull(rs, "keeper_save"));
			r.setOffsides(intOrNull(rs, "offside"));
			r.setXgAgainst(dblOrNull(rs, "opp_exp"));
			r.setShotsAgainst(intOrNull(rs, "opp_shoot_all"));
			r.setShotsOnTargetAgainst(intOrNull(rs, "opp_shoot_in"));
			return r;
		});
	}

	private static Integer intOrNull(ResultSet rs, String col) throws SQLException {
		int v = rs.getInt(col);
		return rs.wasNull() ? null : v;
	}

	private static Double dblOrNull(ResultSet rs, String col) throws SQLException {
		double v = rs.getDouble(col);
		return rs.wasNull() ? null : v;
	}

	private static MapSqlParameterSource params(String country, String league, String season) {
		return new MapSqlParameterSource()
				.addValue("country", country == null ? "" : country)
				.addValue("league", league == null ? "" : league)
				.addValue("season", season == null ? "" : season);
	}
}
