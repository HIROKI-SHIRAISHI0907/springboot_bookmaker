package dev.web.repository.user;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * お気に入り（favorites, user DB）のチームを、昇格・降格先のリーグへ付け替える（bm_a039。2026-11-24 追加）
 * <ol>
 *   <li>そのチームをお気に入りにしているユーザーに、移動先リーグ（level 2）を入れる
 *       （favorites_parent_check トリガーが親の存在を確認するため、先に入れる）。</li>
 *   <li>移動先リーグで同じチームを既にお気に入りにしているユーザーは、移動元の行を消す（重複させない）。</li>
 *   <li>残りのチーム（level 3）のリーグを移動先へ書き換える。</li>
 * </ol>
 * 移動元リーグ（level 2）のお気に入りはそのまま残す（リーグとしてお気に入りにしている場合があるため）。
 * @author shiraishitoshio
 */
@Repository
public class FavoriteLeagueMoveRepository {

	private final NamedParameterJdbcTemplate userJdbcTemplate;

	private final TransactionTemplate transactionTemplate;

	public FavoriteLeagueMoveRepository(
			@Qualifier("webUserJdbcTemplate") NamedParameterJdbcTemplate userJdbcTemplate) {
		this.userJdbcTemplate = userJdbcTemplate;
		this.transactionTemplate = new TransactionTemplate(
				new DataSourceTransactionManager(userJdbcTemplate.getJdbcTemplate().getDataSource()));
	}

	/**
	 * @return 付け替えた（または重複のため消した）チームのお気に入りの件数
	 */
	public int moveTeam(String country, String team, String fromLeague, String toLeague, String operatorId) {
		MapSqlParameterSource p = new MapSqlParameterSource()
				.addValue("country", country)
				.addValue("team", team)
				.addValue("from", fromLeague)
				.addValue("to", toLeague)
				.addValue("op", operatorId);
		int[] n = { 0 };
		transactionTemplate.executeWithoutResult(status -> {
			userJdbcTemplate.update("""
					INSERT INTO favorites (
					  user_id, "level", country, league, team,
					  register_id, register_time, update_id, update_time
					)
					SELECT DISTINCT f.user_id, CAST(2 AS smallint), f.country, :to, '',
					       :op, CURRENT_TIMESTAMP, :op, CURRENT_TIMESTAMP
					FROM favorites f
					WHERE f."level" = '3' AND f.country = :country AND f.league = :from AND f.team = :team
					ON CONFLICT (user_id, "level", country, league, team) DO NOTHING
					""", p);
			int deleted = userJdbcTemplate.update("""
					DELETE FROM favorites f
					WHERE f."level" = '3' AND f.country = :country AND f.league = :from AND f.team = :team
					  AND EXISTS (SELECT 1 FROM favorites x
					               WHERE x.user_id = f.user_id AND x."level" = '3'
					                 AND x.country = f.country AND x.league = :to AND x.team = f.team)
					""", p);
			int updated = userJdbcTemplate.update("""
					UPDATE favorites
					SET league = :to, update_id = :op, update_time = CURRENT_TIMESTAMP
					WHERE "level" = '3' AND country = :country AND league = :from AND team = :team
					""", p);
			n[0] = deleted + updated;
		});
		return n[0];
	}
}
