package dev.web.repository.bm;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Repository
@Slf4j
public class LeaguesRepository {

    private final NamedParameterJdbcTemplate masterJdbcTemplate;

    public LeaguesRepository(
            @Qualifier("webMasterJdbcTemplate") NamedParameterJdbcTemplate masterJdbcTemplate) {
        this.masterJdbcTemplate = masterJdbcTemplate;
    }

    @Data
    public static class LeagueCountRow {
        public String country;
        public String leagueGroup;
        public String subLeague;
        public String leagueFull;
        public String seasonYear;
        public String startSeasonDate;
        public String endSeasonDate;
        public Long teamCount;
        public Long variantCount;
        public String path;
    }

    @Data
    public static class TeamRow {
        public Integer id;
        public String country;
        public String league;
        public String subLeague;
        public String team;
        public String link;
    }

    @Data
    public static class LeagueSeasonRow {
        public String seasonYear;
        public String startSeasonDate;
        public String endSeasonDate;
        public String path;
    }

    private static final Pattern TEAM_LINK_PATTERN = Pattern.compile(
            "^/team/([^/]+)/([^/]+)",
            Pattern.CASE_INSENSITIVE);

    public String toPath(String s) {
        if (s == null) {
            return "";
        }
        String trimmed = s.trim().replaceAll("\\s+", " ");
        String enc = URLEncoder.encode(trimmed, StandardCharsets.UTF_8);
        return enc.replace("+", "%20");
    }

    public String[] parseTeamLink(String link) {
        if (link == null) {
            return new String[] { "", "" };
        }
        Matcher m = TEAM_LINK_PATTERN.matcher(link);
        if (!m.find()) {
            return new String[] { "", "" };
        }
        return new String[] { m.group(1), m.group(2) };
    }

    /**
     * country → leagueGroup → subLeague のメニュー構築用データを返す。
     *
     * ポイント:
     * - ここでは admin/favorite の絞り込みはしない
     * - season master に行がなくても返す（LEFT JOIN）
     * - season 情報が null のリーグは Service 側で「シーズン終了」扱いにできる
     * - sub_league は補助情報。主軸リーグ名は a.league をそのまま使う
     */
    public List<LeagueCountRow> findLeagueCounts() {
        String sql = """
                WITH latest_season AS (
                    SELECT
                        t.country,
                        t.league,
                        t.season_year,
                        t.start_season_date,
                        t.end_season_date,
                        t.path
                    FROM (
                        SELECT
                            s.country,
                            s.league,
                            s.season_year,
                            s.start_season_date,
                            s.end_season_date,
                            s.path,
                            ROW_NUMBER() OVER (
                                PARTITION BY s.country, s.league
                                ORDER BY
                                    CASE WHEN NULLIF(TRIM(s.season_year), '') IS NULL THEN 1 ELSE 0 END,
                                    s.season_year DESC NULLS LAST,
                                    s.end_season_date DESC NULLS LAST,
                                    s.start_season_date DESC NULLS LAST
                            ) AS rn
                        FROM country_league_season_master s
                        WHERE s.valid_flg = '0'
                          AND s.del_flg   = '0'
                    ) t
                    WHERE t.rn = 1
                ),
                team_base AS (
                    SELECT
                        a.country,
                        a.league AS league_group,
                        a.league AS league_full,
                        NULLIF(TRIM(c.sub_league), '') AS sub_league,
                        ls.path AS routing_path,
                        ls.season_year,
                        ls.start_season_date,
                        ls.end_season_date,
                        c.team
                    FROM all_league_scrape_master a
                    LEFT JOIN latest_season ls
                      ON ls.country = a.country
                     AND ls.league  = a.league
                    LEFT JOIN country_league_master c
                      ON c.country = a.country
                     AND c.league  = a.league
                     AND c.del_flg = '0'
                    WHERE COALESCE(a.del_flg, '0') = '0'
                ),
                sub_league_agg AS (
                    SELECT
                        country,
                        league_group,
                        league_full,
                        sub_league,
                        MIN(routing_path) AS path,
                        MAX(season_year) AS season_year,
                        MIN(start_season_date) AS start_season_date,
                        MAX(end_season_date) AS end_season_date,
                        COUNT(DISTINCT team) AS team_count
                    FROM team_base
                    GROUP BY
                        country,
                        league_group,
                        league_full,
                        sub_league
                )
                SELECT
                    country,
                    league_group AS league_group,
                    sub_league AS sub_league,
                    league_full,
                    season_year,
                    start_season_date,
                    end_season_date,
                    team_count,
                    COUNT(*) OVER (PARTITION BY country, league_group) AS variant_count,
                    path
                FROM sub_league_agg
                ORDER BY
                    country,
                    league_group,
                    CASE WHEN sub_league IS NULL THEN 0 ELSE 1 END,
                    sub_league
                """;

        return masterJdbcTemplate.query(
                sql,
                new BeanPropertyRowMapper<>(LeagueCountRow.class));
    }

    /** 国＋リーグのチーム一覧 */
    public List<TeamRow> findTeamsInLeague(String country, String league) {
        String sql = """
                SELECT id, country, league, sub_league, team, link
                FROM country_league_master
                WHERE country = :country
                  AND league  = :league
                  AND del_flg = '0'
                ORDER BY team
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("country", country)
                .addValue("league", league);

        return masterJdbcTemplate.query(
                sql,
                params,
                new BeanPropertyRowMapper<>(TeamRow.class));
    }

    /** 国＋リーグのチーム一覧(英語slug) */
    public List<TeamRow> findTeamsInLeagueOnSlug(String country, String league) {
        String sql = """
                SELECT clm.id, clm.country, clm.league, clm.sub_league, clm.team, clm.link
                FROM country_league_master clm
                INNER JOIN country_league_season_master clsm
                  ON clm.country = clsm.country
                 AND clm.league  = clsm.league
                WHERE clsm.path = :path
                  AND clsm.valid_flg = '0'
                  AND clsm.del_flg   = '0'
                  AND clm.del_flg    = '0'
                ORDER BY clm.team
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("path", "/soccer/" + country + "/" + league + "/");

        return masterJdbcTemplate.query(
                sql,
                params,
                new BeanPropertyRowMapper<>(TeamRow.class));
    }

    /** 国＋リーグのチーム一覧(英語slug)+サブリーグ付き */
    public List<TeamRow> findTeamsInLeagueOnSlug(String country, String league, String subLeague) {
        String normalizedSubLeague = normalizeSubLeague(subLeague);

        StringBuilder sql = new StringBuilder("""
                SELECT clm.id, clm.country, clm.league, clm.sub_league, clm.team, clm.link
                FROM country_league_master clm
                INNER JOIN country_league_season_master clsm
                  ON clm.country = clsm.country
                 AND clm.league  = clsm.league
                WHERE clsm.path = :path
                  AND clsm.valid_flg = '0'
                  AND clsm.del_flg   = '0'
                  AND clm.del_flg    = '0'
                """);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("path", "/soccer/" + country + "/" + league + "/");

        if (normalizedSubLeague != null) {
            sql.append("""
                      AND NULLIF(TRIM(clm.sub_league), '') = :subLeague
                    """);
            params.addValue("subLeague", normalizedSubLeague);
        }

        sql.append("""
                ORDER BY clm.team
                """);

        return masterJdbcTemplate.query(
                sql.toString(),
                params,
                new BeanPropertyRowMapper<>(TeamRow.class));
    }

    public LeagueSeasonRow findLeagueSeasonBySlug(String country, String league) {
        String sql = """
                SELECT
                    season_year,
                    start_season_date,
                    end_season_date,
                    path
                FROM country_league_season_master
                WHERE path = :path
                  AND valid_flg = '0'
                  AND del_flg   = '0'
                ORDER BY season_year DESC NULLS LAST, end_season_date DESC NULLS LAST
                LIMIT 1
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("path", "/soccer/" + country + "/" + league + "/");

        List<LeagueSeasonRow> list = masterJdbcTemplate.query(
                sql,
                params,
                new BeanPropertyRowMapper<>(LeagueSeasonRow.class));

        return list.isEmpty() ? null : list.get(0);
    }

    private String normalizeSubLeague(String subLeague) {
        if (subLeague == null) {
            return null;
        }
        String s = subLeague.trim();
        if (s.isEmpty()) {
            return null;
        }
        if ("未設定".equals(s)) {
            return null;
        }
        return s;
    }

    public TeamRow findTeamDetailByTeamAndHash(String teamEnglish, String teamHash) {
        String sql = """
                SELECT id, country, league, sub_league, team, link
                FROM country_league_master
                WHERE link = :link
                  AND del_flg = '0'
                LIMIT 1
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("link", "/team/" + teamEnglish + "/" + teamHash + "/");

        List<TeamRow> list = masterJdbcTemplate.query(
                sql,
                params,
                new BeanPropertyRowMapper<>(TeamRow.class));

        if (list != null && !list.isEmpty()) {
            log.info(
                    "teamInfoData: country: {}, league: {}, subLeague: {}, team: {}",
                    list.get(0).getCountry(),
                    list.get(0).getLeague(),
                    list.get(0).getSubLeague(),
                    list.get(0).getTeam());
        }

        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 指定チームと同じ sub_league に属する team 一覧を取得する。
     * 対象チームの sub_league が未設定(null/空)なら、
     * 同一 country + league の全チームを返す。
     */
    public List<String> findTeamsInSameSubLeague(String country, String league, String team) {
        String sql = """
                WITH target_sub_league AS (
                    SELECT
                        NULLIF(TRIM(MAX(sub_league)), '') AS sub_league_norm
                    FROM country_league_master
                    WHERE country = :country
                      AND league  = :league
                      AND team    = :team
                      AND del_flg = '0'
                )
                SELECT DISTINCT clm.team
                FROM country_league_master clm
                CROSS JOIN target_sub_league t
                WHERE clm.country = :country
                  AND clm.league  = :league
                  AND clm.del_flg = '0'
                  AND (
                        t.sub_league_norm IS NULL
                        OR NULLIF(TRIM(clm.sub_league), '') = t.sub_league_norm
                      )
                ORDER BY clm.team
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("country", country)
                .addValue("league", league)
                .addValue("team", team);

        return masterJdbcTemplate.queryForList(sql, params, String.class);
    }
}
