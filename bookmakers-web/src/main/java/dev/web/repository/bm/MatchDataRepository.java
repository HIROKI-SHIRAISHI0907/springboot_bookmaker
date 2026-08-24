package dev.web.repository.bm;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
     * 指定日の対戦データ総件数を取得
     */
    public int countMatchDataByDate(String targetDate) {
        String sql = """
            WITH ranked AS (
                SELECT
                    ROW_NUMBER() OVER (
                        PARTITION BY COALESCE(
                            NULLIF(d.match_id, ''),
                            NULLIF(d.game_id, ''),
                            d.home_team_name || '|' || d.away_team_name || '|' || COALESCE(d.data_category, '')
                        )
                        ORDER BY d.record_time DESC NULLS LAST, d.seq_key DESC
                    ) AS rn
                FROM static_data d
                WHERE CAST(d.record_time AS DATE) = CAST(:targetDate AS DATE)
            )
            SELECT COUNT(*)
            FROM ranked
            WHERE rn = 1
        """;

        Integer count = bmJdbcTemplate.queryForObject(
                sql,
                new MapSqlParameterSource().addValue("targetDate", targetDate),
                Integer.class);

        return count == null ? 0 : count;
    }

    /**
     * 指定日の対戦データをページ単位で取得
     * - data.record_time を日付検索
     * - 同一試合内で最新1件だけ返す
     */
    public List<MatchDataByDateItemResource> findMatchDataByDate(String targetDate, int limit, int offset) {
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
                        ORDER BY d.record_time DESC NULLS LAST, d.seq_key DESC
                    ) AS rn
                FROM static_data d
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
            ORDER BY sortRecordTime DESC
            LIMIT :limit OFFSET :offset
        """;

        return bmJdbcTemplate.query(
            sql,
            new MapSqlParameterSource()
                .addValue("targetDate", targetDate)
                .addValue("limit", limit)
                .addValue("offset", offset),
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

    /**
     * csv_detail_manage に一致するレコードが存在するかを判定する。
     * data_category は完全一致、home/away は表記ゆれを吸収するため NFKC 正規化した上で比較する。
     * season は country/league からの再計算に依存すると batch 側の算出ロジックとズレるリスクがあるため、
     * 判定条件から除外している。
     */
    public boolean existsCsvDetailManage(String dataCategory, String homeTeamName, String awayTeamName) {
        String sql = """
            SELECT COUNT(*)
            FROM csv_detail_manage
            WHERE data_category = :dataCategory
              AND normalize(home_team_name, NFKC) = normalize(:homeTeamName, NFKC)
              AND normalize(away_team_name, NFKC) = normalize(:awayTeamName, NFKC)
        """;
        Integer count = bmJdbcTemplate.queryForObject(
                sql,
                new MapSqlParameterSource()
                        .addValue("dataCategory", dataCategory)
                        .addValue("homeTeamName", homeTeamName)
                        .addValue("awayTeamName", awayTeamName),
                Integer.class);
        return count != null && count > 0;
    }

    /**
     * 指定した matchId 群のうち、static_data に data_category = 'ハーフタイム' と '終了済' が
     * 両方存在する matchId の集合を返す（CSV作成対象の判定に使用）。
     */
    public Set<String> findMatchIdsWithHalftimeAndFinished(List<String> matchIds) {
        if (matchIds == null || matchIds.isEmpty()) {
            return Set.of();
        }
        String sql = """
            SELECT match_id
            FROM static_data
            WHERE match_id IN (:matchIds)
              AND data_category IN ('ハーフタイム', '終了済')
            GROUP BY match_id
            HAVING COUNT(DISTINCT data_category) = 2
        """;
        List<String> rows = bmJdbcTemplate.queryForList(
                sql,
                new MapSqlParameterSource().addValue("matchIds", matchIds),
                String.class);
        return new HashSet<>(rows);
    }

}
