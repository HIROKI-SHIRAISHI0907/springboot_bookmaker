package dev.web.repository.bm;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.common.util.DateOffsetDecisionUtil;
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

	/** 列に :param を含むか（NFKC 正規化・大文字小文字を区別しない部分一致） */
	private static String contains(String column, String param) {
		return "lower(normalize(" + column + ", NFKC)) LIKE '%' || lower(normalize(:" + param + ", NFKC)) || '%' ESCAPE '\\'";
	}

	/** LIKE のワイルドカード（\ % _）をエスケープした検索語 */
	private static String likeValue(String s) {
		return s.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

	/**
	 * dataCategory × home × away でグルーピングした件数と、home/away 単位の形式判定を取得する。
	 *
	 * <p>2026-11-18 変更: 完全一致 → 部分一致（NFKC 正規化・大文字小文字を区別しない）。teamName を追加。</p>
	 *
	 * @param homeTeamName 絞り込むホームチーム名（null・空なら絞り込まない。部分一致）
	 * @param awayTeamName 絞り込むアウェーチーム名（同上）
	 * @param teamName     ホーム・アウェーのどちらかに含むチーム名（同上）
	 */
	public List<RealTimeDataDTO> findGrouping(String homeTeamName, String awayTeamName, String teamName) {
		StringBuilder where = new StringBuilder();
		MapSqlParameterSource params = new MapSqlParameterSource();
		if (homeTeamName != null && !homeTeamName.isBlank()) {
			where.append(" AND ").append(contains("home_team_name", "homeTeamName"));
			params.addValue("homeTeamName", likeValue(homeTeamName));
		}
		if (awayTeamName != null && !awayTeamName.isBlank()) {
			where.append(" AND ").append(contains("away_team_name", "awayTeamName"));
			params.addValue("awayTeamName", likeValue(awayTeamName));
		}
		if (teamName != null && !teamName.isBlank()) {
			where.append(" AND (").append(contains("home_team_name", "teamName"))
					.append(" OR ").append(contains("away_team_name", "teamName")).append(")");
			params.addValue("teamName", likeValue(teamName));
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
				    g.min_record_time,
				    g.max_record_time,
				    g.min_manual_record_time,
				    g.max_manual_record_time,
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
			// 日本時間の「yyyy-MM-dd HH:mm:ss」（2026-11-04: to_char はセッションのタイムゾーンになるため Util に変更）
			dto.setMinRecordTime(DateOffsetDecisionUtil.getJstLocalDateTimeString(rs, "min_record_time"));
			dto.setMaxRecordTime(DateOffsetDecisionUtil.getJstLocalDateTimeString(rs, "max_record_time"));
			dto.setMinManualRecordTime(DateOffsetDecisionUtil.getJstLocalDateTimeString(rs, "min_manual_record_time"));
			dto.setMaxManualRecordTime(DateOffsetDecisionUtil.getJstLocalDateTimeString(rs, "max_manual_record_time"));
			return dto;
		});
	}
}
