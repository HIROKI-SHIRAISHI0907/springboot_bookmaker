package dev.web.repository.master;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.web.api.matchDetail.matchVenueDTO.MatchVenueDTO;


/**
 * team_location_master（master DB）: 試合詳細のスタジアム地理情報
 *
 * 照合キー = NFKC 正規化して、空白・中黒（・･）・＝・ハイフン類・括弧・ピリオド・カンマを除いたもの。
 */
@Repository
public class MatchVenueRepository {

	/** 照合キー（SQL 側）。:x に列名かパラメータを入れて使う */
	private static String key(String expr) {
		return "regexp_replace(normalize(COALESCE(" + expr + ", ''), NFKC), '[[:space:]・=\\-‐‑–—―－ー.,()「」]', '', 'g')";
	}

	private static final String SELECT = """
			SELECT
			  id,
			  country,
			  team_name,
			  home_city,
			  home_city_translate,
			  stadium_name,
			  COALESCE(NULLIF(BTRIM(display_name_en), ''), NULLIF(BTRIM(stadium_name_translate), '')) AS stadium_name_en,
			  COALESCE(NULLIF(BTRIM(address), ''), NULLIF(BTRIM(address_en), '')) AS address,
			  COALESCE(latitude, latitude_en)   AS latitude,
			  COALESCE(longitude, longitude_en) AS longitude,
			  NULLIF(BTRIM(place_id), '') AS place_id
			FROM team_location_master
			WHERE CURRENT_DATE BETWEEN COALESCE(valid_from, DATE '1900-01-01') AND COALESCE(valid_to, DATE '9999-12-31')
			""";

	private static final RowMapper<MatchVenueDTO> MAPPER = (rs, n) -> {
		MatchVenueDTO d = new MatchVenueDTO();
		d.setCountry(rs.getString("country"));
		d.setTeamName(rs.getString("team_name"));
		d.setHomeCity(blankToNull(rs.getString("home_city")));
		d.setHomeCityEn(blankToNull(rs.getString("home_city_translate")));
		d.setStadiumName(blankToNull(rs.getString("stadium_name")));
		d.setStadiumNameEn(blankToNull(rs.getString("stadium_name_en")));
		d.setAddress(blankToNull(rs.getString("address")));
		d.setLatitude(rs.getBigDecimal("latitude"));
		d.setLongitude(rs.getBigDecimal("longitude"));
		d.setPlaceId(rs.getString("place_id"));
		return d;
	};

	private final NamedParameterJdbcTemplate masterJdbcTemplate;

	public MatchVenueRepository(
			@Qualifier("webMasterJdbcTemplate") NamedParameterJdbcTemplate masterJdbcTemplate) {
		this.masterJdbcTemplate = masterJdbcTemplate;
	}

	/**
	 * スタジアム名で探す。完全一致（照合キー）→ 部分一致（どちらかがもう一方を含む。4文字以上のときだけ）。
	 * 同点なら国が一致 → 座標あり → 新しい順。
	 */
	public MatchVenueDTO findByStadium(String stadium, String country) {
		String k = key("stadium_name");
		String p = key("CAST(:stadium AS text)");
		String sql = SELECT + """
				  AND BTRIM(COALESCE(stadium_name, '')) <> ''
				  AND (
				        %1$s = %2$s
				     OR (length(%2$s) >= 4 AND length(%1$s) >= 4
				         AND (strpos(%1$s, %2$s) > 0 OR strpos(%2$s, %1$s) > 0))
				  )
				ORDER BY
				  (%1$s = %2$s) DESC,
				  (normalize(COALESCE(country, ''), NFKC) = normalize(COALESCE(CAST(:country AS text), ''), NFKC)) DESC,
				  (COALESCE(latitude, latitude_en) IS NOT NULL) DESC,
				  valid_from DESC NULLS LAST,
				  id DESC
				LIMIT 1
				""".formatted(k, p);
		return first(masterJdbcTemplate.query(sql,
				new MapSqlParameterSource().addValue("stadium", stadium).addValue("country", country), MAPPER));
	}

	/**
	 * ホームチームのホームスタジアム（team_name の NFKC 一致）。国が一致 → 座標あり → 新しい順。
	 */
	public MatchVenueDTO findByTeam(String team, String country) {
		String sql = SELECT + """
				  AND normalize(team_name, NFKC) = normalize(CAST(:team AS text), NFKC)
				ORDER BY
				  (normalize(COALESCE(country, ''), NFKC) = normalize(COALESCE(CAST(:country AS text), ''), NFKC)) DESC,
				  (COALESCE(latitude, latitude_en) IS NOT NULL) DESC,
				  valid_from DESC NULLS LAST,
				  id DESC
				LIMIT 1
				""";
		return first(masterJdbcTemplate.query(sql,
				new MapSqlParameterSource().addValue("team", team).addValue("country", country), MAPPER));
	}

	private static MatchVenueDTO first(List<MatchVenueDTO> list) {
		return list.isEmpty() ? null : list.get(0);
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}
}
