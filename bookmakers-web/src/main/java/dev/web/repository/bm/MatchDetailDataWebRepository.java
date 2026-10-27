package dev.web.repository.bm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 統計詳細データ（B016 が aws-s3-detail-data-csv から登録する match_detail_* テーブル）参照用リポジトリ
 * @author shiraishitoshio
 */
@Repository
public class MatchDetailDataWebRepository {

	/** IN 句 1 回あたりの件数 */
	private static final int IN_CHUNK_SIZE = 1000;

	private final NamedParameterJdbcTemplate bmJdbcTemplate;

	public MatchDetailDataWebRepository(
			@Qualifier("bmJdbcTemplate") NamedParameterJdbcTemplate bmJdbcTemplate) {
		this.bmJdbcTemplate = bmJdbcTemplate;
	}

	/**
	 * 指定した試合ID（Flashscore の mid）のうち、match_detail_match に登録済みのものを返す。
	 *
	 * @param matchIds 調べる試合ID
	 * @return 登録済みの試合ID
	 */
	public Set<String> findRegisteredMatchIds(Collection<String> matchIds) {
		Set<String> result = new HashSet<>();
		if (matchIds == null || matchIds.isEmpty()) {
			return result;
		}

		List<String> ids = new ArrayList<>(new HashSet<>(matchIds));
		String sql = """
				    SELECT DISTINCT match_id
				    FROM match_detail_match
				    WHERE match_id IN (:ids)
				""";

		for (int i = 0; i < ids.size(); i += IN_CHUNK_SIZE) {
			List<String> chunk = ids.subList(i, Math.min(i + IN_CHUNK_SIZE, ids.size()));
			result.addAll(bmJdbcTemplate.queryForList(
					sql, new MapSqlParameterSource().addValue("ids", chunk), String.class));
		}
		return result;
	}
}
