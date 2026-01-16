package dev.web.repository.bm;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.web.api.bm_w009.PlayerDTO;

/**
 * PlayersRepositoryクラス
 * @author shiraishitoshio
 *
 */
@Repository
public class PlayersRepository {

	private final NamedParameterJdbcTemplate bmJdbcTemplate;
    private final NamedParameterJdbcTemplate masterJdbcTemplate;

    public PlayersRepository(
            @Qualifier("bmJdbcTemplate") NamedParameterJdbcTemplate bmJdbcTemplate,
            @Qualifier("webMasterJdbcTemplate") NamedParameterJdbcTemplate masterJdbcTemplate
    ) {
        this.bmJdbcTemplate = bmJdbcTemplate;
        this.masterJdbcTemplate = masterJdbcTemplate;
    }

    /**
     * country / league / teamSlug から 日本語チーム名を取得。
     * 見つからない場合は teamSlug をそのまま返す（Node 実装と同じ挙動）。
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
     * チーム（日本語名）に紐づく現役選手一覧を取得。
     */
    public List<PlayerDTO> findPlayers(String country, String league, String teamJa) {

        String sql = """
            SELECT
               t.id,
               NULLIF(TRIM(t.jersey), '') AS jersey,
               t.member,
               NULLIF(TRIM(t.face_pic_path), '') AS face_pic_path,
               NULLIF(TRIM(t.position), '') AS position,

               /* birth: text の可能性があるため timestamp にキャストしてから to_char */
               CASE
                  WHEN t.birth IS NULL OR TRIM(t.birth::text) = '' THEN NULL
                  ELSE to_char((t.birth::timestamp), 'YYYY-MM-DD')
               END AS birth,

               /* age: 数値化（text の場合にも対応） */
               NULLIF(TRIM(t.age::text), '')::int AS age,

               NULLIF(TRIM(t.market_value), '') AS market_value,
               NULLIF(TRIM(t.height), '') AS height,
               NULLIF(TRIM(t.weight), '') AS weight,
               NULLIF(TRIM(t.loan_belong), '') AS loan_belong,
               NULLIF(TRIM(t.belong_list), '') AS belong_list,
               NULLIF(TRIM(t.injury), '') AS injury,

               /* 契約期限・最新情報日: 同様に text→timestamp→to_char で安全化 */
               CASE
                  WHEN t.deadline_contract_date IS NULL OR TRIM(t.deadline_contract_date::text) = '' THEN NULL
                  ELSE to_char((t.deadline_contract_date::timestamp), 'YYYY-MM-DD')
               END AS deadline_contract_date,
               CASE
                  WHEN t.latest_info_date IS NULL OR TRIM(t.latest_info_date::text) = '' THEN NULL
                  ELSE to_char((t.latest_info_date::timestamp), 'YYYY-MM-DD')
               END AS latest_info_date

            FROM team_member_master t
            WHERE t.country = :country
               AND t.league  = :league
               AND t.team    = :team
               AND COALESCE(t.retire_flg, '0') = '0'
            ORDER BY
               CASE t.position
                  WHEN 'ゴールキーパー' THEN 1
                  WHEN 'ディフェンダー'   THEN 2
                  WHEN 'ミッドフィルダー' THEN 3
                  WHEN 'フォワード'       THEN 4
                  ELSE 5
               END,
               /* 背番号は非数値に強い並び（数字以外を除去→int）*/
               NULLIF(regexp_replace(COALESCE(t.jersey, ''), '\\D', '', 'g'), '')::int NULLS LAST,
               t.member
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("country", country)
                .addValue("league", league)
                .addValue("team", teamJa);

        return bmJdbcTemplate.query(
                sql,
                params,
                (rs, rowNum) -> {
                    PlayerDTO dto = new PlayerDTO();
                    dto.setId(rs.getLong("id"));

                    String jerseyStr = rs.getString("jersey");
                    if (jerseyStr != null && !jerseyStr.isBlank()) {
                        try {
                            dto.setJersey(Integer.valueOf(jerseyStr));
                        } catch (NumberFormatException e) {
                            dto.setJersey(null);
                        }
                    } else {
                        dto.setJersey(null);
                    }

                    dto.setName(rs.getString("member"));
                    dto.setFace(rs.getString("face_pic_path"));
                    dto.setPosition(rs.getString("position") != null ? rs.getString("position") : "-");
                    dto.setBirth(rs.getString("birth"));
                    dto.setAge((Integer) rs.getObject("age"));
                    dto.setMarketValue(rs.getString("market_value"));
                    dto.setHeight(rs.getString("height"));
                    dto.setWeight(rs.getString("weight"));
                    dto.setLoanBelong(rs.getString("loan_belong"));
                    dto.setBelongList(rs.getString("belong_list"));
                    dto.setInjury(rs.getString("injury"));
                    dto.setContractUntil(rs.getString("deadline_contract_date"));
                    dto.setLatestInfoDate(rs.getString("latest_info_date"));
                    return dto;
                }
        );
    }
}
