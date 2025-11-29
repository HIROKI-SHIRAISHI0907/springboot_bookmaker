// src/main/java/dev/web/repository/GamesRepository.java
package dev.web.repository;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.web.api.bm_w005.GameMatchDTO;
import lombok.RequiredArgsConstructor;

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
@RequiredArgsConstructor
public class GamesRepository {

	@Qualifier("bmJdbcTemplate")
    private final NamedParameterJdbcTemplate bmJdbcTemplate;

    @Qualifier("masterJdbcTemplate")
    private final NamedParameterJdbcTemplate masterJdbcTemplate;

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

        String sql = """
        WITH
        team_norm AS (
          SELECT lower(
                   btrim(
                     regexp_replace(
                       translate(TRIM(:teamName), CHR(12288) || CHR(160), '  '),
                       '\\s+', ' ', 'g'
                     )
                   )
                 ) AS key
        ),
        base AS (
          SELECT
            f.seq::text AS seq,
            f.game_team_category,
            f.future_time,
            f.home_team_name,
            f.away_team_name,
            NULLIF(TRIM(f.game_link), '') AS game_link,
            CASE
              WHEN regexp_match(f.game_team_category, '(ラウンド|Round)\\s*([0-9]+)') IS NULL THEN NULL
              ELSE ((regexp_match(f.game_team_category, '(ラウンド|Round)\\s*([0-9]+)'))[2])::int
            END AS round_no,
            lower(btrim(regexp_replace(translate(TRIM(f.home_team_name), CHR(12288) || CHR(160), '  '), '\\s+',' ','g'))) AS home_key,
            lower(btrim(regexp_replace(translate(TRIM(f.away_team_name), CHR(12288) || CHR(160), '  '), '\\s+',' ','g'))) AS away_key
          FROM future_master f
          WHERE f.game_team_category LIKE :likeCond
            AND f.start_flg = '0'
        ),
        base_keyed AS (
          SELECT
            b.*,
            CASE WHEN b.home_key <= b.away_key
                 THEN b.home_key || '|' || b.away_key
                 ELSE b.away_key || '|' || b.home_key
            END AS pair_key
          FROM base b
        ),
        base_for_team AS (
          SELECT bk.*
          FROM base_keyed bk
          CROSS JOIN team_norm t
          WHERE bk.home_key = t.key OR bk.away_key = t.key
        ),
        data_norm AS (
          SELECT
            d.seq::bigint AS seq_big,
            lower(btrim(regexp_replace(translate(TRIM(d.home_team_name), CHR(12288) || CHR(160), '  '), '\\s+',' ','g'))) AS home_key,
            lower(btrim(regexp_replace(translate(TRIM(d.away_team_name), CHR(12288) || CHR(160), '  '), '\\s+',' ','g'))) AS away_key,
            NULLIF(TRIM(d.times), '') AS times,
            NULLIF(TRIM(d.home_score), '')::int AS home_score,
            NULLIF(TRIM(d.away_score), '')::int AS away_score
          FROM public.data d
          WHERE d.home_team_name IS NOT NULL
            AND d.away_team_name IS NOT NULL
            AND d.data_category LIKE :likeCond
            AND (d.record_time AT TIME ZONE 'Asia/Tokyo')::date = (now() AT TIME ZONE 'Asia/Tokyo')::date
        ),
        data_keyed AS (
          SELECT
            dn.*,
            CASE WHEN dn.home_key <= dn.away_key
                 THEN dn.home_key || '|' || dn.away_key
                 ELSE dn.away_key || '|' || dn.home_key
            END AS pair_key
          FROM data_norm dn
        ),
        latest_any AS (
          SELECT pair_key, MAX(seq_big) AS seq_any
          FROM data_keyed
          GROUP BY pair_key
        ),
        latest_fin AS (
          SELECT pair_key, MAX(seq_big) AS seq_fin
          FROM data_keyed
          WHERE times ILIKE '%終了%'
          GROUP BY pair_key
        ),
        chosen AS (
          SELECT
            la.pair_key,
            COALESCE(lf.seq_fin, la.seq_any) AS chosen_seq,
            (lf.seq_fin IS NOT NULL)         AS is_finished
          FROM latest_any la
          LEFT JOIN latest_fin lf ON lf.pair_key = la.pair_key
        ),
        chosen_rows AS (
          SELECT
            dk.pair_key,
            c.chosen_seq,
            c.is_finished,
            dk.times,
            dk.home_score,
            dk.away_score
          FROM chosen c
          JOIN data_keyed dk
            ON dk.pair_key = c.pair_key
           AND dk.seq_big  = c.chosen_seq
        )
        SELECT
          bft.seq,
          bft.game_team_category,
          bft.future_time,
          bft.home_team_name,
          bft.away_team_name,
          bft.game_link,
          bft.round_no,
          cr.chosen_seq::text AS latest_seq,
          cr.times            AS latest_times,
          cr.home_score       AS home_score,
          cr.away_score       AS away_score,
          CASE WHEN cr.is_finished THEN 'FINISHED' ELSE 'LIVE' END AS status
        FROM base_for_team bft
        JOIN chosen_rows cr ON cr.pair_key = bft.pair_key
        ORDER BY bft.round_no NULLS LAST, bft.future_time ASC
        """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("likeCond", likeCond)
                .addValue("teamName", teamJa);

        RowMapper<GameMatchDTO> rowMapper = (rs, rowNum) -> {
            GameMatchDTO dto = new GameMatchDTO();
            dto.setSeq(Long.parseLong(rs.getString("seq")));
            dto.setGameTeamCategory(rs.getString("game_team_category"));
            dto.setFutureTime(rs.getString("future_time"));
            dto.setHomeTeam(rs.getString("home_team_name"));
            dto.setAwayTeam(rs.getString("away_team_name"));
            dto.setLink(rs.getString("game_link"));
            dto.setRoundNo((Integer) rs.getObject("round_no"));
            dto.setLatestTimes(rs.getString("latest_times"));
            String latestSeqStr = rs.getString("latest_seq");
            dto.setLatestSeq(latestSeqStr == null ? null : Long.parseLong(latestSeqStr));
            dto.setHomeScore((Integer) rs.getObject("home_score"));
            dto.setAwayScore((Integer) rs.getObject("away_score"));
            dto.setStatus(rs.getString("status"));
            return dto;
        };

        return bmJdbcTemplate.query(sql, params, rowMapper);
    }
}
