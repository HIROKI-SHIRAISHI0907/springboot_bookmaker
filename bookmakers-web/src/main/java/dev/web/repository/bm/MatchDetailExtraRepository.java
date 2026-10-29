package dev.web.repository.bm;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 試合詳細の統計詳細データ（B016 が登録する match_detail_* テーブル）参照用リポジトリ。
 * 値はテーブルどおり文字列（VARCHAR）で返す。
 * @author shiraishitoshio
 */
@Repository
public class MatchDetailExtraRepository {

	private final NamedParameterJdbcTemplate bmJdbcTemplate;

	public MatchDetailExtraRepository(
			@Qualifier("bmJdbcTemplate") NamedParameterJdbcTemplate bmJdbcTemplate) {
		this.bmJdbcTemplate = bmJdbcTemplate;
	}

	private List<Map<String, Object>> query(String sql, String matchId) {
		return bmJdbcTemplate.queryForList(sql, new MapSqlParameterSource().addValue("matchId", matchId));
	}

	/** 試合の基本情報（1行。無ければ空） */
	public List<Map<String, Object>> findMatch(String matchId) {
		return query("""
				SELECT match_id, category, home_team_name, away_team_name, home_score, away_score,
				       status, start_time, match_url, scrape_errors
				FROM match_detail_match
				WHERE match_id = :matchId
				""", matchId);
	}

	/** マッチモメンタム（1分1行） */
	public List<Map<String, Object>> findMomentum(String matchId) {
		return query("""
				SELECT half, half_seq, minute_label, minute, added_minute, side, momentum_value, signed_momentum_value
				FROM match_detail_momentum
				WHERE match_id = :matchId
				ORDER BY CAST(NULLIF(half, '') AS INTEGER), CAST(NULLIF(half_seq, '') AS INTEGER), row_no
				""", matchId);
	}

	/** モメンタム上のアイコン */
	public List<Map<String, Object>> findMomentumIncident(String matchId) {
		return query("""
				SELECT incident_type, minute_label, minute, added_minute, side
				FROM match_detail_momentum_incident
				WHERE match_id = :matchId
				ORDER BY row_no
				""", matchId);
	}

	/** 試合の出来事（ゴール・カード・交代など） */
	public List<Map<String, Object>> findSummary(String matchId) {
		return query("""
				SELECT half, half_score, half_seq, time_label, minute, added_minute, side, incident_type,
				       player_name, sub_out_player_name, score_at_time, note, note2, description
				FROM match_detail_summary
				WHERE match_id = :matchId
				ORDER BY row_no
				""", matchId);
	}

	/** ゴール */
	public List<Map<String, Object>> findGoal(String matchId) {
		return query("""
				SELECT time_label, minute, added_minute, half, scoring_side, scorer_name, scorer_team_name,
				       own_goal_flg, penalty_flg, assist_name, score_at_time
				FROM match_detail_goal
				WHERE match_id = :matchId
				ORDER BY row_no
				""", matchId);
	}

	/** フォーメーション・監督（チームごとに1行） */
	public List<Map<String, Object>> findFormation(String matchId) {
		return query("""
				SELECT side, team_name, formation, average_rating, coach_name, coach_nationality
				FROM match_detail_formation
				WHERE match_id = :matchId
				ORDER BY row_no
				""", matchId);
	}

	/** メンバー（スタメン・ベンチ） */
	public List<Map<String, Object>> findLineup(String matchId) {
		return query("""
				SELECT side, lineup_type, player_id, jersey_number, player_name, nationality, role, played_flg,
				       sub_in_minute, sub_in_for_player_name, sub_out_minute, sub_out_for_player_name,
				       final_rating, pitch_row, pitch_col, goal_count, own_goal_count, events
				FROM match_detail_lineup
				WHERE match_id = :matchId
				ORDER BY row_no
				""", matchId);
	}

	/** 欠場者 */
	public List<Map<String, Object>> findMissing(String matchId) {
		return query("""
				SELECT side, player_name, nationality, reason
				FROM match_detail_missing
				WHERE match_id = :matchId
				ORDER BY row_no
				""", matchId);
	}

	/** 選手スタッツ */
	public List<Map<String, Object>> findMemberStat(String matchId) {
		return query("""
				SELECT player_id, player_name, position, side, fs_rating, match_minutes_played,
				       goals, assists_goal, expected_goals, expected_assists,
				       shots_total, shots_on_target, big_chances_created, key_passes,
				       passes_accurate, passes_final_third_accurate, long_balls_accurate, crosses_accurate,
				       touches_total, touches_box_opposite, dribbles_won,
				       duels_won, duels_total, duels_aerial_won, tackles_won, interceptions, clearances,
				       fouls_committed, fouls_suffered, cards_yellow, cards_red,
				       saves_total, goals_conceded, goals_prevented
				FROM match_detail_member_stat
				WHERE match_id = :matchId
				ORDER BY row_no
				""", matchId);
	}
}
