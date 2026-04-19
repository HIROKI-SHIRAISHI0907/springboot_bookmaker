package dev.web.repository.bm;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.web.api.bm_w005.GameDetailDTO;

/**
 * GameDetailRepositoryクラス
 *
 * public.data から 1 試合分のスタッツを取得する。
 */
@Repository
public class GameDetailsRepository {

	private final NamedParameterJdbcTemplate bmJdbcTemplate;

	private static final Pattern PCT_PATTERN = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s*[%％]");
	private static final Pattern FIRST_NUMBER_PATTERN = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)");

	private static final String BASE_SELECT = """
			SELECT
			  d.data_category,
			  CASE
			    WHEN regexp_match(d.data_category, '(ラウンド|Round)\\s*([0-9]+)') IS NULL THEN NULL
			    ELSE CAST((regexp_match(d.data_category, '(ラウンド|Round)\\s*([0-9]+)'))[2] AS INT)
			  END AS round_no,
			  to_char((d.record_time AT TIME ZONE 'Asia/Tokyo'), 'YYYY-MM-DD"T"HH24:MI:SS') AS record_time_jst,
			  d.home_team_name,
			  d.away_team_name,
			  NULLIF(TRIM(d.home_score), '')::int AS home_score,
			  NULLIF(TRIM(d.away_score), '')::int AS away_score,
			  NULLIF(TRIM(d.home_exp), '')::numeric AS home_exp,
			  NULLIF(TRIM(d.away_exp), '')::numeric AS away_exp,
			  NULLIF(TRIM(d.home_in_goal_exp), '')::numeric AS home_in_goal_exp,
			  NULLIF(TRIM(d.away_in_goal_exp), '')::numeric AS away_in_goal_exp,
			  NULLIF(TRIM(d.home_donation), '') AS home_donation,
			  NULLIF(TRIM(d.away_donation), '') AS away_donation,
			  NULLIF(TRIM(d.home_shoot_all), '')::int AS home_shoot_all,
			  NULLIF(TRIM(d.away_shoot_all), '')::int AS away_shoot_all,
			  NULLIF(TRIM(d.home_shoot_in), '')::int AS home_shoot_in,
			  NULLIF(TRIM(d.away_shoot_in), '')::int AS away_shoot_in,
			  NULLIF(TRIM(d.home_shoot_out), '')::int AS home_shoot_out,
			  NULLIF(TRIM(d.away_shoot_out), '')::int AS away_shoot_out,
			  NULLIF(TRIM(d.home_block_shoot), '')::int AS home_block_shoot,
			  NULLIF(TRIM(d.away_block_shoot), '')::int AS away_block_shoot,
			  NULLIF(TRIM(d.home_big_chance), '')::int AS home_big_chance,
			  NULLIF(TRIM(d.away_big_chance), '')::int AS away_big_chance,
			  NULLIF(TRIM(d.home_corner), '')::int AS home_corner,
			  NULLIF(TRIM(d.away_corner), '')::int AS away_corner,
			  NULLIF(TRIM(d.home_box_shoot_in), '')::int AS home_box_shoot_in,
			  NULLIF(TRIM(d.away_box_shoot_in), '')::int AS away_box_shoot_in,
			  NULLIF(TRIM(d.home_box_shoot_out), '')::int AS home_box_shoot_out,
			  NULLIF(TRIM(d.away_box_shoot_out), '')::int AS away_box_shoot_out,
			  NULLIF(TRIM(d.home_goal_post), '')::int AS home_goal_post,
			  NULLIF(TRIM(d.away_goal_post), '')::int AS away_goal_post,
			  NULLIF(TRIM(d.home_goal_head), '')::int AS home_goal_head,
			  NULLIF(TRIM(d.away_goal_head), '')::int AS away_goal_head,
			  NULLIF(TRIM(d.home_keeper_save), '')::int AS home_keeper_save,
			  NULLIF(TRIM(d.away_keeper_save), '')::int AS away_keeper_save,
			  NULLIF(TRIM(d.home_free_kick), '')::int AS home_free_kick,
			  NULLIF(TRIM(d.away_free_kick), '')::int AS away_free_kick,
			  NULLIF(TRIM(d.home_offside), '')::int AS home_offside,
			  NULLIF(TRIM(d.away_offside), '')::int AS away_offside,
			  NULLIF(TRIM(d.home_foul), '')::int AS home_foul,
			  NULLIF(TRIM(d.away_foul), '')::int AS away_foul,
			  NULLIF(TRIM(d.home_yellow_card), '')::int AS home_yellow_card,
			  NULLIF(TRIM(d.away_yellow_card), '')::int AS away_yellow_card,
			  NULLIF(TRIM(d.home_red_card), '')::int AS home_red_card,
			  NULLIF(TRIM(d.away_red_card), '')::int AS away_red_card,
			  NULLIF(TRIM(d.home_slow_in), '')::int AS home_slow_in,
			  NULLIF(TRIM(d.away_slow_in), '')::int AS away_slow_in,
			  NULLIF(TRIM(d.home_box_touch), '')::int AS home_box_touch,
			  NULLIF(TRIM(d.away_box_touch), '')::int AS away_box_touch,
			  NULLIF(TRIM(d.home_pass_count), '') AS home_pass_count,
			  NULLIF(TRIM(d.away_pass_count), '') AS away_pass_count,
			  NULLIF(TRIM(d.home_long_pass_count), '') AS home_long_pass_count,
			  NULLIF(TRIM(d.away_long_pass_count), '') AS away_long_pass_count,
			  NULLIF(TRIM(d.home_final_third_pass_count), '') AS home_final_third_pass_count,
			  NULLIF(TRIM(d.away_final_third_pass_count), '') AS away_final_third_pass_count,
			  NULLIF(TRIM(d.home_cross_count), '') AS home_cross_count,
			  NULLIF(TRIM(d.away_cross_count), '') AS away_cross_count,
			  NULLIF(TRIM(d.home_tackle_count), '') AS home_tackle_count,
			  NULLIF(TRIM(d.away_tackle_count), '') AS away_tackle_count,
			  NULLIF(TRIM(d.home_clear_count), '') AS home_clear_count,
			  NULLIF(TRIM(d.away_clear_count), '') AS away_clear_count,
			  NULLIF(TRIM(d.home_duel_count), '') AS home_duel_count,
			  NULLIF(TRIM(d.away_duel_count), '') AS away_duel_count,
			  NULLIF(TRIM(d.home_intercept_count), '') AS home_intercept_count,
			  NULLIF(TRIM(d.away_intercept_count), '') AS away_intercept_count,
			  NULLIF(TRIM(d.home_manager), '') AS home_manager,
			  NULLIF(TRIM(d.away_manager), '') AS away_manager,
			  NULLIF(TRIM(d.home_formation), '') AS home_formation,
			  NULLIF(TRIM(d.away_formation), '') AS away_formation,
			  NULLIF(TRIM(d.studium), '') AS studium,
			  NULLIF(TRIM(d.capacity), '') AS capacity,
			  NULLIF(TRIM(d.audience), '') AS audience,
			  NULLIF(TRIM(d.judge), '') AS link_maybe,
			  NULLIF(TRIM(d.times), '') AS times
			FROM data d
			""";

	public GameDetailsRepository(
			@Qualifier("bmJdbcTemplate") NamedParameterJdbcTemplate bmJdbcTemplate) {
		this.bmJdbcTemplate = bmJdbcTemplate;
	}

	private static String safeGetString(ResultSet rs, String column) throws SQLException {
		String value = rs.getString(column);
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private static Integer parsePercent(String str) {
		if (str == null) {
			return null;
		}
		Matcher m = PCT_PATTERN.matcher(str);
		if (m.find()) {
			double v = Double.parseDouble(m.group(1));
			return Integer.valueOf((int) Math.round(v));
		}
		return null;
	}

	private static Integer parseStatNumber(String str) {
		if (str == null) {
			return null;
		}

		String text = str.trim();
		if (text.isEmpty()) {
			return null;
		}

		Matcher pctMatcher = PCT_PATTERN.matcher(text);
		if (pctMatcher.find()) {
			double v = Double.parseDouble(pctMatcher.group(1));
			return Integer.valueOf((int) Math.round(v));
		}

		Matcher numberMatcher = FIRST_NUMBER_PATTERN.matcher(text);
		if (numberMatcher.find()) {
			double v = Double.parseDouble(numberMatcher.group(1));
			return Integer.valueOf((int) Math.round(v));
		}

		return null;
	}

	private static Integer safeGetInteger(ResultSet rs, String column) throws SQLException {
		Object value = rs.getObject(column);

		if (value == null) {
			return null;
		}
		if (value instanceof Integer) {
			return (Integer) value;
		}
		if (value instanceof Number) {
			return Integer.valueOf(((Number) value).intValue());
		}
		if (value instanceof String) {
			return parseStatNumber((String) value);
		}

		return null;
	}

	private static Double safeGetDouble(ResultSet rs, String column) throws SQLException {
		Object value = rs.getObject(column);

		if (value == null) {
			return null;
		}
		if (value instanceof BigDecimal) {
			return Double.valueOf(((BigDecimal) value).doubleValue());
		}
		if (value instanceof Number) {
			return Double.valueOf(((Number) value).doubleValue());
		}
		if (value instanceof String) {
			String text = ((String) value).trim();
			if (text.isEmpty()) {
				return null;
			}
			try {
				return Double.valueOf(Double.parseDouble(text));
			} catch (NumberFormatException ignore) {
				Integer parsed = parseStatNumber(text);
				return parsed == null ? null : Double.valueOf(parsed.doubleValue());
			}
		}

		return null;
	}

	private static final RowMapper<GameDetailDTO> GAME_DETAIL_ROW_MAPPER = new RowMapper<GameDetailDTO>() {
		@Override
		public GameDetailDTO mapRow(ResultSet rs, int rowNum) throws SQLException {
			Integer homeScoreObj = safeGetInteger(rs, "home_score");
			Integer awayScoreObj = safeGetInteger(rs, "away_score");

			int homeScoreForJudge = homeScoreObj == null ? 0 : homeScoreObj.intValue();
			int awayScoreForJudge = awayScoreObj == null ? 0 : awayScoreObj.intValue();

			String times = safeGetString(rs, "times");

			boolean finished = times != null
					&& (times.contains("終了")
							|| times.toUpperCase().contains("FT")
							|| times.toUpperCase().contains("AET")
							|| times.toUpperCase().contains("PEN"));

			String winner;
			if (!finished) {
				winner = "LIVE";
			} else if (homeScoreForJudge == awayScoreForJudge) {
				winner = "DRAW";
			} else if (homeScoreForJudge > awayScoreForJudge) {
				winner = "HOME";
			} else {
				winner = "AWAY";
			}

			GameDetailDTO detail = new GameDetailDTO();
			detail.setCompetition(safeGetString(rs, "data_category"));
			detail.setRoundNo(safeGetInteger(rs, "round_no"));
			detail.setRecordedAt(safeGetString(rs, "record_time_jst"));
			detail.setWinner(winner);
			detail.setLink(safeGetString(rs, "link_maybe"));
			detail.setTimes(times);

			GameDetailDTO.TeamSide home = new GameDetailDTO.TeamSide();
			home.setName(safeGetString(rs, "home_team_name"));
			home.setScore(homeScoreObj);
			home.setManager(safeGetString(rs, "home_manager"));
			home.setFormation(safeGetString(rs, "home_formation"));
			home.setXg(safeGetDouble(rs, "home_exp"));
			home.setInGoalXg(safeGetDouble(rs, "home_in_goal_exp"));
			home.setPossession(parsePercent(safeGetString(rs, "home_donation")));
			home.setShots(safeGetInteger(rs, "home_shoot_all"));
			home.setShotsOn(safeGetInteger(rs, "home_shoot_in"));
			home.setShotsOff(safeGetInteger(rs, "home_shoot_out"));
			home.setBlocks(safeGetInteger(rs, "home_block_shoot"));
			home.setBigChances(safeGetInteger(rs, "home_big_chance"));
			home.setCorners(safeGetInteger(rs, "home_corner"));
			home.setBoxShotsIn(safeGetInteger(rs, "home_box_shoot_in"));
			home.setBoxShotsOut(safeGetInteger(rs, "home_box_shoot_out"));
			home.setGoalPost(safeGetInteger(rs, "home_goal_post"));
			home.setHeadGoals(safeGetInteger(rs, "home_goal_head"));
			home.setSaves(safeGetInteger(rs, "home_keeper_save"));
			home.setFreeKicks(safeGetInteger(rs, "home_free_kick"));
			home.setOffsides(safeGetInteger(rs, "home_offside"));
			home.setFouls(safeGetInteger(rs, "home_foul"));
			home.setYc(safeGetInteger(rs, "home_yellow_card"));
			home.setRc(safeGetInteger(rs, "home_red_card"));
			home.setThrowIns(safeGetInteger(rs, "home_slow_in"));
			home.setBoxTouches(safeGetInteger(rs, "home_box_touch"));
			home.setPasses(safeGetString(rs, "home_pass_count"));
			home.setLongPasses(safeGetString(rs, "home_long_pass_count"));
			home.setFinalThirdPasses(safeGetString(rs, "home_final_third_pass_count"));
			home.setCrosses(safeGetInteger(rs, "home_cross_count"));
			home.setTackles(safeGetInteger(rs, "home_tackle_count"));
			home.setClearances(safeGetInteger(rs, "home_clear_count"));
			home.setDuels(safeGetInteger(rs, "home_duel_count"));
			home.setInterceptions(safeGetInteger(rs, "home_intercept_count"));

			GameDetailDTO.TeamSide away = new GameDetailDTO.TeamSide();
			away.setName(safeGetString(rs, "away_team_name"));
			away.setScore(awayScoreObj);
			away.setManager(safeGetString(rs, "away_manager"));
			away.setFormation(safeGetString(rs, "away_formation"));
			away.setXg(safeGetDouble(rs, "away_exp"));
			away.setInGoalXg(safeGetDouble(rs, "away_in_goal_exp"));
			away.setPossession(parsePercent(safeGetString(rs, "away_donation")));
			away.setShots(safeGetInteger(rs, "away_shoot_all"));
			away.setShotsOn(safeGetInteger(rs, "away_shoot_in"));
			away.setShotsOff(safeGetInteger(rs, "away_shoot_out"));
			away.setBlocks(safeGetInteger(rs, "away_block_shoot"));
			away.setBigChances(safeGetInteger(rs, "away_big_chance"));
			away.setCorners(safeGetInteger(rs, "away_corner"));
			away.setBoxShotsIn(safeGetInteger(rs, "away_box_shoot_in"));
			away.setBoxShotsOut(safeGetInteger(rs, "away_box_shoot_out"));
			away.setGoalPost(safeGetInteger(rs, "away_goal_post"));
			away.setHeadGoals(safeGetInteger(rs, "away_goal_head"));
			away.setSaves(safeGetInteger(rs, "away_keeper_save"));
			away.setFreeKicks(safeGetInteger(rs, "away_free_kick"));
			away.setOffsides(safeGetInteger(rs, "away_offside"));
			away.setFouls(safeGetInteger(rs, "away_foul"));
			away.setYc(safeGetInteger(rs, "away_yellow_card"));
			away.setRc(safeGetInteger(rs, "away_red_card"));
			away.setThrowIns(safeGetInteger(rs, "away_slow_in"));
			away.setBoxTouches(safeGetInteger(rs, "away_box_touch"));
			away.setPasses(safeGetString(rs, "away_pass_count"));
			away.setLongPasses(safeGetString(rs, "away_long_pass_count"));
			away.setFinalThirdPasses(safeGetString(rs, "away_final_third_pass_count"));
			away.setCrosses(safeGetInteger(rs, "away_cross_count"));
			away.setTackles(safeGetInteger(rs, "away_tackle_count"));
			away.setClearances(safeGetInteger(rs, "away_clear_count"));
			away.setDuels(safeGetInteger(rs, "away_duel_count"));
			away.setInterceptions(safeGetInteger(rs, "away_intercept_count"));

			GameDetailDTO.Venue venue = new GameDetailDTO.Venue();
			venue.setStadium(safeGetString(rs, "studium"));
			venue.setAudience(safeGetString(rs, "audience"));
			venue.setCapacity(safeGetString(rs, "capacity"));

			detail.setHome(home);
			detail.setAway(away);
			detail.setVenue(venue);

			return detail;
		}
	};

	/**
	 * 既存用：country / league + seq で取得
	 */
	public Optional<GameDetailDTO> findGameDetail(String country, String league, long seq) {
		String likeCond = country + ": " + league + "%";

		String sql = BASE_SELECT + """
				WHERE d.seq = :seq
				  AND d.data_category LIKE :likeCond
				LIMIT 1
				""";

		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("seq", seq)
				.addValue("likeCond", likeCond);

		return querySingle(sql, params);
	}

	/**
	 * 新API用：seq のみで取得
	 */
	public Optional<GameDetailDTO> findGameDetail(long seq) {
		String sql = BASE_SELECT + """
				WHERE d.seq = :seq
				LIMIT 1
				""";

		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("seq", seq);

		return querySingle(sql, params);
	}

	private Optional<GameDetailDTO> querySingle(String sql, MapSqlParameterSource params) {
		List<GameDetailDTO> list = bmJdbcTemplate.query(sql, params, GAME_DETAIL_ROW_MAPPER);
		return list.stream().findFirst();
	}
}
