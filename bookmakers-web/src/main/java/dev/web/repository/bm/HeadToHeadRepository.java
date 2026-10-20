package dev.web.repository.bm;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.web.api.headToHead.bm_w001.HeadToHeadResponse;
import dev.web.api.headToHead.headToHeadDTO.HeadToHeadMatchDTO;
import dev.web.api.headToHead.headToHeadDTO.HeadToHeadRecordDTO;

/**
 * 対戦成績（soccer_bm: VIEW head_to_head_summary・head_to_head_match）
 *
 * <ul>
 *   <li>{@link #fillSummary}: 通算・ホーム時・アウェー時の成績（head_to_head_summary の 1 行）</li>
 *   <li>{@link #findMatches}: 直近の対戦（head_to_head_match。新しい順）</li>
 * </ul>
 * チーム名は NFKC 正規化・前後空白除去したキー（team_key / opponent_key）で引く。
 * @author shiraishitoshio
 */
@Repository
public class HeadToHeadRepository {

	private final NamedParameterJdbcTemplate bmJdbcTemplate;

	public HeadToHeadRepository(@Qualifier("bmJdbcTemplate") NamedParameterJdbcTemplate bmJdbcTemplate) {
		this.bmJdbcTemplate = bmJdbcTemplate;
	}

	/**
	 * 通算成績を res に入れる
	 * @return 対戦があれば true（無ければ res は 0 件のまま）
	 */
	public boolean fillSummary(String team, String opponent, HeadToHeadResponse res) {
		String sql = """
				SELECT matches, wins, draws, losses, goals_for, goals_against,
				       home_matches, home_wins, home_draws, home_losses, home_goals_for, home_goals_against,
				       away_matches, away_wins, away_draws, away_losses, away_goals_for, away_goals_against,
				       pk_wins, pk_losses, clean_sheets, failed_to_score, both_scored,
				       scored_first, conceded_first, seasons,
				       to_char(first_match_time AT TIME ZONE 'Asia/Tokyo', 'YYYY-MM-DD HH24:MI:SS') AS first_match_time,
				       to_char(last_match_time  AT TIME ZONE 'Asia/Tokyo', 'YYYY-MM-DD HH24:MI:SS') AS last_match_time
				FROM head_to_head_summary
				WHERE team_key     = normalize(BTRIM(:team), NFKC)
				  AND opponent_key = normalize(BTRIM(:opponent), NFKC)
				""";
		List<Boolean> found = bmJdbcTemplate.query(sql, params(team, opponent), (rs, n) -> {
			set(res.getTotal(), rs.getInt("matches"), rs.getInt("wins"), rs.getInt("draws"), rs.getInt("losses"),
					rs.getInt("goals_for"), rs.getInt("goals_against"));
			set(res.getHome(), rs.getInt("home_matches"), rs.getInt("home_wins"), rs.getInt("home_draws"),
					rs.getInt("home_losses"), rs.getInt("home_goals_for"), rs.getInt("home_goals_against"));
			set(res.getAway(), rs.getInt("away_matches"), rs.getInt("away_wins"), rs.getInt("away_draws"),
					rs.getInt("away_losses"), rs.getInt("away_goals_for"), rs.getInt("away_goals_against"));
			res.setPkWins(rs.getInt("pk_wins"));
			res.setPkLosses(rs.getInt("pk_losses"));
			res.setCleanSheets(rs.getInt("clean_sheets"));
			res.setFailedToScore(rs.getInt("failed_to_score"));
			res.setBothScored(rs.getInt("both_scored"));
			res.setScoredFirst(rs.getInt("scored_first"));
			res.setConcededFirst(rs.getInt("conceded_first"));
			res.setSeasons(rs.getInt("seasons"));
			res.setFirstMatchTime(rs.getString("first_match_time"));
			res.setLastMatchTime(rs.getString("last_match_time"));
			return Boolean.TRUE;
		});
		return !found.isEmpty();
	}

	/** 直近の対戦（試合時刻の新しい順。時刻が無い試合はシーズン・ラウンドの新しい順で後ろ） */
	public List<HeadToHeadMatchDTO> findMatches(String team, String opponent, int limit) {
		String sql = """
				SELECT season, country, league, round_no, data_category,
				       to_char(match_time AT TIME ZONE 'Asia/Tokyo', 'YYYY-MM-DD HH24:MI:SS') AS match_time,
				       ha, home_team_name, away_team_name, home_score, away_score,
				       result, pk_flg, pk_goals_for, pk_goals_against,
				       goals_for_1st, goals_against_1st, first_goal
				FROM head_to_head_match
				WHERE team_key     = normalize(BTRIM(:team), NFKC)
				  AND opponent_key = normalize(BTRIM(:opponent), NFKC)
				ORDER BY match_time DESC NULLS LAST, season DESC, round_no DESC
				LIMIT :limit
				""";
		MapSqlParameterSource p = params(team, opponent).addValue("limit", limit);
		return bmJdbcTemplate.query(sql, p, (rs, n) -> {
			HeadToHeadMatchDTO m = new HeadToHeadMatchDTO();
			m.setSeason(rs.getString("season"));
			m.setCountry(rs.getString("country"));
			m.setLeague(rs.getString("league"));
			m.setRoundNo((Integer) rs.getObject("round_no", Integer.class));
			m.setDataCategory(rs.getString("data_category"));
			m.setMatchTime(rs.getString("match_time"));
			m.setHa(rs.getString("ha"));
			m.setHomeTeamName(rs.getString("home_team_name"));
			m.setAwayTeamName(rs.getString("away_team_name"));
			m.setHomeScore((Integer) rs.getObject("home_score", Integer.class));
			m.setAwayScore((Integer) rs.getObject("away_score", Integer.class));
			m.setResult(rs.getString("result"));
			m.setPk(rs.getBoolean("pk_flg"));
			m.setPkGoalsFor((Integer) rs.getObject("pk_goals_for", Integer.class));
			m.setPkGoalsAgainst((Integer) rs.getObject("pk_goals_against", Integer.class));
			m.setGoalsFor1st((Integer) rs.getObject("goals_for_1st", Integer.class));
			m.setGoalsAgainst1st((Integer) rs.getObject("goals_against_1st", Integer.class));
			m.setFirstGoal(rs.getString("first_goal"));
			return m;
		});
	}

	private static void set(HeadToHeadRecordDTO r, int matches, int wins, int draws,
			int losses, int goalsFor, int goalsAgainst) {
		r.setMatches(matches);
		r.setWins(wins);
		r.setDraws(draws);
		r.setLosses(losses);
		r.setGoalsFor(goalsFor);
		r.setGoalsAgainst(goalsAgainst);
	}

	private static MapSqlParameterSource params(String team, String opponent) {
		return new MapSqlParameterSource().addValue("team", team).addValue("opponent", opponent);
	}
}
