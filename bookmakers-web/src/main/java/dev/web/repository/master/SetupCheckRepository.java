package dev.web.repository.master;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 管理画面の未対応チェック（bm_a035）用リポジトリ（soccer_bm_master）
 * @author shiraishitoshio
 */
@Repository
public class SetupCheckRepository {

	private final NamedParameterJdbcTemplate masterJdbcTemplate;

	public SetupCheckRepository(
			@Qualifier("webMasterJdbcTemplate") NamedParameterJdbcTemplate masterJdbcTemplate) {
		this.masterJdbcTemplate = masterJdbcTemplate;
	}

	/**
	 * 新規登録で、まだ画面で確認していない国・リーグ（initial_reading_csv_master の initial_flg = '0'）。
	 * 確認画面（モーダル）に出せるもの = 対応するマスタに有効な行（del_flg = '0'）がある国・リーグだけを数える。
	 * マスタに行が無い・非表示（del_flg = '1'）の国・リーグは、モーダルに出ず確認も削除もできないので数えない
	 * （数えると、解消する手段が無いまま管理画面がブロックされ続けるため）。
	 *
	 * @param masterName master_name（MasterNameConstant の値。テーブル名と同じ）
	 * @return 「国 / リーグ」の一覧
	 */
	public List<String> findInitialPending(String masterName) {
		String table = masterTable(masterName);
		String sql = """
				SELECT i.country, i.league
				FROM initial_reading_csv_master i
				WHERE i.master_name = :masterName
				  AND i.initial_flg = '0'
				  AND EXISTS (
				    SELECT 1 FROM """ + " " + table + """
				 m
				    WHERE m.country = i.country AND m.league = i.league AND m.del_flg = '0'
				  )
				ORDER BY i.country, i.league
				""";
		return masterJdbcTemplate.query(sql, new MapSqlParameterSource("masterName", masterName),
				(rs, n) -> rs.getString("country") + " / " + rs.getString("league"));
	}

	/**
	 * 確認画面に出せない「未確認」（マスタに有効な行が無い国・リーグ）。ログ・調査用。
	 */
	public List<String> findInitialOrphans(String masterName) {
		String table = masterTable(masterName);
		String sql = """
				SELECT i.country, i.league
				FROM initial_reading_csv_master i
				WHERE i.master_name = :masterName
				  AND i.initial_flg = '0'
				  AND NOT EXISTS (
				    SELECT 1 FROM """ + " " + table + """
				 m
				    WHERE m.country = i.country AND m.league = i.league AND m.del_flg = '0'
				  )
				ORDER BY i.country, i.league
				""";
		return masterJdbcTemplate.query(sql, new MapSqlParameterSource("masterName", masterName),
				(rs, n) -> rs.getString("country") + " / " + rs.getString("league"));
	}

	/** master_name → 対応するマスタのテーブル名（SQL に埋め込むので、del_flg を持つ決まったテーブルだけ許す） */
	private static String masterTable(String masterName) {
		if ("country_league_season_master".equals(masterName) || "country_league_master".equals(masterName)) {
			return masterName;
		}
		throw new IllegalArgumentException("未対応の master_name: " + masterName);
	}

	/**
	 * チームの重複: 同じ国の有効なチーム（del_flg = '0'）で、リンクの「/team/xxx/yyy/」が同じ行が2行以上あるもの。
	 * リーグ所属チーム付け替え設定（LeagueTeamService）と同じ判定。
	 * @return 「国: チーム名 チームが Aリーグ と Bリーグ に重複しています。」の一覧
	 */
	public List<String> findTeamDuplicates() {
		String sql = """
				WITH t AS (
				  SELECT id, country, league, team,
				         lower(substring(link FROM '/team/[^/?#]+/[^/?#]+')) AS lk
				  FROM country_league_master
				  WHERE del_flg = '0'
				)
				SELECT country,
				       (array_agg(team ORDER BY id DESC))[1] AS team,
				       string_agg(league, ' と ' ORDER BY id) AS leagues
				FROM t
				WHERE lk IS NOT NULL
				GROUP BY country, lk
				HAVING COUNT(*) > 1
				ORDER BY country, lk
				""";
		return masterJdbcTemplate.query(sql, new MapSqlParameterSource(),
				(rs, n) -> rs.getString("country") + ": " + rs.getString("team") + " チームが "
						+ rs.getString("leagues") + " に重複しています。");
	}
}
