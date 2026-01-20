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

	private final NamedParameterJdbcTemplate masterJdbcTemplate;

	public PlayersRepository(
			@Qualifier("webMasterJdbcTemplate") NamedParameterJdbcTemplate masterJdbcTemplate) {
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
				(rs, rowNum) -> rs.getString("team"));

		return list.isEmpty() ? teamSlug : list.get(0);
	}

	/**
	 * チーム（日本語名）に紐づく現役選手一覧を取得。
	 */
	public List<PlayerDTO> findPlayers(String country, String league, String teamJa) {

		String sql = """
				SELECT
				   t.id,

				   /* jersey: 数字だけ抽出して int 化（非数値/空は NULL） */
				   NULLIF(regexp_replace(COALESCE(t.jersey, ''), '[^0-9]', '', 'g'), '')::int AS jersey,

				   t.member,
				   NULLIF(TRIM(t.face_pic_path), '') AS face_pic_path,
				   NULLIF(TRIM(t.position), '') AS position,

				   /* birth: text/date/timestamp 混在想定。落ちないように YYYY-MM-DD に整形 */
				   CASE
				      WHEN t.birth IS NULL THEN NULL
				      WHEN TRIM(t.birth::text) = '' THEN NULL
				      WHEN LOWER(TRIM(t.birth::text)) = 'null' THEN NULL

				      /* YYYY-MM-DD */
				      WHEN TRIM(t.birth::text) ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}$'
				        THEN TRIM(t.birth::text)

				      /* YYYY/MM/DD */
				      WHEN TRIM(t.birth::text) ~ '^[0-9]{4}/[0-9]{2}/[0-9]{2}$'
				        THEN to_char(to_date(TRIM(t.birth::text), 'YYYY/MM/DD'), 'YYYY-MM-DD')

				      /* timestamp っぽい（2026-01-21 12:34:56 / 2026-01-21T12:34:56 等） */
				      WHEN TRIM(t.birth::text) ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}[ T][0-9]{2}:[0-9]{2}(:[0-9]{2})?.*$'
				        THEN to_char(TRIM(t.birth::text)::timestamp, 'YYYY-MM-DD')

				      ELSE NULL
				   END AS birth,

				   /* age: 数字だけ抽出して int 化（例: "23歳" -> 23）。空/非数値は NULL */
				   NULLIF(regexp_replace(COALESCE(t.age::text, ''), '[^0-9]', '', 'g'), '')::int AS age,

				   NULLIF(TRIM(t.market_value), '') AS market_value,
				   NULLIF(TRIM(t.height), '') AS height,
				   NULLIF(TRIM(t.weight), '') AS weight,
				   NULLIF(TRIM(t.loan_belong), '') AS loan_belong,
				   NULLIF(TRIM(t.belong_list), '') AS belong_list,
				   NULLIF(TRIM(t.injury), '') AS injury,

				   /* 契約期限: YYYY-MM-DD / YYYY/MM/DD / timestamp を許容して YYYY-MM-DD に整形 */
				   CASE
				      WHEN t.deadline_contract_date IS NULL THEN NULL
				      WHEN TRIM(t.deadline_contract_date::text) = '' THEN NULL
				      WHEN LOWER(TRIM(t.deadline_contract_date::text)) = 'null' THEN NULL

				      WHEN TRIM(t.deadline_contract_date::text) ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}$'
				        THEN TRIM(t.deadline_contract_date::text)

				      WHEN TRIM(t.deadline_contract_date::text) ~ '^[0-9]{4}/[0-9]{2}/[0-9]{2}$'
				        THEN to_char(to_date(TRIM(t.deadline_contract_date::text), 'YYYY/MM/DD'), 'YYYY-MM-DD')

				      WHEN TRIM(t.deadline_contract_date::text) ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}[ T][0-9]{2}:[0-9]{2}(:[0-9]{2})?.*$'
				        THEN to_char(TRIM(t.deadline_contract_date::text)::timestamp, 'YYYY-MM-DD')

				      ELSE NULL
				   END AS deadline_contract_date,

				   /* 最新情報日: 同様 */
				   CASE
				      WHEN t.latest_info_date IS NULL THEN NULL
				      WHEN TRIM(t.latest_info_date::text) = '' THEN NULL
				      WHEN LOWER(TRIM(t.latest_info_date::text)) = 'null' THEN NULL

				      WHEN TRIM(t.latest_info_date::text) ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}$'
				        THEN TRIM(t.latest_info_date::text)

				      WHEN TRIM(t.latest_info_date::text) ~ '^[0-9]{4}/[0-9]{2}/[0-9]{2}$'
				        THEN to_char(to_date(TRIM(t.latest_info_date::text), 'YYYY/MM/DD'), 'YYYY-MM-DD')

				      WHEN TRIM(t.latest_info_date::text) ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}[ T][0-9]{2}:[0-9]{2}(:[0-9]{2})?.*$'
				        THEN to_char(TRIM(t.latest_info_date::text)::timestamp, 'YYYY-MM-DD')

				      ELSE NULL
				   END AS latest_info_date

				FROM team_member_master t
				WHERE t.country = :country
				  AND t.league  = :league
				  AND t.team    = :team
				  AND COALESCE(t.retire_flg, '0') = '0'
				ORDER BY
				  CASE t.position
				     WHEN 'ゴールキーパー' THEN 1
				     WHEN 'ディフェンダー' THEN 2
				     WHEN 'ミッドフィルダー' THEN 3
				     WHEN 'フォワード' THEN 4
				     ELSE 5
				  END,
				  (NULLIF(regexp_replace(COALESCE(t.jersey, ''), '[^0-9]', '', 'g'), '')::int) NULLS LAST,
				  t.member
				""";

		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("country", country)
				.addValue("league", league)
				.addValue("team", teamJa);

		return masterJdbcTemplate.query(
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
				});
	}
}
