package dev.web.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.web.api.bm_w005.GameDetailDTO;

/**
 * GameDetailRepositoryクラス
 *
 * /api/{country}/{league}/{team}/games/detail/{seq}
 * 用の DB アクセス。
 *
 * public.data から 1 試合分のスタッツを取得する。
 *
 * @author shiraishitoshio
 */
@Repository
public class GameDetailsRepository {

    private final NamedParameterJdbcTemplate namedJdbcTemplate;

    public GameDetailsRepository(NamedParameterJdbcTemplate namedJdbcTemplate) {
        this.namedJdbcTemplate = namedJdbcTemplate;
    }

    /**
     * 試合詳細を取得（LIVE / FINISHED 問わず）
     *
     * @param country 国名
     * @param league リーグ名
     * @param seq public.data.seq
     * @return 試合詳細（存在しなければ empty）
     */
    public Optional<GameDetailDTO> findGameDetail(String country, String league, long seq) {

        String likeCond = country + ": " + league + "%";

        String sql = """
          SELECT
            d.data_category,
            CASE
              WHEN regexp_match(d.data_category, '(ラウンド|Round)\\s*([0-9]+)') IS NULL THEN NULL
              ELSE CAST((regexp_match(d.data_category, '(ラウンド|Round)\\s*([0-9]+)'))[2] AS INT)
            END AS round_no,
            to_char((d.record_time AT TIME ZONE 'Asia/Tokyo'), 'YYYY-MM-DD"T"HH24:MI:SS') AS record_time_jst,
            d.home_team_name, d.away_team_name,
            NULLIF(TRIM(d.home_score), '')::int AS home_score,
            NULLIF(TRIM(d.away_score), '')::int AS away_score,
            NULLIF(TRIM(d.home_exp), '')::numeric AS home_exp,
            NULLIF(TRIM(d.away_exp), '')::numeric AS away_exp,
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
            NULLIF(TRIM(d.home_corner), '')::int AS home_corner,
            NULLIF(TRIM(d.away_corner), '')::int AS away_corner,
            NULLIF(TRIM(d.home_big_chance), '')::int AS home_big_chance,
            NULLIF(TRIM(d.away_big_chance), '')::int AS away_big_chance,
            NULLIF(TRIM(d.home_keeper_save), '')::int AS home_keeper_save,
            NULLIF(TRIM(d.away_keeper_save), '')::int AS away_keeper_save,
            NULLIF(TRIM(d.home_yellow_card), '')::int AS home_yellow_card,
            NULLIF(TRIM(d.away_yellow_card), '')::int AS away_yellow_card,
            NULLIF(TRIM(d.home_red_card), '')::int AS home_red_card,
            NULLIF(TRIM(d.away_red_card), '')::int AS away_red_card,
            NULLIF(TRIM(d.home_pass_count), '') AS home_pass_count,
            NULLIF(TRIM(d.away_pass_count), '') AS away_pass_count,
            NULLIF(TRIM(d.home_long_pass_count), '') AS home_long_pass_count,
            NULLIF(TRIM(d.away_long_pass_count), '') AS away_long_pass_count,
            NULLIF(TRIM(d.home_manager), '') AS home_manager,
            NULLIF(TRIM(d.away_manager), '') AS away_manager,
            NULLIF(TRIM(d.home_formation), '') AS home_formation,
            NULLIF(TRIM(d.away_formation), '') AS away_formation,
            NULLIF(TRIM(d.studium), '') AS studium,
            NULLIF(TRIM(d.capacity), '') AS capacity,
            NULLIF(TRIM(d.audience), '') AS audience,
            NULLIF(TRIM(d.judge), '') AS link_maybe,
            NULLIF(TRIM(d.times), '') AS times
          FROM public.data d
          WHERE d.seq = :seq::bigint
            AND d.data_category LIKE :likeCond
          LIMIT 1
          """;

        // "57%" -> 57 にするための正規表現
        Pattern pctPattern = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s*%");

        RowMapper<GameDetailDTO> rowMapper = (rs, rowNum) -> {
            int hs = rs.getObject("home_score") == null ? 0 : rs.getInt("home_score");
            int as = rs.getObject("away_score") == null ? 0 : rs.getInt("away_score");
            String times = rs.getString("times");

            boolean finished = times != null && times.contains("終了");
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

            // helper: "57%" -> 57
            java.util.function.Function<String, Integer> pct = (str) -> {
                if (str == null) return null;
                Matcher m = pctPattern.matcher(str);
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

            // --- home ---
            GameDetailDTO.TeamSide home = new GameDetailDTO.TeamSide();
            home.setName(rs.getString("home_team_name"));
            home.setScore(hs);
            home.setManager(rs.getString("home_manager"));
            home.setFormation(rs.getString("home_formation"));

            BigDecimal homeExp = (BigDecimal) rs.getObject("home_exp");
            home.setXg(homeExp == null ? null : homeExp.doubleValue());
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

            // --- away ---
            GameDetailDTO.TeamSide away = new GameDetailDTO.TeamSide();
            away.setName(rs.getString("away_team_name"));
            away.setScore(as);
            away.setManager(rs.getString("away_manager"));
            away.setFormation(rs.getString("away_formation"));

            BigDecimal awayExp = (BigDecimal) rs.getObject("away_exp");
            away.setXg(awayExp == null ? null : awayExp.doubleValue());
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

            // --- venue ---
            GameDetailDTO.Venue venue = new GameDetailDTO.Venue();
            venue.setStadium(rs.getString("studium"));
            venue.setAudience(rs.getString("audience"));
            venue.setCapacity(rs.getString("capacity"));

            detail.setHome(home);
            detail.setAway(away);
            detail.setVenue(venue);

            return detail;
        };

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("seq", seq)
                .addValue("likeCond", likeCond);

        List<GameDetailDTO> list = namedJdbcTemplate.query(sql, params, rowMapper);
        if (list.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(list.get(0));
    }
}
