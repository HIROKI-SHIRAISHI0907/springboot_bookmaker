package dev.web.repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.web.api.bm_w006.StandingRowDTO;
import lombok.RequiredArgsConstructor;

/**
 * 順位表取得用リポジトリ.
 *
 * /api/{country}/{league}/standings
 * で利用される。
 *
 * - public.surface_overview からチームごとに集計
 * - country_league_master から link を取得し、slug (teamEnglish) を切り出す
 * - 並び順:
 *   勝点 DESC → 得失点差 DESC → 勝利 DESC → 敗戦 ASC → チーム名 ASC
 *
 * @author shiraishitoshio
 */
@Repository
@RequiredArgsConstructor
public class StandingsRepository {

	@Qualifier("bmJdbcTemplate")
    private final NamedParameterJdbcTemplate bmJdbcTemplate;

    @Qualifier("masterJdbcTemplate")
    private final NamedParameterJdbcTemplate masterJdbcTemplate;

    /**
     * 国・リーグを指定して順位表を取得する。
     *
     * @param country 国名（日本語）
     * @param league リーグ名（日本語）
     * @return 順位表行一覧
     */
    public List<StandingRowDTO> findStandings(String country, String league) {

        String baseSql = """
            WITH base AS (
              SELECT
                o.team,
                SUM(COALESCE(NULLIF(BTRIM(o.win),  '')::int, 0))   AS win,
                SUM(COALESCE(NULLIF(BTRIM(o.lose), '')::int, 0))   AS lose,
                SUM(COALESCE(NULLIF(BTRIM(o.draw), '')::int, 0))   AS draw,
                SUM(COALESCE(NULLIF(BTRIM(o.winning_points), '')::int, 0)) AS points,
                -- 総得点（ホーム+アウェイ）
                SUM(
                  COALESCE(NULLIF(BTRIM(o.home_sum_score), '')::int, 0)
                  + COALESCE(NULLIF(BTRIM(o.away_sum_score), '')::int, 0)
                ) AS goals_for,
                -- 総失点（ホーム+アウェイ）
                SUM(
                  COALESCE(NULLIF(BTRIM(o.home_sum_lost), '')::int, 0)
                  + COALESCE(NULLIF(BTRIM(o.away_sum_lost), '')::int, 0)
                ) AS goals_against
              FROM public.surface_overview o
              WHERE o.country = :country
                AND o.league  = :league
              GROUP BY o.team
            ),
            with_meta AS (
              SELECT
                b.*,
                (b.win + b.draw + b.lose) AS game,
                (b.goals_for - b.goals_against) AS goal_diff
              FROM base b
            ),
            ranked AS (
              SELECT
                ROW_NUMBER() OVER (
                  ORDER BY points DESC, goal_diff DESC, win DESC, lose ASC, team ASC
                ) AS position,
                *
              FROM with_meta
            )
            SELECT
              r.position,
              r.team AS team_name,
              r.game,
              r.win,
              r.draw,
              r.lose,
              r.points,
              r.goal_diff
            FROM ranked r
            ORDER BY r.points DESC, r.goal_diff DESC
            """;

        Map<String, Object> params = Map.of(
                "country", country,
                "league", league
        );

        List<StandingRowDTO> rows = bmJdbcTemplate.query(
                baseSql,
                params,
                (rs, rowNum) -> {
                	StandingRowDTO row = new StandingRowDTO();
                    row.setPosition(rs.getInt("position"));
                    row.setTeamName(rs.getString("team_name"));
                    row.setGame(rs.getInt("game"));
                    row.setWin(rs.getInt("win"));
                    row.setDraw(rs.getInt("draw"));
                    row.setLose(rs.getInt("lose"));
                    row.setWinningPoints(rs.getInt("points"));
                    row.setGoalDiff(rs.getInt("goal_diff"));
                    return row;
                }
        );

        // ② master 側（country_league_master）から link を取得
        String masterSql = """
          SELECT team, MIN(NULLIF(TRIM(link), '')) AS link
          FROM country_league_master
          WHERE country = :country AND league = :league
          GROUP BY team
        """;

        Map<String, String> linkMap = masterJdbcTemplate.query(
                masterSql,
                params,
                rs -> {
                    Map<String, String> m = new HashMap<>();
                    while (rs.next()) {
                        m.put(rs.getString("team"), rs.getString("link"));
                    }
                    return m;
                }
        );

        // ③ Java で merge ＆ teamEnglish をセット
        for (StandingRowDTO row : rows) {
            String link = linkMap.getOrDefault(row.getTeamName(), "");
            String teamEnglish = "";
            if (link != null && !link.isBlank()) {
                // 例: "/team/premier-league/arsenal/" → 3番目のパス要素を取り出すなど
                String[] parts = link.split("/");
                if (parts.length >= 4) {
                    teamEnglish = parts[3]; // 実際の link 形式に合わせて調整
                }
            }
            row.setTeamEnglish(teamEnglish);
        }

        return rows;
    }
}
