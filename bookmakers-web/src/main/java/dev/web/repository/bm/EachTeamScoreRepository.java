package dev.web.repository.bm;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.web.api.bm_w015.EachTeamScoreResponseDTO;

/**
 * each_team_score_based_feature_stats 用 Repository
 */
@Repository
public class EachTeamScoreRepository {

    private final NamedParameterJdbcTemplate bmJdbcTemplate;
    private final NamedParameterJdbcTemplate masterJdbcTemplate;

    public EachTeamScoreRepository(
            @Qualifier("bmJdbcTemplate") NamedParameterJdbcTemplate bmJdbcTemplate,
            @Qualifier("webMasterJdbcTemplate") NamedParameterJdbcTemplate masterJdbcTemplate
    ) {
        this.bmJdbcTemplate = bmJdbcTemplate;
        this.masterJdbcTemplate = masterJdbcTemplate;
    }

    private static final String TABLE_NAME = "each_team_score_based_feature_stats";

    private static final String[] STAT_COLUMNS = {
        "home_exp_stat",
        "away_exp_stat",
        "home_in_goal_exp_stat",
        "away_in_goal_exp_stat",
        "home_donation_stat",
        "away_donation_stat",
        "home_shoot_all_stat",
        "away_shoot_all_stat",
        "home_shoot_in_stat",
        "away_shoot_in_stat",
        "home_shoot_out_stat",
        "away_shoot_out_stat",
        "home_block_shoot_stat",
        "away_block_shoot_stat",
        "home_big_chance_stat",
        "away_big_chance_stat",
        "home_corner_stat",
        "away_corner_stat",
        "home_box_shoot_in_stat",
        "away_box_shoot_in_stat",
        "home_box_shoot_out_stat",
        "away_box_shoot_out_stat",
        "home_goal_post_stat",
        "away_goal_post_stat",
        "home_goal_head_stat",
        "away_goal_head_stat",
        "home_keeper_save_stat",
        "away_keeper_save_stat",
        "home_free_kick_stat",
        "away_free_kick_stat",
        "home_offside_stat",
        "away_offside_stat",
        "home_foul_stat",
        "away_foul_stat",
        "home_yellow_card_stat",
        "away_yellow_card_stat",
        "home_red_card_stat",
        "away_red_card_stat",
        "home_slow_in_stat",
        "away_slow_in_stat",
        "home_box_touch_stat",
        "away_box_touch_stat",
        "home_pass_count_stat",
        "away_pass_count_stat",
        "home_long_pass_count_stat",
        "away_long_pass_count_stat",
        "home_final_third_pass_count_stat",
        "away_final_third_pass_count_stat",
        "home_cross_count_stat",
        "away_cross_count_stat",
        "home_tackle_count_stat",
        "away_tackle_count_stat",
        "home_clear_count_stat",
        "away_clear_count_stat",
        "home_duel_count_stat",
        "away_duel_count_stat",
        "home_intercept_count_stat",
        "away_intercept_count_stat"
    };

    /**
     * team slug から日本語チーム名を取得。
     * 見つからない場合はそのまま teamSlug を返す。
     */
    public String findTeamName(String country, String league, String teamSlug) {
        String sql = """
            SELECT team
            FROM country_league_master
            WHERE country = :country
              AND league  = :league
              AND link LIKE :link
            LIMIT 1
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("country", nvl(country))
                .addValue("league", nvl(league))
                .addValue("link", "/team/" + nvl(teamSlug) + "/%");

        List<String> list = masterJdbcTemplate.query(
                sql,
                params,
                (rs, rowNum) -> rs.getString("team")
        );

        return list.isEmpty() ? nvl(teamSlug) : nvl(list.get(0));
    }

    /**
     * /api/each-team-score 用
     */
    public List<EachTeamScoreResponseDTO> findEachTeamScore(String country, String league, String teamSlug) {

        String teamJa = findTeamName(country, league, teamSlug);

        String sql = """
            SELECT %s
            FROM %s s
            WHERE s.country = :country
              AND s.league  = :league
              AND s.team    = :team
            ORDER BY s.id
            """.formatted(buildSelectColumns(), TABLE_NAME);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("country", nvl(country))
                .addValue("league", nvl(league))
                .addValue("team", nvl(teamJa));

        return bmJdbcTemplate.query(sql, params, (rs, rowNum) -> {
            EachTeamScoreResponseDTO dto = new EachTeamScoreResponseDTO();

            dto.setId(nvl(rs.getString("id")));
            dto.setSituation(nvl(rs.getString("situation")));
            dto.setScore(nvl(rs.getString("score")));
            dto.setCountry(nvl(rs.getString("country")));
            dto.setLeague(nvl(rs.getString("league")));
            dto.setTeam(nvl(rs.getString("team")));

            dto.setHomeExpStat(EachTeamScoreResponseDTO.StatSummaryDTO.fromRaw(rs.getString("home_exp_stat")));
            dto.setAwayExpStat(EachTeamScoreResponseDTO.StatSummaryDTO.fromRaw(rs.getString("away_exp_stat")));

            dto.setHomeInGoalExpStat(EachTeamScoreResponseDTO.StatSummaryDTO.fromRaw(rs.getString("home_in_goal_exp_stat")));
            dto.setAwayInGoalExpStat(EachTeamScoreResponseDTO.StatSummaryDTO.fromRaw(rs.getString("away_in_goal_exp_stat")));

            dto.setHomeDonationStat(EachTeamScoreResponseDTO.StatSummaryDTO.fromRaw(rs.getString("home_donation_stat")));
            dto.setAwayDonationStat(EachTeamScoreResponseDTO.StatSummaryDTO.fromRaw(rs.getString("away_donation_stat")));

            dto.setHomeShootAllStat(EachTeamScoreResponseDTO.StatSummaryDTO.fromRaw(rs.getString("home_shoot_all_stat")));
            dto.setAwayShootAllStat(EachTeamScoreResponseDTO.StatSummaryDTO.fromRaw(rs.getString("away_shoot_all_stat")));

            dto.setHomeShootInStat(EachTeamScoreResponseDTO.StatSummaryDTO.fromRaw(rs.getString("home_shoot_in_stat")));
            dto.setAwayShootInStat(EachTeamScoreResponseDTO.StatSummaryDTO.fromRaw(rs.getString("away_shoot_in_stat")));

            dto.setHomeShootOutStat(EachTeamScoreResponseDTO.StatSummaryDTO.fromRaw(rs.getString("home_shoot_out_stat")));
            dto.setAwayShootOutStat(EachTeamScoreResponseDTO.StatSummaryDTO.fromRaw(rs.getString("away_shoot_out_stat")));

            dto.setHomeBlockShootStat(EachTeamScoreResponseDTO.StatSummaryDTO.fromRaw(rs.getString("home_block_shoot_stat")));
            dto.setAwayBlockShootStat(EachTeamScoreResponseDTO.StatSummaryDTO.fromRaw(rs.getString("away_block_shoot_stat")));

            dto.setHomeBigChanceStat(EachTeamScoreResponseDTO.StatSummaryDTO.fromRaw(rs.getString("home_big_chance_stat")));
            dto.setAwayBigChanceStat(EachTeamScoreResponseDTO.StatSummaryDTO.fromRaw(rs.getString("away_big_chance_stat")));

            dto.setHomeCornerStat(EachTeamScoreResponseDTO.StatSummaryDTO.fromRaw(rs.getString("home_corner_stat")));
            dto.setAwayCornerStat(EachTeamScoreResponseDTO.StatSummaryDTO.fromRaw(rs.getString("away_corner_stat")));

            dto.setHomeBoxShootInStat(EachTeamScoreResponseDTO.StatSummaryDTO.fromRaw(rs.getString("home_box_shoot_in_stat")));
            dto.setAwayBoxShootInStat(EachTeamScoreResponseDTO.StatSummaryDTO.fromRaw(rs.getString("away_box_shoot_in_stat")));

            dto.setHomeBoxShootOutStat(EachTeamScoreResponseDTO.StatSummaryDTO.fromRaw(rs.getString("home_box_shoot_out_stat")));
            dto.setAwayBoxShootOutStat(EachTeamScoreResponseDTO.StatSummaryDTO.fromRaw(rs.getString("away_box_shoot_out_stat")));

            dto.setHomeGoalPostStat(EachTeamScoreResponseDTO.StatSummaryDTO.fromRaw(rs.getString("home_goal_post_stat")));
            dto.setAwayGoalPostStat(EachTeamScoreResponseDTO.StatSummaryDTO.fromRaw(rs.getString("away_goal_post_stat")));

            dto.setHomeGoalHeadStat(EachTeamScoreResponseDTO.StatSummaryDTO.fromRaw(rs.getString("home_goal_head_stat")));
            dto.setAwayGoalHeadStat(EachTeamScoreResponseDTO.StatSummaryDTO.fromRaw(rs.getString("away_goal_head_stat")));

            dto.setHomeKeeperSaveStat(EachTeamScoreResponseDTO.StatSummaryDTO.fromRaw(rs.getString("home_keeper_save_stat")));
            dto.setAwayKeeperSaveStat(EachTeamScoreResponseDTO.StatSummaryDTO.fromRaw(rs.getString("away_keeper_save_stat")));

            dto.setHomeFreeKickStat(EachTeamScoreResponseDTO.StatSummaryDTO.fromRaw(rs.getString("home_free_kick_stat")));
            dto.setAwayFreeKickStat(EachTeamScoreResponseDTO.StatSummaryDTO.fromRaw(rs.getString("away_free_kick_stat")));

            dto.setHomeOffsideStat(EachTeamScoreResponseDTO.StatSummaryDTO.fromRaw(rs.getString("home_offside_stat")));
            dto.setAwayOffsideStat(EachTeamScoreResponseDTO.StatSummaryDTO.fromRaw(rs.getString("away_offside_stat")));

            dto.setHomeFoulStat(EachTeamScoreResponseDTO.StatSummaryDTO.fromRaw(rs.getString("home_foul_stat")));
            dto.setAwayFoulStat(EachTeamScoreResponseDTO.StatSummaryDTO.fromRaw(rs.getString("away_foul_stat")));

            dto.setHomeYellowCardStat(EachTeamScoreResponseDTO.StatSummaryDTO.fromRaw(rs.getString("home_yellow_card_stat")));
            dto.setAwayYellowCardStat(EachTeamScoreResponseDTO.StatSummaryDTO.fromRaw(rs.getString("away_yellow_card_stat")));

            dto.setHomeRedCardStat(EachTeamScoreResponseDTO.StatSummaryDTO.fromRaw(rs.getString("home_red_card_stat")));
            dto.setAwayRedCardStat(EachTeamScoreResponseDTO.StatSummaryDTO.fromRaw(rs.getString("away_red_card_stat")));

            dto.setHomeSlowInStat(EachTeamScoreResponseDTO.StatSummaryDTO.fromRaw(rs.getString("home_slow_in_stat")));
            dto.setAwaySlowInStat(EachTeamScoreResponseDTO.StatSummaryDTO.fromRaw(rs.getString("away_slow_in_stat")));

            dto.setHomeBoxTouchStat(EachTeamScoreResponseDTO.StatSummaryDTO.fromRaw(rs.getString("home_box_touch_stat")));
            dto.setAwayBoxTouchStat(EachTeamScoreResponseDTO.StatSummaryDTO.fromRaw(rs.getString("away_box_touch_stat")));

            dto.setHomePassCountStat(EachTeamScoreResponseDTO.StatSummaryDTO.fromRaw(rs.getString("home_pass_count_stat")));
            dto.setAwayPassCountStat(EachTeamScoreResponseDTO.StatSummaryDTO.fromRaw(rs.getString("away_pass_count_stat")));

            dto.setHomeLongPassCountStat(EachTeamScoreResponseDTO.StatSummaryDTO.fromRaw(rs.getString("home_long_pass_count_stat")));
            dto.setAwayLongPassCountStat(EachTeamScoreResponseDTO.StatSummaryDTO.fromRaw(rs.getString("away_long_pass_count_stat")));

            dto.setHomeFinalThirdPassCountStat(EachTeamScoreResponseDTO.StatSummaryDTO.fromRaw(rs.getString("home_final_third_pass_count_stat")));
            dto.setAwayFinalThirdPassCountStat(EachTeamScoreResponseDTO.StatSummaryDTO.fromRaw(rs.getString("away_final_third_pass_count_stat")));

            dto.setHomeCrossCountStat(EachTeamScoreResponseDTO.StatSummaryDTO.fromRaw(rs.getString("home_cross_count_stat")));
            dto.setAwayCrossCountStat(EachTeamScoreResponseDTO.StatSummaryDTO.fromRaw(rs.getString("away_cross_count_stat")));

            dto.setHomeTackleCountStat(EachTeamScoreResponseDTO.StatSummaryDTO.fromRaw(rs.getString("home_tackle_count_stat")));
            dto.setAwayTackleCountStat(EachTeamScoreResponseDTO.StatSummaryDTO.fromRaw(rs.getString("away_tackle_count_stat")));

            dto.setHomeClearCountStat(EachTeamScoreResponseDTO.StatSummaryDTO.fromRaw(rs.getString("home_clear_count_stat")));
            dto.setAwayClearCountStat(EachTeamScoreResponseDTO.StatSummaryDTO.fromRaw(rs.getString("away_clear_count_stat")));

            dto.setHomeDuelCountStat(EachTeamScoreResponseDTO.StatSummaryDTO.fromRaw(rs.getString("home_duel_count_stat")));
            dto.setAwayDuelCountStat(EachTeamScoreResponseDTO.StatSummaryDTO.fromRaw(rs.getString("away_duel_count_stat")));

            dto.setHomeInterceptCountStat(EachTeamScoreResponseDTO.StatSummaryDTO.fromRaw(rs.getString("home_intercept_count_stat")));
            dto.setAwayInterceptCountStat(EachTeamScoreResponseDTO.StatSummaryDTO.fromRaw(rs.getString("away_intercept_count_stat")));

            dto.setLogicFlg(nvl(rs.getString("logic_flg")));
            dto.setRegisterId(nvl(rs.getString("register_id")));

            Timestamp registerTime = rs.getTimestamp("register_time");
            if (registerTime != null) {
                dto.setRegisterTime(registerTime.toLocalDateTime());
            }

            dto.setUpdateId(nvl(rs.getString("update_id")));

            Timestamp updateTime = rs.getTimestamp("update_time");
            if (updateTime != null) {
                dto.setUpdateTime(updateTime.toLocalDateTime());
            }

            return dto;
        });
    }

    /**
     * 既存用途を残したい場合用。
     * 列名 -> 値 の素の行を返す。
     */
    public List<Map<String, Object>> findStatsRows(String country, String league, String teamJa) {
        String sql = """
            SELECT %s
            FROM %s s
            WHERE s.country = :country
              AND s.league  = :league
              AND s.team    = :team
            ORDER BY s.id
            """.formatted(buildSelectColumns(), TABLE_NAME);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("country", nvl(country))
                .addValue("league", nvl(league))
                .addValue("team", nvl(teamJa));

        return bmJdbcTemplate.queryForList(sql, params);
    }

    public String[] getStatColumns() {
        return STAT_COLUMNS.clone();
    }

    private String buildSelectColumns() {
        List<String> cols = new ArrayList<>();
        cols.add("s.id");
        cols.add("s.situation");
        cols.add("s.score");
        cols.add("s.country");
        cols.add("s.league");
        cols.add("s.team");

        for (String c : STAT_COLUMNS) {
            cols.add("s." + c);
        }

        cols.add("s.logic_flg");
        cols.add("s.register_id");
        cols.add("s.register_time");
        cols.add("s.update_id");
        cols.add("s.update_time");

        return String.join(", ", cols);
    }

    private String nvl(String value) {
        return value == null ? "" : value.trim();
    }
}