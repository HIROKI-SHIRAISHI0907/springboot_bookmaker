package dev.web.repository.bm;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.web.api.bm_a017.TodayCreatedCsvItemResource;

/**
 * 本日作成CSV情報取得Repository
 */
@Repository
public class TodayCreatedCsvRepository {

    private final NamedParameterJdbcTemplate bmJdbcTemplate;

    public TodayCreatedCsvRepository(
            @Qualifier("bmJdbcTemplate") NamedParameterJdbcTemplate bmJdbcTemplate
    ) {
        this.bmJdbcTemplate = bmJdbcTemplate;
    }

    /**
     * 本日作成されたCSV情報を取得
     */
    public List<TodayCreatedCsvItemResource> findTodayCreatedCsvs() {
        String sql = """
            SELECT
              cdm.csv_id         AS csvId,
              cdm.data_category  AS dataCategory,
              cdm.season         AS season,
              cdm.home_team_name AS homeTeamName,
              cdm.away_team_name AS awayTeamName,
              cdm.check_fin_flg  AS checkFinFlg,
              TO_CHAR(cdm.register_time, 'YYYY-MM-DD HH24:MI:SS') AS registerTime
            FROM csv_detail_manage cdm
            WHERE cdm.register_time >= CURRENT_DATE
              AND cdm.register_time < CURRENT_DATE + INTERVAL '1 day'
            ORDER BY cdm.register_time DESC, cdm.csv_id ASC
        """;

        return bmJdbcTemplate.query(
            sql,
            new MapSqlParameterSource(),
            (rs, n) -> {
                TodayCreatedCsvItemResource item = new TodayCreatedCsvItemResource();
                item.setCsvId(rs.getString("csvId"));
                item.setDataCategory(rs.getString("dataCategory"));
                item.setSeason(rs.getString("season"));
                item.setHomeTeamName(rs.getString("homeTeamName"));
                item.setAwayTeamName(rs.getString("awayTeamName"));
                item.setCheckFinFlg(rs.getString("checkFinFlg"));
                item.setRegisterTime(rs.getString("registerTime"));
                return item;
            }
        );
    }
}
