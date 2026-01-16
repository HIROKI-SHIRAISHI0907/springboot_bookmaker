package dev.web.repository.master;

import java.util.Set;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.common.util.TableUtil;

/**
 * LogicFlgRepositoryクラス
 * @author shiraishitoshio
 *
 */
@Repository
public class LogicFlgRepository {

    private final NamedParameterJdbcTemplate webMasterJdbcTemplate;

    // SQLインジェクション防止：テーブル名は許可リストのみ
    private final Set<String> allowedTables;

    public LogicFlgRepository(
            @Qualifier("webMasterJdbcTemplate") NamedParameterJdbcTemplate webMasterJdbcTemplate
    ) {
        this.webMasterJdbcTemplate = webMasterJdbcTemplate;

        // 既存の TableUtil から作る（国テーブル＋カテゴリテーブル）
        // ※必要に応じて union する
        this.allowedTables = Set.copyOf(TableUtil.getAllList());
    }

    /**
     * 指定テーブルの件数
     */
    public int findDataCount(String table) {
        String safeTable = requireAllowedTable(table);

        String sql = "SELECT COUNT(*) FROM " + safeTable;
        Integer count = webMasterJdbcTemplate.queryForObject(sql, new MapSqlParameterSource(), Integer.class);
        return count == null ? 0 : count;
    }

    /**
     * country + league の完全一致で更新
     */
    public int updateLogicFlgByCountryLeague(String table, String country, String league, String logicFlg) {
        String safeTable = requireAllowedTable(table);

        String sql = """
            UPDATE %s
            SET logic_flg = :logicFlg
            WHERE country = :country
              AND league  = :league
        """.formatted(safeTable);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("logicFlg", logicFlg)
                .addValue("country", country)
                .addValue("league", league);

        return webMasterJdbcTemplate.update(sql, params);
    }

    /**
     * data_category LIKE "country: league%" で更新
     */
    public int updateLogicFlgByCategoryLike(String table, String country, String league, String logicFlg) {
        String safeTable = requireAllowedTable(table);

        String sql = """
            UPDATE %s
            SET logic_flg = :logicFlg
            WHERE data_category LIKE :pattern
        """.formatted(safeTable);

        String pattern = country + ": " + league + "%";

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("logicFlg", logicFlg)
                .addValue("pattern", pattern);

        return webMasterJdbcTemplate.update(sql, params);
    }

    /**
     * 全件更新
     */
    public int updateAllLogicFlg(String table, String logicFlg) {
        String safeTable = requireAllowedTable(table);

        String sql = """
            UPDATE %s
            SET logic_flg = :logicFlg
        """.formatted(safeTable);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("logicFlg", logicFlg);

        return webMasterJdbcTemplate.update(sql, params);
    }

    // -------------------------
    // 共通：テーブル名ガード
    // -------------------------
    private String requireAllowedTable(String table) {
        if (table == null || table.isBlank()) {
            throw new IllegalArgumentException("table is blank");
        }
        if (!allowedTables.contains(table)) {
            throw new IllegalArgumentException("table is not allowed: " + table);
        }
        return table;
    }
}
