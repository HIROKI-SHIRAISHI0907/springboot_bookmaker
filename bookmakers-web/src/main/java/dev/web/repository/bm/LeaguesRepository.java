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

/**
 * LeaguesRepositoryクラス
 * @author shiraishitoshio
 *
 */
@Repository
public class LeaguesRepository {

    private final NamedParameterJdbcTemplate masterJdbcTemplate;

    public LeaguesRepository(
            @Qualifier("webMasterJdbcTemplate") NamedParameterJdbcTemplate masterJdbcTemplate
    ) {
        this.masterJdbcTemplate = masterJdbcTemplate;
    }

    // --- Row 用の内部クラス（DB そのまま） ---
    @Data
    public static class LeagueCountRow {
        public String country;
        public String league;
        public Long teamCount;
        public String path;
    }

    @Data
    public static class TeamRow {
        public Integer id;
        public String country;
        public String league;
        public String team;
        public String link;
    }

    private static final Pattern TEAM_LINK_PATTERN =
            Pattern.compile("^/team/([^/]+)/([^/]+)", Pattern.CASE_INSENSITIVE);

    // Node の toPath と同様: trim → 空白を1つに → encodeURIComponent 相当
    public String toPath(String s) {
        if (s == null) return "";
        String trimmed = s.trim().replaceAll("\\s+", " ");
        String enc = URLEncoder.encode(trimmed, StandardCharsets.UTF_8);
        // encodeURIComponent っぽく "+" を "%20" に
        return enc.replace("+", "%20");
    }

    public String[] parseTeamLink(String link) {
        if (link == null) return new String[] { "", "" };
        Matcher m = TEAM_LINK_PATTERN.matcher(link);
        if (!m.find()) return new String[] { "", "" };
        return new String[] { m.group(1), m.group(2) };
    }

    // --- クエリ ---

    /** country, league ごとのチーム数 */
    public List<LeagueCountRow> findLeagueCounts() {
    	String sql = """
    	        SELECT
    	            s.country,
    	            s.league,
    	            s.path,
    	            COUNT(c.*) AS team_count
    	        FROM country_league_season_master s
    	        LEFT JOIN country_league_master c
    	          ON c.country = s.country
    	         AND c.league  = s.league
    	        GROUP BY
    	            s.country, s.league, s.path
    	        ORDER BY
    	            s.country, s.league, s.path
    	        """;

        return masterJdbcTemplate.query(
                sql,
                new BeanPropertyRowMapper<>(LeagueCountRow.class)
        );
    }

    /** 国＋リーグのチーム一覧 */
    public List<TeamRow> findTeamsInLeague(String country, String league) {
        String sql = """
            SELECT id, country, league, team, link
            FROM country_league_master
            WHERE country = :country AND league = :league
            ORDER BY team
            """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("country", country)
                .addValue("league", league);

        return masterJdbcTemplate.query(
                sql,
                params,
                new BeanPropertyRowMapper<>(TeamRow.class)
        );
    }

    /** 指定チーム詳細 (1件のみ) */
    public TeamRow findTeamDetail(String country, String league, String teamEnglish) {
        String sql = """
            SELECT id, country, league, team, link
            FROM country_league_master
            WHERE country = :country
              AND league  = :league
              AND link LIKE :link
            LIMIT 1
            """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("country", country)
                .addValue("league", league)
                .addValue("link", "/team/" + teamEnglish + "/%");

        List<TeamRow> list = masterJdbcTemplate.query(
                sql,
                params,
                new BeanPropertyRowMapper<>(TeamRow.class)
        );
        return list.isEmpty() ? null : list.get(0);
    }
}
