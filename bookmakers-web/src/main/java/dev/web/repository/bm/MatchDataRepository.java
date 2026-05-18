package dev.web.repository.bm;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.web.api.bm_a018.MatchDataByDateItemResource;

/**
 * 対戦データ検索Repository
 */
@Repository
public class MatchDataRepository {

    private final NamedParameterJdbcTemplate bmJdbcTemplate;

    public MatchDataRepository(
            @Qualifier("bmJdbcTemplate") NamedParameterJdbcTemplate bmJdbcTemplate
    ) {
        this.bmJdbcTemplate = bmJdbcTemplate;
    }

    /**
     * 指定日の対戦データを取得
     * - data.record_time を日付検索
     * - 同一試合内で最新1件だけ返す
     */
    public List<MatchDataByDateItemResource> findMatchDataByDate(String targetDate) {
        String sql = """
            WITH ranked AS (
                SELECT
                    COALESCE(
                        NULLIF(d.match_id, ''),
                        NULLIF(d.game_id, ''),
                        d.home_team_name || '|' || d.away_team_name || '|' || COALESCE(d.data_category, '')
                    ) AS match_key,
                    d.match_id        AS matchId,
                    d.game_id         AS gameId,
                    d.data_category   AS dataCategory,
                    d.home_team_name  AS homeTeamName,
                    d.away_team_name  AS awayTeamName,
                    d.add_manual_flg  AS addManualFlg,
                    TO_CHAR(d.record_time, 'YYYY-MM-DD HH24:MI:SS') AS recordTime,
                    d.record_time     AS sortRecordTime,
                    ROW_NUMBER() OVER (
                        PARTITION BY COALESCE(
                            NULLIF(d.match_id, ''),
                            NULLIF(d.game_id, ''),
                            d.home_team_name || '|' || d.away_team_name || '|' || COALESCE(d.data_category, '')
                        )
                        ORDER BY d.record_time DESC NULLS LAST, d.seq DESC
                    ) AS rn
                FROM data d
                WHERE CAST(d.record_time AS DATE) = CAST(:targetDate AS DATE)
            )
            SELECT
                match_key     AS matchKey,
                matchId,
                gameId,
                dataCategory,
                homeTeamName,
                awayTeamName,
                addManualFlg,
                recordTime
            FROM ranked
            WHERE rn = 1
            ORDER BY sortRecordTime DESC, homeTeamName ASC, awayTeamName ASC
        """;

        return bmJdbcTemplate.query(
            sql,
            new MapSqlParameterSource()
                .addValue("targetDate", targetDate),
            (rs, n) -> {
                MatchDataByDateItemResource item = new MatchDataByDateItemResource();
                item.setMatchKey(rs.getString("matchKey"));
                item.setMatchId(rs.getString("matchId"));
                item.setGameId(rs.getString("gameId"));
                item.setDataCategory(rs.getString("dataCategory"));
                item.setHomeTeamName(rs.getString("homeTeamName"));
                item.setAwayTeamName(rs.getString("awayTeamName"));
                item.setAddManualFlg(rs.getString("addManualFlg"));
                item.setRecordTime(rs.getString("recordTime"));
                return item;
            }
        );
    }
}
