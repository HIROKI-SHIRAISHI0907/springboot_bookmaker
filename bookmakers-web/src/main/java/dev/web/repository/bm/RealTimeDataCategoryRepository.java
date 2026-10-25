package dev.web.repository.bm;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.web.api.bm_a025.RealTimeDataDTO;

/**
 * リアルタイムデータ管理画面（dataCategory 確認・上書き）用の static_data 取得
 * <p>BookDataRepository#findAllGrouping と同じ集計を、home/away の絞り込み付きでも使えるようにしたもの。
 * （以前の search は1件しか返さず、形式の判定も付いていなかった）</p>
 * <p>2026-10-24: グループごとの最初・最後の記録日時（record_time。日本時間の timestamp）も返す（シーズン期間の判定用）。
 * 手動取得（add_manual_flg = '1'。終了済が取れなかった試合を別日に取り直した行）は record_time が取得した日なので、
 * 試合中に記録した行（min/max_record_time）と分けて返す（min/max_manual_record_time）。</p>
 * @author shiraishitoshio
 *
 */
@Repository
public class RealTimeDataCategoryRepository {

	private final NamedParameterJdbcTemplate bmJdbcTemplate;

	public RealTimeDataCategoryRepository(
			@Qualifier("bmJdbcTemplate") NamedParameterJdbcTemplate bmJdbcTemplate) {
		this.bmJdbcTemplate = bmJdbcTemplate;
	}

	/** データ形式判定の正規表現を CASE 式として埋め込むための共通 SQL 断片 */
	private static final String CATEGORY_FORMAT_CASE = """
			CASE
			    WHEN data_category ~ '^[^:]+:\\s.+\\s-\\s*ラウンド\\s+\\d+$' THEN 'COUNTRY_LEAGUE_ROUND'
			    WHEN data_category ~ '^[^:]+\\s-\\s*ラウンド\\s+\\d+$'      THEN 'LEAGUE_ROUND'
			    WHEN data_category ~ '^[^:]+:\\s[^:]+$'                    THEN 'COUNTRY_LEAGUE'
			    ELSE NULL
			END
			""";

	/**
	 * dataCategory × home × away でグルーピングした件数と、home/away 単位の形式判定を取得する。
	 *
	 * @param homeTeamName 絞り込むホームチーム名（null・空なら絞り込まない。NFKC 正規化して比較）
	 * @param awayTeamName 絞り込むアウェーチーム名（同上）
	 */
	public List<RealTimeDataDTO> findGrouping(String homeTeamName, String awayTeamName) {
		boolean filterHome = homeTeamName != null && !homeTeamName.isBlank();
		boolean filterAway = awayTeamName != null && !awayTeamName.isBlank();

		StringBuilder where = new StringBuilder();
		MapSqlParameterSource params = new MapSqlParameterSource();
		if (filterHome) {
			where.append(" AND normalize(home_team_name, NFKC) = normalize(:homeTeamName, NFKC)");
			params.addValue("homeTeamName", homeTeamName.trim());
		}
		if (filterAway) {
			where.append(" AND normalize(away_team_name, NFKC) = normalize(:awayTeamName, NFKC)");
			params.addValue("awayTeamName", awayTeamName.trim());
		}

		String sql = """
				WITH classified AS (
				    SELECT
				        data_category,
				        home_team_name,
				        away_team_name,
				        record_time,
				        (BTRIM(CAST(add_manual_flg AS text)) = '1') AS is_manual,
				        %s AS category_format
				    FROM static_data
				    WHERE 1 = 1 %s
				),
				grouped AS (
				    SELECT
				        data_category,
				        home_team_name,
				        away_team_name,
				        category_format,
				        COUNT(*) AS cnt,
				        MIN(record_time) FILTER (WHERE is_manual IS NOT TRUE) AS min_record_time,
				        MAX(record_time) FILTER (WHERE is_manual IS NOT TRUE) AS max_record_time,
				        MIN(record_time) FILTER (WHERE is_manual) AS min_manual_record_time,
				        MAX(record_time) FILTER (WHERE is_manual) AS max_manual_record_time
				    FROM classified
				    GROUP BY data_category, home_team_name, away_team_name, category_format
				),
				format_consistency AS (
				    SELECT
				        home_team_name,
				        away_team_name,
				        CASE
				            WHEN COUNT(DISTINCT COALESCE(category_format, '__NONE__')) = 1
				            THEN '同一カテゴリ名'
				            ELSE '混在'
				        END AS category_format_icon
				    FROM classified
				    GROUP BY home_team_name, away_team_name
				)
				SELECT
				    g.data_category,
				    CASE WHEN g.category_format IS NOT NULL THEN g.data_category ELSE NULL END AS formatted_data_category,
				    g.home_team_name,
				    g.away_team_name,
				    g.cnt,
				    to_char(g.min_record_time, 'YYYY-MM-DD HH24:MI:SS') AS min_record_time,
				    to_char(g.max_record_time, 'YYYY-MM-DD HH24:MI:SS') AS max_record_time,
				    to_char(g.min_manual_record_time, 'YYYY-MM-DD HH24:MI:SS') AS min_manual_record_time,
				    to_char(g.max_manual_record_time, 'YYYY-MM-DD HH24:MI:SS') AS max_manual_record_time,
				    fc.category_format_icon
				FROM grouped g
				JOIN format_consistency fc
				    ON g.home_team_name = fc.home_team_name
				   AND g.away_team_name = fc.away_team_name
				""".formatted(CATEGORY_FORMAT_CASE, where);

		return bmJdbcTemplate.query(sql, params, (rs, rowNum) -> {
			RealTimeDataDTO dto = new RealTimeDataDTO();
			dto.setDataCategory(rs.getString("data_category"));
			dto.setFormattedDataCategory(rs.getString("formatted_data_category"));
			dto.setHomeTeamName(rs.getString("home_team_name"));
			dto.setAwayTeamName(rs.getString("away_team_name"));
			dto.setCnt(rs.getLong("cnt"));
			dto.setCategoryFormatIcon(rs.getString("category_format_icon"));
			dto.setMinRecordTime(rs.getString("min_record_time"));
			dto.setMaxRecordTime(rs.getString("max_record_time"));
			dto.setMinManualRecordTime(rs.getString("min_manual_record_time"));
			dto.setMaxManualRecordTime(rs.getString("max_manual_record_time"));
			return dto;
		});
	}
}
