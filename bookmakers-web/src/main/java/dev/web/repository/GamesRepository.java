// src/main/java/dev/web/repository/GamesRepository.java
package dev.web.repository;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.web.api.bm_w005.GameMatchDTO;

/**
 * GamesRepositoryクラス
 *
 * /api/{country}/{league}/{team}/games
 * 用の DB アクセス。
 *
 * - future_master & public.data を突合し、
 *   当日開催中/LIVE & FINISHED 試合を取得する。
 *
 * @author shiraishitoshio
 */
@Repository
public class GamesRepository {

	private final NamedParameterJdbcTemplate bmJdbcTemplate;
    private final NamedParameterJdbcTemplate masterJdbcTemplate;

    public GamesRepository(
            @Qualifier("bmJdbcTemplate") NamedParameterJdbcTemplate bmJdbcTemplate,
            @Qualifier("webMasterJdbcTemplate") NamedParameterJdbcTemplate masterJdbcTemplate
    ) {
        this.bmJdbcTemplate = bmJdbcTemplate;
        this.masterJdbcTemplate = masterJdbcTemplate;
    }

    /**
     * スラッグ → 日本語チーム名 の解決
     *
     * @param country 国名
     * @param league リーグ名
     * @param teamSlug /team/{slug}/... の slug
     * @return 日本語チーム名（見つからなければ slug をそのまま返す）
     */
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

    /**
     * 当日対象チームの試合一覧（LIVE/FINISHED）を取得。
     *
     * @param country 国名
     * @param league リーグ名
     * @param teamJa 日本語チーム名（正規化前）
     * @return 試合一覧（LIVE/FINISHED 混在）※コントローラー側で振り分け
     */
    public List<GameMatchDTO> findGamesForTeam(String country, String league, String teamJa) {

        String likeCond = country + ": " + league + "%";

        // =========================
        // Step 1: future_master から対象チームの試合一覧を取得（master DB）
        // =========================
        String futureSql = """
            SELECT
              f.seq::text                AS seq,
              f.game_team_category       AS game_team_category,
              f.future_time              AS future_time,
              f.home_team_name           AS home_team_name,
              f.away_team_name           AS away_team_name,
              NULLIF(TRIM(f.game_link), '') AS game_link,
              CASE
                WHEN regexp_match(f.game_team_category, '(ラウンド|Round)\\s*([0-9]+)') IS NULL THEN NULL
                ELSE ((regexp_match(f.game_team_category, '(ラウンド|Round)\\s*([0-9]+)'))[2])::int
              END AS round_no
            FROM future_master f
            WHERE f.game_team_category LIKE :likeCond
              AND f.start_flg = '0'
              AND (f.home_team_name = :teamName OR f.away_team_name = :teamName)
            ORDER BY round_no NULLS LAST, f.future_time ASC
            """;

        MapSqlParameterSource futureParams = new MapSqlParameterSource()
                .addValue("likeCond", likeCond)
                .addValue("teamName", teamJa);

        List<GameMatchDTO> baseGames = masterJdbcTemplate.query(
                futureSql,
                futureParams,
                (rs, rowNum) -> {
                    GameMatchDTO dto = new GameMatchDTO();
                    dto.setSeq(Long.parseLong(rs.getString("seq")));
                    dto.setGameTeamCategory(rs.getString("game_team_category"));
                    dto.setFutureTime(rs.getString("future_time"));
                    dto.setHomeTeam(rs.getString("home_team_name"));
                    dto.setAwayTeam(rs.getString("away_team_name"));
                    dto.setLink(rs.getString("game_link"));
                    dto.setRoundNo((Integer) rs.getObject("round_no"));
                    // スコア関連はこの後 Step2 で埋める
                    return dto;
                }
        );

        if (baseGames.isEmpty()) {
            return baseGames;
        }

        // =========================
        // Step 2: data から各試合の最新スコアを取得（bm DB）
        // =========================
        String latestDataSql = """
            SELECT
              d.seq::bigint AS seq_big,
              d.times,
              NULLIF(TRIM(d.home_score), '')::int AS home_score,
              NULLIF(TRIM(d.away_score), '')::int AS away_score
            FROM public.data d
            WHERE d.data_category LIKE :likeCond
              AND d.home_team_name = :homeTeam
              AND d.away_team_name = :awayTeam
              AND (d.record_time AT TIME ZONE 'Asia/Tokyo')::date =
                  (now() AT TIME ZONE 'Asia/Tokyo')::date
            ORDER BY d.seq::bigint DESC
            LIMIT 1
            """;

        List<GameMatchDTO> result = new ArrayList<>();

        for (GameMatchDTO base : baseGames) {

            MapSqlParameterSource dataParams = new MapSqlParameterSource()
                    .addValue("likeCond", likeCond)
                    .addValue("homeTeam", base.getHomeTeam())
                    .addValue("awayTeam", base.getAwayTeam());

            masterFillLatestScore(base, latestDataSql, dataParams);

            result.add(base);
        }

        return result;
    }

    /**
     * bm DB (data テーブル) から最新スコアを取って GameMatchDTO に埋めるヘルパー
     */
    private void masterFillLatestScore(
            GameMatchDTO dto,
            String latestDataSql,
            MapSqlParameterSource params
    ) {
        bmJdbcTemplate.query(
                latestDataSql,
                params,
                rs -> {
                    if (!rs.next()) {
                        // スコアなし → status も null にしておく
                        dto.setStatus(null);
                        dto.setLatestSeq(null);
                        dto.setLatestTimes(null);
                        dto.setHomeScore(null);
                        dto.setAwayScore(null);
                        return;
                    }

                    long latestSeq = rs.getLong("seq_big");
                    String times = rs.getString("times");
                    Integer homeScore = (Integer) rs.getObject("home_score");
                    Integer awayScore = (Integer) rs.getObject("away_score");

                    dto.setLatestSeq(latestSeq);
                    dto.setLatestTimes(times);
                    dto.setHomeScore(homeScore);
                    dto.setAwayScore(awayScore);

                    // times に「終了」が含まれていたら FINISHED、それ以外は LIVE として判定
                    if (times != null && times.contains("終了")) {
                        dto.setStatus("FINISHED");
                    } else {
                        dto.setStatus("LIVE");
                    }
                }
        );
    }
}
