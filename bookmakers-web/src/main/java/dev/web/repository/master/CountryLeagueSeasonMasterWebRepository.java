package dev.web.repository.master;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.common.util.DateOffsetDecisionUtil;
import dev.web.api.bm_a002.CountryLeagueSeasonDTO;
import dev.web.api.bm_a002.CountryLeagueSeasonSearchCondition;

/**
 * CountryLeagueSeasonMasterRepository
 *
 * <p>2026-10 修正</p>
 * <ul>
 *   <li>search: getOffsetDateTime に列の値ではなく列名を渡す
 *       （値を列名として探して「The column name 2026-01-02 00:00:00+00 was not found」→ bad SQL grammar になっていた）。</li>
 *   <li>updateRow: start_season_date / end_season_date（timestamptz）に文字列をそのまま入れていたため、
 *       CAST(NULLIF(:x, '') AS timestamptz) に変更（空文字は NULL）。</li>
 *   <li>2026-10-24 findSeasonPeriods 追加（データカテゴリ統一管理のシーズン期間判定用。日本時間の日付で返す）。</li>
 *   <li>2026-10-27 findEndSeasonDateMap 追加（b025_fin_season_all_data.json 出力用。国-リーグ → 終了日時）。</li>
 * </ul>
 * @author shiraishitoshio
 */
@Repository
public class CountryLeagueSeasonMasterWebRepository {

	private final NamedParameterJdbcTemplate masterJdbcTemplate;

	public CountryLeagueSeasonMasterWebRepository(
			@Qualifier("webMasterJdbcTemplate") NamedParameterJdbcTemplate masterJdbcTemplate) {
		this.masterJdbcTemplate = masterJdbcTemplate;
	}

	// ---- 全件 ----
	public List<CountryLeagueSeasonDTO> findAll() {
		StringBuilder sql = new StringBuilder("""
				    SELECT
				      id,
				      country,
				      league,
				      start_season_date,
				      end_season_date,
				      season_year,
				      round,
				      path,
				      del_flg
				    FROM country_league_season_master
				    WHERE
				      del_flg = :delFlg
				    ORDER BY country
				""");

		MapSqlParameterSource params = new MapSqlParameterSource();
		params.addValue("delFlg", "0");

		return masterJdbcTemplate.query(sql.toString(), params, (rs, n) -> {
			CountryLeagueSeasonDTO dto = new CountryLeagueSeasonDTO();
			dto.setId(rs.getString("id"));
			dto.setCountry(rs.getString("country"));
			dto.setLeague(rs.getString("league"));
			dto.setStartSeasonDate(rs.getString("start_season_date"));
			dto.setEndSeasonDate(rs.getString("end_season_date"));
			dto.setSeasonYear(rs.getString("season_year"));
			dto.setRound(rs.getString("round"));
			dto.setPath(rs.getString("path"));
			dto.setDelFlg(rs.getString("del_flg"));
			return dto;
		});
	}

	// ---- 条件検索（IF）----
	public List<CountryLeagueSeasonDTO> search(CountryLeagueSeasonSearchCondition cond) {

		StringBuilder sql = new StringBuilder("""
				    SELECT
				      id,
				      country,
				      league,
				      start_season_date,
				      end_season_date,
				      season_year,
				      round,
				      path,
				      icon,
				      del_flg
				    FROM country_league_season_master
				    WHERE 1=1
				""");

		MapSqlParameterSource params = new MapSqlParameterSource();

		if (hasText(cond.getCountry())) {
			sql.append(" AND country = :country ");
			params.addValue("country", cond.getCountry());
		}
		if (hasText(cond.getLeague())) {
			sql.append(" AND league = :league ");
			params.addValue("league", cond.getLeague());
		}
		if (hasText(cond.getSeasonYear())) {
			sql.append(" AND season_year = :seasonYear ");
			params.addValue("seasonYear", cond.getSeasonYear());
		}
		if (hasText(cond.getPath())) {
			// 部分一致
			sql.append(" AND path LIKE :path ");
			params.addValue("path", "%" + cond.getPath() + "%");
		}
		if (hasText(cond.getDelFlg())) {
			sql.append(" AND del_flg = :delFlg ");
			params.addValue("delFlg", cond.getDelFlg());
		}

		sql.append(" ORDER BY country ASC ");

		return masterJdbcTemplate.query(sql.toString(), params, (rs, n) -> {
			CountryLeagueSeasonDTO dto = new CountryLeagueSeasonDTO();
			dto.setId(rs.getString("id"));
			dto.setCountry(rs.getString("country"));
			dto.setLeague(rs.getString("league"));
			// 列名を渡す（値ではない）
			OffsetDateTime fts = DateOffsetDecisionUtil.getOffsetDateTime(rs, "start_season_date");
			dto.setStartSeasonDate(DateOffsetDecisionUtil.toIsoJstString(fts));
			OffsetDateTime fte = DateOffsetDecisionUtil.getOffsetDateTime(rs, "end_season_date");
			dto.setEndSeasonDate(DateOffsetDecisionUtil.toIsoJstString(fte));
			dto.setSeasonYear(rs.getString("season_year"));
			dto.setRound(rs.getString("round"));
			dto.setPath(rs.getString("path"));
			dto.setIcon(rs.getString("icon"));
			dto.setDelFlg(rs.getString("del_flg"));
			return dto;
		});
	}

	// ---- 更新（path更新）----
	public int updatePath(String country, String league, String seasonYear, String path) {
		String sql = """
				    UPDATE country_league_season_master
				    SET
				      path = :path,
				      season_year = :seasonYear,
				      del_flg = '0'
				    WHERE
				      country = :country
				      AND league = :league
				""";

		return masterJdbcTemplate.update(
				sql,
				new MapSqlParameterSource()
						.addValue("country", country)
						.addValue("league", league)
						.addValue("seasonYear", seasonYear)
						.addValue("path", path));
	}

	// ---- path重複チェック（自分自身は除外）----
	public boolean existsPathOtherThanKey(String country, String league, String seasonYear, String path) {
		String sql = """
				    SELECT COUNT(*)
				    FROM country_league_season_master
				    WHERE path = :path
				      AND NOT (
				           country = :country
				       AND league  = :league
				       AND season_year = :seasonYear
				      )
				""";

		Integer cnt = masterJdbcTemplate.queryForObject(
				sql,
				new MapSqlParameterSource()
						.addValue("country", country)
						.addValue("league", league)
						.addValue("seasonYear", seasonYear)
						.addValue("path", path),
				Integer.class);

		return cnt != null && cnt > 0;
	}

	/**
	 * del_flgを更新する。更新対象が存在しない場合は 0 を返却する。
	 */
	public int updateDelFlg(String country, String league, String del_flg) {
		String sql = """
				    UPDATE country_league_season_master
				    SET
				      del_flg = :del_flg
				    WHERE
				      country = :country
				      AND league = :league
				""";

		return masterJdbcTemplate.update(
				sql,
				new MapSqlParameterSource()
						.addValue("country", country)
						.addValue("league", league)
						.addValue("del_flg", del_flg));
	}

	/** 更新 */
	public int updateById(String id, String country, String league, String seasonYear, String path, String delFlg) {
		String sql = """
				    UPDATE country_league_season_master
				    SET
				      country = :country,
				      league = :league,
				      season_year = :seasonYear,
				      path = :path,
				      del_flg = :delFlg
				    WHERE id = CAST(:id AS integer)
				""";

		return masterJdbcTemplate.update(sql,
				new MapSqlParameterSource()
						.addValue("id", id)
						.addValue("country", country)
						.addValue("league", league)
						.addValue("seasonYear", seasonYear)
						.addValue("path", path)
						.addValue("delFlg", delFlg));
	}

	/**
	 * country_league_season_masterとpoint_setting_masterとのdel_flg同期用
	 */
	public int updateDelFlgByCountryAndLeague(String country, String league, String delFlg) {

		String sql = """
				UPDATE country_league_season_master
				SET del_flg     = :delFlg,
				    update_id   = 'SYSTEM',
				    update_time = NOW()
				WHERE country = :country
				  AND league  = :league
				""";

		Map<String, Object> params = Map.of(
				"country", country,
				"league", league,
				"delFlg", delFlg == null || delFlg.isBlank() ? "0" : delFlg.trim());

		return masterJdbcTemplate.update(sql, params);
	}

	public String findCurrentSeasonYear(String country, String league) {
		String sql = """
				    SELECT season_year
				    FROM country_league_season_master
				      WHERE country = :country
				      AND league  = :league
				      AND del_flg = '0'
				      AND NOW() BETWEEN start_season_date AND end_season_date
				    ORDER BY start_season_date DESC
				    LIMIT 1
				""";

		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("country", country)
				.addValue("league", league);

		List<String> list = masterJdbcTemplate.queryForList(sql, params, String.class);
		return list.isEmpty() ? null : list.get(0);
	}

	/**
	 * 更新（初回読み込み確認モーダル）。
	 * 日付は "2026-01-02T09:00+09:00" / "2026-01-02 00:00:00+00" など timestamptz に変換できる文字列。空文字は NULL。
	 */
	public int updateRow(Integer id, String country, String league,
			String seasonYear, String startSeasonDate, String endSeasonDate,
			String round) {
		String sql = """
				    UPDATE country_league_season_master
				    SET
				      country = :country,
				      league = :league,
				      season_year = :seasonYear,
				      start_season_date = CAST(NULLIF(BTRIM(CAST(:startSeasonDate AS text)), '') AS timestamptz),
				      end_season_date   = CAST(NULLIF(BTRIM(CAST(:endSeasonDate AS text)), '') AS timestamptz),
				      round = NULLIF(BTRIM(CAST(:round AS text)), ''),
				      update_id   = 'SYSTEM',
				      update_time = NOW()
				    WHERE id = :id
				""";

		return masterJdbcTemplate.update(sql,
				new MapSqlParameterSource()
						.addValue("id", id)
						.addValue("country", country)
						.addValue("league", league)
						.addValue("seasonYear", seasonYear)
						.addValue("startSeasonDate", startSeasonDate)
						.addValue("endSeasonDate", endSeasonDate)
						.addValue("round", round));
	}

	/** 削除 */
	/**
	 * 新規データ確認モーダルの「選択行を削除」。
	 * 2026-10-31: 物理削除（DELETE）から論理削除（del_flg = '1'）に変更。
	 * まとめて選んで押すと、今シーズンのシーズン行がすべて消えてダンプから戻すしかなくなっていたため。
	 * 論理削除した行は、次にシーズンを取得したとき B003 が同じ行を使う（非表示のまま更新される）。
	 */
	public int deleteRow(Integer id) {
		String sql = """
				    UPDATE country_league_season_master
				    SET del_flg = '1',
				        update_time = CURRENT_TIMESTAMP
				    WHERE id = :id
				      AND del_flg = '0'
				""";

		return masterJdbcTemplate.update(
				sql,
				new MapSqlParameterSource().addValue("id", id));
	}

	/** シーズン期間（日本時間の日付）。データカテゴリ統一管理のシーズン期間判定用 */
	public static class SeasonPeriodRow {
		private final String country;
		private final String league;
		private final LocalDate startDate;
		private final LocalDate endDate;

		public SeasonPeriodRow(String country, String league, LocalDate startDate, LocalDate endDate) {
			this.country = country;
			this.league = league;
			this.startDate = startDate;
			this.endDate = endDate;
		}

		public String getCountry() {
			return country;
		}

		public String getLeague() {
			return league;
		}

		/** 開始日（日本時間）。未設定は null */
		public LocalDate getStartDate() {
			return startDate;
		}

		/** 終了日（日本時間）。未設定は null */
		public LocalDate getEndDate() {
			return endDate;
		}
	}

	/**
	 * 有効（del_flg = '0'）なシーズンの 国・リーグ・開始日・終了日 を返す。
	 * start_season_date / end_season_date（timestamptz）は日本時間の日付にして返す（未設定は null）。
	 */
	public List<SeasonPeriodRow> findSeasonPeriods() {
		String sql = """
				    SELECT
				      country,
				      league,
				      to_char(start_season_date AT TIME ZONE 'Asia/Tokyo', 'YYYY-MM-DD') AS start_date,
				      to_char(end_season_date   AT TIME ZONE 'Asia/Tokyo', 'YYYY-MM-DD') AS end_date
				    FROM country_league_season_master
				    WHERE del_flg = '0'
				""";
		return masterJdbcTemplate.query(sql, new MapSqlParameterSource(), (rs, n) -> new SeasonPeriodRow(
				rs.getString("country"),
				rs.getString("league"),
				toDate(rs.getString("start_date")),
				toDate(rs.getString("end_date"))));
	}

	/**
	 * 有効（del_flg = '0'）なシーズンの「国-リーグ → 終了日時（日本時間）」を返す。
	 * キーは b025_fin_season_data.json と同じ "国-リーグ" 形式。
	 * 終了日時は "yyyy-MM-dd HH:mm:ss+09"（画面表示と同じ形式）。終了日が無いリーグは値が null。
	 * 同じ国リーグが複数行ある場合は、終了日時が一番新しいものを残す。
	 */
	public Map<String, String> findEndSeasonDateMap() {
		String sql = """
				    SELECT
				      country,
				      league,
				      to_char(end_season_date AT TIME ZONE 'Asia/Tokyo', 'YYYY-MM-DD HH24:MI:SS') || '+09' AS end_date
				    FROM country_league_season_master
				    WHERE del_flg = '0'
				    ORDER BY country, league, end_season_date NULLS FIRST
				""";
		Map<String, String> map = new LinkedHashMap<>();
		masterJdbcTemplate.query(sql, new MapSqlParameterSource(), (RowCallbackHandler) rs -> {
			String key = rs.getString("country") + "-" + rs.getString("league");
			String endDate = rs.getString("end_date");
			if (endDate != null || !map.containsKey(key)) {
				map.put(key, endDate);
			}
		});
		return map;
	}

	private static LocalDate toDate(String s) {
		return s == null || s.isBlank() ? null : LocalDate.parse(s.trim());
	}

	private boolean hasText(String s) {
		return s != null && !s.isBlank();
	}
}