// src/main/java/dev/web/repository/GamesRepository.java
package dev.web.repository.bm;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.common.entity.MatchKeySaveEntity;

/**
 * MatchKeyRepositoryクラス
 *
 * @author shiraishitoshio
 */
@Repository
public class MatchKeyRepository {

	private final NamedParameterJdbcTemplate bmJdbcTemplate;

    public MatchKeyRepository(
            @Qualifier("bmJdbcTemplate") NamedParameterJdbcTemplate bmJdbcTemplate
    ) {
        this.bmJdbcTemplate = bmJdbcTemplate;
    }

    /**
     * マッチキー取得
     *
     * @param matchKey マッチキー
     * @return id
     */
    public Optional<String> findMatchKeyId(String matchKey) {
        String sql = """
            SELECT id
            FROM match_key_save
            WHERE match_key = :matchKey
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("matchKey", matchKey);

        try {
            String id = bmJdbcTemplate.queryForObject(sql, params, String.class);
            return Optional.ofNullable(id);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    /** 新規登録（手動登録） */
	public int insert(MatchKeySaveEntity e) {

		String sql = """
				INSERT INTO match_key_save (
				  match_key,
				  data_category,
				  home_team_name,
				  away_team_name,
				  logic_flg,
				  register_id,
				  register_time,
				  update_id,
				  update_time
				) VALUES (
				  :matchKey,
				  :dataCategory,
				  :homeTeamName,
				  :awayTeamName,
				  :logicFlg,
				  SYSTEM,
				  CURRENT_TIMESTAMP,
				  SYSTEM,
				  CURRENT_TIMESTAMP
				)
				""";

		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("matchKey", e.getMatchKey())
                .addValue("dataCategory", e.getDataCategory())
                .addValue("homeTeamName", e.getHomeTeamName())
                .addValue("awayTeamName", e.getAwayTeamName())
                .addValue("logicFlg", e.getLogicFlg());

		return bmJdbcTemplate.update(sql, params);
	}

}
