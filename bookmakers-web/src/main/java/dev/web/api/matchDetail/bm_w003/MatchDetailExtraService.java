package dev.web.api.matchDetail.bm_w003;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import dev.web.repository.bm.MatchDetailExtraRepository;
import lombok.RequiredArgsConstructor;

/**
 * 試合詳細の統計詳細データ（match_detail_*）を返すサービス。
 * 試合が終わってから取得するデータなので、画面はライブ中の30秒更新とは別に1回だけ読む。
 * @author shiraishitoshio
 */
@Service
@RequiredArgsConstructor
public class MatchDetailExtraService {

	private final MatchDetailExtraRepository repository;

	public MatchDetailExtraResponse getExtra(String matchId) {
		MatchDetailExtraResponse res = new MatchDetailExtraResponse();
		String id = trimToNull(matchId);
		res.setMatchId(id);
		if (id == null) {
			return res;
		}

		List<Map<String, Object>> match = repository.findMatch(id);
		if (match.isEmpty()) {
			return res;
		}
		Map<String, Object> m = match.get(0);
		res.setFound(true);
		res.setCategory(s(m, "category"));
		res.setHomeTeam(s(m, "home_team_name"));
		res.setAwayTeam(s(m, "away_team_name"));
		res.setHomeScore(s(m, "home_score"));
		res.setAwayScore(s(m, "away_score"));
		res.setScrapeErrors(s(m, "scrape_errors"));

		for (Map<String, Object> r : repository.findMomentum(id)) {
			MatchDetailExtraResponse.Momentum x = new MatchDetailExtraResponse.Momentum();
			x.setHalf(s(r, "half"));
			x.setMinuteLabel(s(r, "minute_label"));
			x.setMinute(s(r, "minute"));
			x.setAddedMinute(s(r, "added_minute"));
			x.setSide(s(r, "side"));
			x.setValue(s(r, "momentum_value"));
			x.setSignedValue(s(r, "signed_momentum_value"));
			res.getMomentum().add(x);
		}

		for (Map<String, Object> r : repository.findMomentumIncident(id)) {
			MatchDetailExtraResponse.MomentumIncident x = new MatchDetailExtraResponse.MomentumIncident();
			x.setType(s(r, "incident_type"));
			x.setMinuteLabel(s(r, "minute_label"));
			x.setMinute(s(r, "minute"));
			x.setSide(s(r, "side"));
			res.getMomentumIncidents().add(x);
		}

		// ハーフごとのスコアは、出来事の行に付いている（同じハーフの最初の行の値を使う）
		Map<String, String> halfScores = new LinkedHashMap<>();
		for (Map<String, Object> r : repository.findSummary(id)) {
			MatchDetailExtraResponse.Event x = new MatchDetailExtraResponse.Event();
			x.setHalf(s(r, "half"));
			x.setTimeLabel(s(r, "time_label"));
			x.setMinute(s(r, "minute"));
			x.setAddedMinute(s(r, "added_minute"));
			x.setSide(s(r, "side"));
			x.setType(s(r, "incident_type"));
			x.setPlayer(s(r, "player_name"));
			x.setSubOutPlayer(s(r, "sub_out_player_name"));
			x.setScore(s(r, "score_at_time"));
			x.setNote(s(r, "note"));
			x.setAssist(s(r, "note2"));
			x.setDescription(s(r, "description"));
			res.getEvents().add(x);
			if (x.getHalf() != null) {
				halfScores.putIfAbsent(x.getHalf(), s(r, "half_score"));
			}
		}
		for (Map.Entry<String, String> e : halfScores.entrySet()) {
			MatchDetailExtraResponse.Half h = new MatchDetailExtraResponse.Half();
			h.setHalf(e.getKey());
			h.setScore(e.getValue());
			res.getHalves().add(h);
		}

		for (Map<String, Object> r : repository.findGoal(id)) {
			MatchDetailExtraResponse.Goal x = new MatchDetailExtraResponse.Goal();
			x.setTimeLabel(s(r, "time_label"));
			x.setMinute(s(r, "minute"));
			x.setAddedMinute(s(r, "added_minute"));
			x.setHalf(s(r, "half"));
			x.setSide(s(r, "scoring_side"));
			x.setScorer(s(r, "scorer_name"));
			x.setScorerTeam(s(r, "scorer_team_name"));
			x.setOwnGoal("1".equals(s(r, "own_goal_flg")));
			x.setPenalty("1".equals(s(r, "penalty_flg")));
			x.setAssist(s(r, "assist_name"));
			x.setScore(s(r, "score_at_time"));
			res.getGoals().add(x);
		}

		for (Map<String, Object> r : repository.findFormation(id)) {
			MatchDetailExtraResponse.Formation x = new MatchDetailExtraResponse.Formation();
			x.setSide(s(r, "side"));
			x.setTeamName(s(r, "team_name"));
			x.setFormation(s(r, "formation"));
			x.setAverageRating(s(r, "average_rating"));
			x.setCoach(s(r, "coach_name"));
			x.setCoachNationality(s(r, "coach_nationality"));
			res.getFormations().add(x);
		}

		for (Map<String, Object> r : repository.findLineup(id)) {
			MatchDetailExtraResponse.Lineup x = new MatchDetailExtraResponse.Lineup();
			x.setSide(s(r, "side"));
			x.setLineupType(s(r, "lineup_type"));
			x.setPlayerId(s(r, "player_id"));
			x.setNumber(s(r, "jersey_number"));
			x.setName(s(r, "player_name"));
			x.setNationality(s(r, "nationality"));
			x.setRole(s(r, "role"));
			x.setPlayed("1".equals(s(r, "played_flg")));
			x.setSubInMinute(s(r, "sub_in_minute"));
			x.setSubInFor(s(r, "sub_in_for_player_name"));
			x.setSubOutMinute(s(r, "sub_out_minute"));
			x.setSubOutFor(s(r, "sub_out_for_player_name"));
			x.setRating(s(r, "final_rating"));
			x.setPitchRow(s(r, "pitch_row"));
			x.setPitchCol(s(r, "pitch_col"));
			x.setGoals(s(r, "goal_count"));
			x.setOwnGoals(s(r, "own_goal_count"));
			x.setEvents(s(r, "events"));
			res.getLineups().add(x);
		}

		for (Map<String, Object> r : repository.findMissing(id)) {
			MatchDetailExtraResponse.Missing x = new MatchDetailExtraResponse.Missing();
			x.setSide(s(r, "side"));
			x.setName(s(r, "player_name"));
			x.setNationality(s(r, "nationality"));
			x.setReason(s(r, "reason"));
			res.getMissing().add(x);
		}

		for (Map<String, Object> r : repository.findMemberStat(id)) {
			MatchDetailExtraResponse.PlayerStat x = new MatchDetailExtraResponse.PlayerStat();
			x.setPlayerId(s(r, "player_id"));
			x.setName(s(r, "player_name"));
			x.setPosition(s(r, "position"));
			x.setSide(s(r, "side"));
			x.setRating(s(r, "fs_rating"));
			x.setMinutes(s(r, "match_minutes_played"));
			x.setGoals(s(r, "goals"));
			x.setAssists(s(r, "assists_goal"));
			x.setXg(s(r, "expected_goals"));
			x.setXa(s(r, "expected_assists"));
			x.setShots(s(r, "shots_total"));
			x.setShotsOnTarget(s(r, "shots_on_target"));
			x.setBigChancesCreated(s(r, "big_chances_created"));
			x.setKeyPasses(s(r, "key_passes"));
			x.setPassesAccurate(s(r, "passes_accurate"));
			x.setPassesFinalThird(s(r, "passes_final_third_accurate"));
			x.setLongBalls(s(r, "long_balls_accurate"));
			x.setCrosses(s(r, "crosses_accurate"));
			x.setTouches(s(r, "touches_total"));
			x.setTouchesBox(s(r, "touches_box_opposite"));
			x.setDribblesWon(s(r, "dribbles_won"));
			x.setDuelsWon(s(r, "duels_won"));
			x.setDuelsTotal(s(r, "duels_total"));
			x.setAerialsWon(s(r, "duels_aerial_won"));
			x.setTacklesWon(s(r, "tackles_won"));
			x.setInterceptions(s(r, "interceptions"));
			x.setClearances(s(r, "clearances"));
			x.setFoulsCommitted(s(r, "fouls_committed"));
			x.setFoulsSuffered(s(r, "fouls_suffered"));
			x.setYellowCards(s(r, "cards_yellow"));
			x.setRedCards(s(r, "cards_red"));
			x.setSaves(s(r, "saves_total"));
			x.setGoalsConceded(s(r, "goals_conceded"));
			x.setGoalsPrevented(s(r, "goals_prevented"));
			res.getPlayerStats().add(x);
		}

		return res;
	}

	/** 列の値を文字列で取る（空・"-" は null） */
	private static String s(Map<String, Object> row, String col) {
		Object v = row.get(col);
		if (v == null) {
			return null;
		}
		String t = String.valueOf(v).trim();
		return t.isEmpty() || "-".equals(t) ? null : t;
	}

	private static String trimToNull(String v) {
		if (v == null) {
			return null;
		}
		String t = v.trim();
		return t.isEmpty() ? null : t;
	}
}
