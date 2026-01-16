// dev/web/repository/EachStatsRepository.java
package dev.web.repository.bm;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * EachStatsRepositoryクラス
 * @author shiraishitoshio
 *
 */
@Repository
public class EachStatsRepository {

	private final NamedParameterJdbcTemplate bmJdbcTemplate;
    private final NamedParameterJdbcTemplate masterJdbcTemplate;

    public EachStatsRepository(
            @Qualifier("bmJdbcTemplate") NamedParameterJdbcTemplate bmJdbcTemplate,
            @Qualifier("webMasterJdbcTemplate") NamedParameterJdbcTemplate masterJdbcTemplate
    ) {
        this.bmJdbcTemplate = bmJdbcTemplate;
        this.masterJdbcTemplate = masterJdbcTemplate;
    }

    private static final String TABLE_NAME = "each_team_score_based_feature_stats";

    // Node 側 STAT_COLUMNS と同じ
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

    /** country / league / teamSlug から日本語チーム名。見つからなければ teamSlug を返す。 */
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
                .addValue("country", country)
                .addValue("league", league)
                .addValue("link", "/team/" + teamSlug + "/%");

        List<String> list = masterJdbcTemplate.query(
                sql,
                params,
                (rs, rowNum) -> rs.getString("team")
        );

        return list.isEmpty() ? teamSlug : list.get(0);
    }

    /**
     * each_team_score_based_feature_stats から、指定チームの全行を取得。
     * 返り値は "列名 -> 値" の Map。
     */
    public List<Map<String, Object>> findStatsRows(String country, String league, String teamJa) {

        // SELECT s.situation, s.score, ... の部分を動的に生成
        List<String> cols = new ArrayList<>();
        cols.add("s.situation");
        cols.add("s.score");
        cols.add("s.country");
        cols.add("s.league");
        cols.add("s.team");
        for (String c : STAT_COLUMNS) {
            cols.add("s." + c);
        }
        String selectCols = String.join(", ", cols);

        String sql = """
            SELECT %s
            FROM %s s
            WHERE s.country = :country
              AND s.league  = :league
              AND s.team    = :team
            """.formatted(selectCols, TABLE_NAME);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("country", country)
                .addValue("league", league)
                .addValue("team", teamJa);

        // List<Map<String,Object>> で素の行をそのまま返す
        return bmJdbcTemplate.queryForList(sql, params);
    }

    public String[] getStatColumns() {
        return STAT_COLUMNS;
    }
}
