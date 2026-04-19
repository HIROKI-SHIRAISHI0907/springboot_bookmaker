package dev.web.repository.bm;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
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
 *
 * @author shiraishitoshio
 */
@Repository
public class GameDetailsRepository {

	private final NamedParameterJdbcTemplate bmJdbcTemplate;

	private static final Pattern PCT_PATTERN = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s*%");
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
			return (int) Math.round(v);
		}

		Matcher numberMatcher = FIRST_NUMBER_PATTERN.matcher(text);
		if (numberMatcher.find()) {
			double v = Double.parseDouble(numberMatcher.group(1));
			return (int) Math.round(v);
		}

		return null;
	}

	private static final RowMapper<GameDetailDTO> GAME_DETAIL_ROW_MAPPER = (rs, rowNum) -> {
		Integer hsObj = (Integer) rs.getObject("home_score");
		Integer asObj = (Integer) rs.getObject("away_score");
		int hs = hsObj == null ? 0 : hsObj;
		int as = asObj == null ? 0 : asObj;

		String times = rs.getString("times");

		boolean finished = times != null &&
				(times.contains("終了")
						|| times.toUpperCase().contains("FT")
						|| times.toUpperCase().contains("AET")
						|| times.toUpperCase().contains("PEN"));

		String winner;
		if (!finished) {
			winner = "LIVE";
		} else if (hs == as) {
			winner = "DRAW";
		} else if (hs > as) {
			winner = "HOME";
		} else {
			winner = "AWAY";
		}

		Function<String, Integer> pct = (str) -> {
			if (str == null) {
				return null;
			}
			Matcher m = PCT_PATTERN.matcher(str);
			if (m.find()) {
				double v = Double.parseDouble(m.group(1));
				return (int) Math.round(v);
			}
			return null;
		};

		GameDetailDTO detail = new GameDetailDTO();
		detail.setCompetition(rs.getString("data_category") == null ? "" : rs.getString("data_category"));
		detail.setRoundNo((Integer) rs.getObject("round_no"));
		detail.setRecordedAt(rs.getString("record_time_jst"));
		detail.setWinner(winner);
		detail.setLink(rs.getString("link_maybe"));
		detail.setTimes(times);

		// home
		GameDetailDTO.TeamSide home = new GameDetailDTO.TeamSide();
		home.setName(rs.getString("home_team_name"));
		home.setScore(hs);
		home.setManager(rs.getString("home_manager"));
		home.setFormation(rs.getString("home_formation"));

		BigDecimal homeExp = (BigDecimal) rs.getObject("home_exp");
		home.setXg(homeExp == null ? null : homeExp.doubleValue());

		BigDecimal homeInGoalExp = (BigDecimal) rs.getObject("home_in_goal_exp");
		home.setInGoalXg(homeInGoalExp == null ? null : homeInGoalExp.doubleValue());

		home.setBoxShotsIn((Integer) rs.getObject("home_box_shoot_in"));
		home.setBoxShotsOut((Integer) rs.getObject("home_box_shoot_out"));
		home.setGoalPost((Integer) rs.getObject("home_goal_post"));
		home.setHeadGoals((Integer) rs.getObject("home_goal_head"));
		home.setFreeKicks((Integer) rs.getObject("home_free_kick"));
		home.setOffsides((Integer) rs.getObject("home_offside"));
		home.setFouls((Integer) rs.getObject("home_foul"));
		home.setThrowIns((Integer) rs.getObject("home_slow_in"));
		home.setBoxTouches((Integer) rs.getObject("home_box_touch"));
		home.setFinalThirdPasses(rs.getString("home_final_third_pass_count"));
		home.setCrosses(parseStatNumber(rs.getString("home_cross_count")));
		home.setTackles(parseStatNumber(rs.getString("home_tackle_count")));
		home.setClearances(parseStatNumber(rs.getString("home_clear_count")));
		home.setDuels(parseStatNumber(rs.getString("home_duel_count")));
		home.setInterceptions(parseStatNumber(rs.getString("home_intercept_count")));
		home.setPossession(pct.apply(rs.getString("home_donation")));
		home.setShots((Integer) rs.getObject("home_shoot_all"));
		home.setShotsOn((Integer) rs.getObject("home_shoot_in"));
		home.setShotsOff((Integer) rs.getObject("home_shoot_out"));
		home.setBlocks((Integer) rs.getObject("home_block_shoot"));
		home.setCorners((Integer) rs.getObject("home_corner"));
		home.setBigChances((Integer) rs.getObject("home_big_chance"));
		home.setSaves((Integer) rs.getObject("home_keeper_save"));
		home.setYc((Integer) rs.getObject("home_yellow_card"));
		home.setRc((Integer) rs.getObject("home_red_card"));
		home.setPasses(rs.getString("home_pass_count"));
		home.setLongPasses(rs.getString("home_long_pass_count"));

		// away
		GameDetailDTO.TeamSide away = new GameDetailDTO.TeamSide();
		away.setName(rs.getString("away_team_name"));
		away.setScore(as);
		away.setManager(rs.getString("away_manager"));
		away.setFormation(rs.getString("away_formation"));

		BigDecimal awayExp = (BigDecimal) rs.getObject("away_exp");
		away.setXg(awayExp == null ? null : awayExp.doubleValue());

		BigDecimal awayInGoalExp = (BigDecimal) rs.getObject("away_in_goal_exp");
		away.setInGoalXg(awayInGoalExp == null ? null : awayInGoalExp.doubleValue());

		away.setBoxShotsIn((Integer) rs.getObject("away_box_shoot_in"));
		away.setBoxShotsOut((Integer) rs.getObject("away_box_shoot_out"));
		away.setGoalPost((Integer) rs.getObject("away_goal_post"));
		away.setHeadGoals((Integer) rs.getObject("away_goal_head"));
		away.setFreeKicks((Integer) rs.getObject("away_free_kick"));
		away.setOffsides((Integer) rs.getObject("away_offside"));
		away.setFouls((Integer) rs.getObject("away_foul"));
		away.setThrowIns((Integer) rs.getObject("away_slow_in"));
		away.setBoxTouches((Integer) rs.getObject("away_box_touch"));
		away.setFinalThirdPasses(rs.getString("away_final_third_pass_count"));
		away.setCrosses(parseStatNumber(rs.getString("away_cross_count")));
		away.setTackles(parseStatNumber(rs.getString("away_tackle_count")));
		away.setClearances(parseStatNumber(rs.getString("away_clear_count")));
		away.setDuels(parseStatNumber(rs.getString("away_duel_count")));
		away.setInterceptions(parseStatNumber(rs.getString("away_intercept_count")));
		away.setPossession(pct.apply(rs.getString("away_donation")));
		away.setShots((Integer) rs.getObject("away_shoot_all"));
		away.setShotsOn((Integer) rs.getObject("away_shoot_in"));
		away.setShotsOff((Integer) rs.getObject("away_shoot_out"));
		away.setBlocks((Integer) rs.getObject("away_block_shoot"));
		away.setCorners((Integer) rs.getObject("away_corner"));
		away.setBigChances((Integer) rs.getObject("away_big_chance"));
		away.setSaves((Integer) rs.getObject("away_keeper_save"));
		away.setYc((Integer) rs.getObject("away_yellow_card"));
		away.setRc((Integer) rs.getObject("away_red_card"));
		away.setPasses(rs.getString("away_pass_count"));
		away.setLongPasses(rs.getString("away_long_pass_count"));

		// venue
		GameDetailDTO.Venue venue = new GameDetailDTO.Venue();
		venue.setStadium(rs.getString("studium"));
		venue.setAudience(rs.getString("audience"));
		venue.setCapacity(rs.getString("capacity"));

		detail.setHome(home);
		detail.setAway(away);
		detail.setVenue(venue);

		return detail;
	};

	public GameDetailsRepository(
			@Qualifier("bmJdbcTemplate") NamedParameterJdbcTemplate bmJdbcTemplate) {
		this.bmJdbcTemplate = bmJdbcTemplate;
	}

	/**
	 * 既存用：country / league + seq で取得
	 *
	 * @param country 国名
	 * @param league リーグ名
	 * @param seq public.data.seq
	 * @return 試合詳細（存在しなければ empty）
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
	 *
	 * @param seq public.data.seq
	 * @return 試合詳細（存在しなければ empty）
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
