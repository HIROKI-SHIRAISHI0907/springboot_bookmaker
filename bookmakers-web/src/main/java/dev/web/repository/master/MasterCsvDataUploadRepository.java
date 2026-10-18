package dev.web.repository.master;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * CSV アップロード用リポジトリ（webMaster DB）
 *
 * <ul>
 *   <li>country_league_season_master（シーズン） / country_league_master（チーム） / future_master（試合予定）</li>
 *   <li>更新は「CSV で値が入っている列だけ」SET する（空欄は変更しない）。SQL は呼び出し側で列を選んで組み立てる。</li>
 *   <li>数値列は CAST(:x AS numeric) で渡す（列が integer でも text でも代入できる）。</li>
 *   <li>日時は日本時間の文字列で受け取り、timestamptz にして保存する。</li>
 * </ul>
 *
 * @author shiraishitoshio
 */
@Repository
public class MasterCsvDataUploadRepository {

	/** 登録者・更新者 */
	public static final String CSV_USER = "CSV_UPLOAD";

	private final NamedParameterJdbcTemplate masterJdbcTemplate;

	/** webMaster DB 用のトランザクション（DataSource が複数あるため、この JdbcTemplate の DataSource で作る） */
	private final TransactionTemplate transactionTemplate;

	public MasterCsvDataUploadRepository(
			@Qualifier("webMasterJdbcTemplate") NamedParameterJdbcTemplate masterJdbcTemplate) {
		this.masterJdbcTemplate = masterJdbcTemplate;
		this.transactionTemplate = new TransactionTemplate(
				new DataSourceTransactionManager(masterJdbcTemplate.getJdbcTemplate().getDataSource()));
	}

	/**
	 * 1 つのトランザクションで実行する（例外が出たら全部戻す）
	 * @param work 処理
	 */
	public void inTransaction(Runnable work) {
		transactionTemplate.executeWithoutResult(status -> work.run());
	}

	// =========================================================
	// 共通
	// =========================================================

	/** 対象テーブル */
	public enum Table {
		SEASON("country_league_season_master", "id", "integer"),
		TEAM("country_league_master", "id", "integer"),
		FUTURE("future_master", "seq", "bigint");

		final String name;
		final String idColumn;
		final String idType;

		Table(String name, String idColumn, String idType) {
			this.name = name;
			this.idColumn = idColumn;
			this.idType = idType;
		}
	}

	/** ID の存在確認 */
	public boolean existsById(Table t, long id) {
		String sql = "SELECT COUNT(*) FROM " + t.name
				+ " WHERE " + t.idColumn + " = CAST(:id AS " + t.idType + ")";
		Integer cnt = masterJdbcTemplate.queryForObject(sql,
				new MapSqlParameterSource("id", id), Integer.class);
		return cnt != null && cnt > 0;
	}

	/** 物理削除 */
	public int deleteById(Table t, long id) {
		String sql = "DELETE FROM " + t.name
				+ " WHERE " + t.idColumn + " = CAST(:id AS " + t.idType + ")";
		return masterJdbcTemplate.update(sql, new MapSqlParameterSource("id", id));
	}

	/** 非表示（'1'）/ 再表示（'0'） */
	public int updateDelFlg(Table t, long id, String delFlg) {
		String sql = "UPDATE " + t.name
				+ " SET del_flg = :delFlg, update_id = :user, update_time = CURRENT_TIMESTAMP"
				+ " WHERE " + t.idColumn + " = CAST(:id AS " + t.idType + ")";
		return masterJdbcTemplate.update(sql, new MapSqlParameterSource()
				.addValue("delFlg", delFlg)
				.addValue("user", CSV_USER)
				.addValue("id", id));
	}

	/**
	 * 値が入っている列だけを UPDATE する
	 * @param t テーブル
	 * @param id 対象 ID
	 * @param sets 列名 → SQL の式（パラメータ名は列名と同じ :列名 を使う）
	 * @param values 列名 → 値
	 * @return 更新件数（SET する列が無ければ 0 のまま update_time だけ更新）
	 */
	public int updateColumns(Table t, long id, Map<String, String> sets, Map<String, Object> values) {
		StringBuilder sql = new StringBuilder("UPDATE ").append(t.name).append(" SET ");
		List<String> parts = new ArrayList<>();
		sets.forEach((col, expr) -> parts.add(col + " = " + expr));
		parts.add("update_id = :user");
		parts.add("update_time = CURRENT_TIMESTAMP");
		sql.append(String.join(", ", parts));
		sql.append(" WHERE ").append(t.idColumn).append(" = CAST(:id AS ").append(t.idType).append(")");

		MapSqlParameterSource p = new MapSqlParameterSource(values)
				.addValue("user", CSV_USER)
				.addValue("id", id);
		return masterJdbcTemplate.update(sql.toString(), p);
	}

	/** SET 用の式（文字列） */
	public static String exprText(String col) {
		return ":" + col;
	}

	/** SET 用の式（数値） */
	public static String exprNumber(String col) {
		return "CAST(:" + col + " AS numeric)";
	}

	/** SET 用の式（日時。値は yyyy-MM-dd'T'HH:mm:ss+09:00） */
	public static String exprTimestamp(String col) {
		return "CAST(:" + col + " AS timestamptz)";
	}

	/** 列 → 式 のマップを作る（順番を保つ） */
	public static Map<String, String> newSets() {
		return new LinkedHashMap<>();
	}

	// =========================================================
	// シーズン（country_league_season_master）
	// =========================================================

	/** 国 + リーグで ID を探す（del_flg は問わない） */
	public List<Long> findSeasonIds(String country, String league) {
		String sql = """
				SELECT id
				  FROM country_league_season_master
				 WHERE normalize(country, NFKC) = normalize(:country, NFKC)
				   AND normalize(league, NFKC) = normalize(:league, NFKC)
				 ORDER BY id
				""";
		return masterJdbcTemplate.queryForList(sql, new MapSqlParameterSource()
				.addValue("country", country)
				.addValue("league", league), Long.class);
	}

	/** ID から国・リーグを取る（point_setting_master の del_flg を合わせるため） */
	public String[] findSeasonCountryLeague(long id) {
		String sql = "SELECT country, league FROM country_league_season_master WHERE id = CAST(:id AS integer)";
		List<String[]> list = masterJdbcTemplate.query(sql, new MapSqlParameterSource("id", id),
				(rs, i) -> new String[] { rs.getString("country"), rs.getString("league") });
		return list.isEmpty() ? null : list.get(0);
	}

	/** シーズン登録 */
	public int insertSeason(Map<String, Object> v) {
		String sql = """
				INSERT INTO country_league_season_master (
				    country, league, season_year, start_season_date, end_season_date,
				    round, path, icon, valid_flg, del_flg,
				    register_id, register_time, update_id, update_time
				) VALUES (
				    :country, :league, NULLIF(:season_year, ''),
				    CAST(NULLIF(:start_season_date, '') AS timestamptz),
				    CAST(NULLIF(:end_season_date, '') AS timestamptz),
				    NULLIF(:round, ''), NULLIF(:path, ''), NULLIF(:icon, ''),
				    :valid_flg, '0',
				    :user, CURRENT_TIMESTAMP, :user, CURRENT_TIMESTAMP
				)
				""";
		return masterJdbcTemplate.update(sql, new MapSqlParameterSource(v).addValue("user", CSV_USER));
	}

	/** point_setting_master の del_flg を国・リーグで合わせる（既存の id 指定更新と同じ動き） */
	public int updatePointSettingDelFlg(String country, String league, String delFlg) {
		String sql = """
				UPDATE point_setting_master
				   SET del_flg = :delFlg, update_id = :user, update_time = CURRENT_TIMESTAMP
				 WHERE country = :country
				   AND league = :league
				""";
		return masterJdbcTemplate.update(sql, new MapSqlParameterSource()
				.addValue("delFlg", delFlg)
				.addValue("user", CSV_USER)
				.addValue("country", country)
				.addValue("league", league));
	}

	// =========================================================
	// チーム（country_league_master）
	// =========================================================

	/** 国 + リーグ + チームで ID を探す（del_flg は問わない） */
	public List<Long> findTeamIds(String country, String league, String team) {
		String sql = """
				SELECT id
				  FROM country_league_master
				 WHERE normalize(country, NFKC) = normalize(:country, NFKC)
				   AND normalize(league, NFKC) = normalize(:league, NFKC)
				   AND normalize(team, NFKC) = normalize(:team, NFKC)
				 ORDER BY id
				""";
		return masterJdbcTemplate.queryForList(sql, new MapSqlParameterSource()
				.addValue("country", country)
				.addValue("league", league)
				.addValue("team", team), Long.class);
	}

	/** チーム登録 */
	public int insertTeam(Map<String, Object> v) {
		String sql = """
				INSERT INTO country_league_master (
				    country, league, team, link, del_flg,
				    register_id, register_time, update_id, update_time
				) VALUES (
				    :country, :league, :team, NULLIF(:link, ''), '0',
				    :user, CURRENT_TIMESTAMP, :user, CURRENT_TIMESTAMP
				)
				""";
		return masterJdbcTemplate.update(sql, new MapSqlParameterSource(v).addValue("user", CSV_USER));
	}

	// =========================================================
	// 試合予定（future_master）
	// =========================================================

	/** ホーム + アウェー + 試合国及びカテゴリで seq を探す（del_flg は問わない） */
	public List<Long> findFutureSeqs(String homeTeamName, String awayTeamName, String gameTeamCategory) {
		String sql = """
				SELECT seq
				  FROM future_master
				 WHERE normalize(home_team_name, NFKC) = normalize(:home, NFKC)
				   AND normalize(away_team_name, NFKC) = normalize(:away, NFKC)
				   AND normalize(BTRIM(game_team_category), NFKC) = normalize(BTRIM(:category), NFKC)
				 ORDER BY seq
				""";
		return masterJdbcTemplate.queryForList(sql, new MapSqlParameterSource()
				.addValue("home", homeTeamName)
				.addValue("away", awayTeamName)
				.addValue("category", gameTeamCategory), Long.class);
	}

	/** 試合予定登録（start_flg は既存のスクレイピング登録と同じ '1'） */
	public int insertFuture(Map<String, Object> v) {
		String sql = """
				INSERT INTO future_master (
				    game_team_category, future_time,
				    home_rank, away_rank, home_team_name, away_team_name,
				    home_max_getting_scorer, away_max_getting_scorer,
				    home_team_home_score, home_team_home_lost,
				    away_team_home_score, away_team_home_lost,
				    home_team_away_score, home_team_away_lost,
				    away_team_away_score, away_team_away_lost,
				    game_link, data_time, start_flg, del_flg,
				    register_id, register_time, update_id, update_time
				) VALUES (
				    :game_team_category, CAST(:future_time AS timestamptz),
				    CAST(NULLIF(:home_rank, '') AS numeric), CAST(NULLIF(:away_rank, '') AS numeric),
				    :home_team_name, :away_team_name,
				    NULLIF(:home_max_getting_scorer, ''), NULLIF(:away_max_getting_scorer, ''),
				    CAST(NULLIF(:home_team_home_score, '') AS numeric), CAST(NULLIF(:home_team_home_lost, '') AS numeric),
				    CAST(NULLIF(:away_team_home_score, '') AS numeric), CAST(NULLIF(:away_team_home_lost, '') AS numeric),
				    CAST(NULLIF(:home_team_away_score, '') AS numeric), CAST(NULLIF(:home_team_away_lost, '') AS numeric),
				    CAST(NULLIF(:away_team_away_score, '') AS numeric), CAST(NULLIF(:away_team_away_lost, '') AS numeric),
				    NULLIF(:game_link, ''), CAST(NULLIF(:data_time, '') AS timestamptz), '1', '0',
				    :user, CURRENT_TIMESTAMP, :user, CURRENT_TIMESTAMP
				)
				""";
		return masterJdbcTemplate.update(sql, new MapSqlParameterSource(v).addValue("user", CSV_USER));
	}
}
