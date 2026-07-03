package dev.web.repository.master;

import java.sql.Types;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.web.api.bm_a022.LanguageDTO;
import dev.web.api.bm_a022.LanguageEntity;
import dev.web.api.bm_a022.LanguageSearchCondition;

/**
 * LanguageRepositoryクラス
 *
 * @author shiraishitoshio
 */
@Repository
public class LanguageRepository {

	private final NamedParameterJdbcTemplate masterJdbcTemplate;

	public LanguageRepository(
			@Qualifier("webMasterJdbcTemplate") NamedParameterJdbcTemplate masterJdbcTemplate) {
		this.masterJdbcTemplate = masterJdbcTemplate;
	}

	// --------------------------------------------------------
	// 条件検索: GET /api/language-master/search
	// --------------------------------------------------------
	public List<LanguageDTO> search(LanguageSearchCondition cond) {

		StringBuilder sql = new StringBuilder("""
				SELECT
				  id,
				  country,
				  lang,
				  lang_cd
				FROM language_master
				WHERE 1 = 1
				""");

		MapSqlParameterSource params = new MapSqlParameterSource();

		if (hasText(cond.getCountry())) {
			sql.append(" AND country = :country ");
			params.addValue("country", cond.getCountry());
		}

		return masterJdbcTemplate.query(sql.toString(), params, (rs, n) -> {
			LanguageDTO dto = new LanguageDTO();
			dto.setId(rs.getInt("id"));
			dto.setCountry(rs.getString("country"));
			dto.setLang(rs.getString("lang"));
			dto.setLangCd(rs.getString("lang_cd"));
			return dto;
		});
	}

	// --------------------------------------------------------
	// upsert用: ID更新
	// --------------------------------------------------------
	public int updateById(
			Integer id,
			String country,
			String lang,
			String langCd) {

		String sql = """
				UPDATE language_master
				SET
				  country = :country,
				  lang = :lang,
				  lang_cd = :langCd,
				WHERE id = :id
				""";

		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("id", id)
				.addValue("country", country)
				.addValue("lang", lang, Types.VARCHAR)
				.addValue("langCd", langCd);

		return masterJdbcTemplate.update(sql, params);
	}

	// --------------------------------------------------------
	// upsert用: 新規登録
	// --------------------------------------------------------
	public int insert(LanguageEntity entity) {

		String sql = """
				INSERT INTO language_master (
				  country,
				  lang,
				  lang_cd,
				  register_id,
			      register_time,
			      update_id,
			      update_time
				) VALUES (
				  :country,
				  :lang,
				  :langCd,
				  'SYSTEM',
				  NOW(),
				  'SYSTEM',
				  NOW()
				)
				""";

		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("country", entity.getCountry(), Types.VARCHAR)
				.addValue("lang", entity.getLang(), Types.VARCHAR)
				.addValue("langCd", entity.getLangCd(), Types.VARCHAR);

		return masterJdbcTemplate.update(sql, params);
	}

	private boolean hasText(String s) {
		return s != null && !s.isBlank();
	}
}
