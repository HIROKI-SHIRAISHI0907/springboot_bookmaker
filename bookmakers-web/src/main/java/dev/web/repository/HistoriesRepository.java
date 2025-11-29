package dev.web.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.web.api.bm_w002.HistoryDetailResponseDTO;
import dev.web.api.bm_w002.HistoryResponseDTO;
import lombok.RequiredArgsConstructor;

/**
 * HistoriesRepositoryクラス
 * @author shiraishitoshio
 *
 */
@Repository
@RequiredArgsConstructor
public class HistoriesRepository {

	@Qualifier("bmJdbcTemplate")
    private final NamedParameterJdbcTemplate bmJdbcTemplate;

    @Qualifier("masterJdbcTemplate")
    private final NamedParameterJdbcTemplate masterJdbcTemplate;

    // --------------------------------------------------------
    // 一覧: GET /api/:country/:league/:team/history（チーム取得）
    // --------------------------------------------------------
    public String findTeamJa(String country, String league, String teamSlug) {
        String sql = """
            SELECT team
            FROM country_league_master
            WHERE country = :country
              AND league  = :league
              AND link LIKE :link
            LIMIT 1
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("country", country)
                .addValue("league", league)
                .addValue("link", "/team/" + teamSlug + "/%");

        List<String> results = masterJdbcTemplate.query(
                sql,
                params,
                (rs, rowNum) -> rs.getString("team")
        );

        return results.isEmpty() ? teamSlug : results.get(0);
    }

    // --------------------------------------------------------
    // 一覧: GET /api/:country/:league/:team/history
    // --------------------------------------------------------
    public List<HistoryResponseDTO> findPastMatches(String country, String league, String teamJa) {

        String likeCond = country + ": " + league + "%";

        String sql = """
            WITH finished AS (
              SELECT
                d.seq::bigint AS seq_big,
                d.data_category,
                d.home_team_name,
                d.away_team_name,
                NULLIF(TRIM(d.times), '') AS times,
                NULLIF(TRIM(d.home_score), '')::int AS home_score,
                NULLIF(TRIM(d.away_score), '')::int AS away_score,
                (d.record_time AT TIME ZONE 'Asia/Tokyo')::timestamp AS record_time_jst_ts,
                to_char((d.record_time AT TIME ZONE 'Asia/Tokyo')::date, 'YYYY-MM-DD') AS jst_date,
                NULLIF(TRIM(d.judge), '') AS link_maybe
              FROM public.data d
              WHERE d.home_team_name IS NOT NULL
                AND d.away_team_name IS NOT NULL
                AND d.times ILIKE '%終了%'
                AND d.data_category LIKE ?
                AND (d.home_team_name = ? OR d.away_team_name = ?)
            ),
            ranked AS (
              SELECT
                seq_big,
                data_category,
                home_team_name,
                away_team_name,
                times,
                home_score,
                away_score,
                record_time_jst_ts,
                jst_date,
                link_maybe,
                ROW_NUMBER() OVER (
                  PARTITION BY jst_date, home_team_name, away_team_name
                  ORDER BY seq_big DESC
                ) AS rn
              FROM finished
            )
            SELECT
              r.seq_big::text AS seq,
              to_char(r.record_time_jst_ts, 'YYYY-MM-DD"T"HH24:MI:SS') AS match_time,
              r.data_category,
              CASE
                WHEN regexp_match(r.data_category, '(ラウンド|Round)\\s*([0-9]+)') IS NULL THEN NULL
                ELSE CAST( (regexp_match(r.data_category, '(ラウンド|Round)\\s*([0-9]+)'))[2] AS INT )
              END AS round_no,
              r.home_team_name,
              r.away_team_name,
              r.home_score,
              r.away_score,
              r.link_maybe
            FROM ranked r
            WHERE r.rn = 1
            ORDER BY r.record_time_jst_ts DESC
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("likeCond", likeCond)
                .addValue("teamJa", teamJa);

        RowMapper<HistoryResponseDTO> rowMapper = (rs, rowNum) -> {
            HistoryResponseDTO m = new HistoryResponseDTO();
            m.setSeq(Long.parseLong(rs.getString("seq")));
            m.setMatchTime(rs.getString("match_time"));
            m.setGameTeamCategory(rs.getString("data_category") == null ? "" : rs.getString("data_category"));
            m.setHomeTeam(rs.getString("home_team_name"));
            m.setAwayTeam(rs.getString("away_team_name"));
            int hs = rs.getObject("home_score") == null ? 0 : rs.getInt("home_score");
            int as = rs.getObject("away_score") == null ? 0 : rs.getInt("away_score");
            m.setHomeScore(hs);
            m.setAwayScore(as);
            Integer roundNo = (Integer) rs.getObject("round_no");
            m.setRoundNo(roundNo);
            m.setLink(rs.getString("link_maybe"));
            return m;
        };

        return bmJdbcTemplate.query(sql, params, rowMapper);
    }

    // --------------------------------------------------------
    // 詳細: GET /api/:country/:league/:team/history/:seq
    // --------------------------------------------------------
    public Optional<HistoryDetailResponseDTO> findHistoryDetail(String country, String league, long seq) {

        String likeCond = country + ": " + league + "%";

        String sql = """
          SELECT
            d.seq::text AS seq,
            d.data_category,
            CASE
              WHEN regexp_match(d.data_category, '(ラウンド|Round)\\s*([0-9]+)') IS NULL THEN NULL
              ELSE CAST( (regexp_match(d.data_category, '(ラウンド|Round)\\s*([0-9]+)'))[2] AS INT )
            END AS round_no,
            to_char((d.record_time AT TIME ZONE 'Asia/Tokyo'), 'YYYY-MM-DD"T"HH24:MI:SS') AS record_time_jst,
            d.home_team_name,
            d.away_team_name,
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
            NULLIF(TRIM(d.judge), '') AS link_maybe
          FROM public.data d
          WHERE d.seq = :seq::bigint
            AND d.times ILIKE '%終了%'
            AND d.data_category LIKE :likeCond
          LIMIT 1
          """;

        // "57%" → 57 にするための正規表現
        Pattern pctPattern = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s*%");

        RowMapper<HistoryDetailResponseDTO> rowMapper = (rs, rowNum) -> {
            int hs = rs.getObject("home_score") == null ? 0 : rs.getInt("home_score");
            int as = rs.getObject("away_score") == null ? 0 : rs.getInt("away_score");

            String winner;
            if (hs == as) {
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

            HistoryDetailResponseDTO detail = new HistoryDetailResponseDTO();
            detail.setCompetition(rs.getString("data_category"));
            detail.setRoundNo((Integer) rs.getObject("round_no"));
            detail.setRecordedAt(rs.getString("record_time_jst"));
            detail.setWinner(winner);
            detail.setLink(rs.getString("link_maybe"));

            // --- home ---
            HistoryDetailResponseDTO.TeamSide home = new HistoryDetailResponseDTO.TeamSide();
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
            HistoryDetailResponseDTO.TeamSide away = new HistoryDetailResponseDTO.TeamSide();
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
            HistoryDetailResponseDTO.Venue venue = new HistoryDetailResponseDTO.Venue();
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

        List<HistoryDetailResponseDTO> list =
        		bmJdbcTemplate.query(sql, params, rowMapper);

        if (list.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(list.get(0));
    }

}
