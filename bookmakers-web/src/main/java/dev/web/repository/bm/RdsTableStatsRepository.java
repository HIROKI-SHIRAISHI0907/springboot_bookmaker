package dev.web.repository.bm;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

import dev.web.api.bm_a013.DashboardDtos.RdsDatabaseRef;
import dev.web.api.bm_a013.DashboardDtos.RdsTables;
import dev.web.api.bm_a013.DashboardDtos.TableCount;

/**
 * アプリに登録されている全 DataSource（bm / master / user など）について、
 * スキーマ別のテーブル一覧とレコード件数を取得する。
 *
 * - DataSource は Bean 名に関係なく自動で全部集める（Map&lt;String, DataSource&gt; 注入）
 * - 同じ DB を指す DataSource が複数あっても 1 つにまとめる（DB 名で重複排除）
 * - 件数は「推定値が maxExactRows 以下のテーブルだけ COUNT(*)」、それより大きいテーブルは推定値
 *   （巨大テーブルの COUNT(*) で DB に負荷をかけないため）
 *
 * PostgreSQL / MySQL(MariaDB) 対応。
 */
@Repository
public class RdsTableStatsRepository {

	/** スキーマ名・テーブル名はこのパターンのみ許可（SQL インジェクション対策） */
	private static final Pattern SAFE_NAME = Pattern.compile("^[A-Za-z0-9_$]+$");

	/** Bean 名 → DataSource（Bean 名順） */
	private final Map<String, DataSource> dataSources;

	public RdsTableStatsRepository(Map<String, DataSource> dataSources) {
		this.dataSources = new TreeMap<String, DataSource>(dataSources);
	}

	// =====================================================================
	// 接続先 DB 一覧
	// =====================================================================

	/** 接続先 DB（DB 名で重複排除・DB 名順） */
	public List<RdsDatabaseRef> databases() {
		List<RdsDatabaseRef> out = new ArrayList<RdsDatabaseRef>();
		Set<String> seen = new HashSet<String>();

		for (Map.Entry<String, DataSource> e : dataSources.entrySet()) {
			String beanName = e.getKey();
			try {
				String[] info = connectionInfo(e.getValue());
				String database = info[0];
				if (database == null || !seen.add(database)) {
					continue;
				}
				out.add(new RdsDatabaseRef(database, database, info[1], beanName, null));
			} catch (Exception ex) {
				// 接続できない DataSource もエラー付きで一覧に出す
				out.add(new RdsDatabaseRef(beanName, "(" + beanName + ")", null, beanName,
						ex.getClass().getSimpleName() + ": " + ex.getMessage()));
			}
		}

		Collections.sort(out, new Comparator<RdsDatabaseRef>() {
			@Override
			public int compare(RdsDatabaseRef a, RdsDatabaseRef b) {
				return a.getDatabase().compareTo(b.getDatabase());
			}
		});
		return out;
	}

	// =====================================================================
	// テーブル件数
	// =====================================================================

	/**
	 * 指定 DB の全スキーマのテーブル件数を取得する。
	 *
	 * @param databaseKey  databases() で返した key（= DB 名）
	 * @param excludes     除外テーブル（"table" または "schema.table"）
	 * @param exactEnabled false なら常に推定値
	 * @param maxExactRows 推定値がこの件数以下なら COUNT(*) で正確に数える
	 */
	public RdsTables tables(String databaseKey, List<String> excludes, boolean exactEnabled, long maxExactRows) {
		DataSource ds = findDataSource(databaseKey);
		if (ds == null) {
			throw new IllegalArgumentException("DB が見つかりません: " + databaseKey);
		}

		JdbcTemplate jdbc = new JdbcTemplate(ds);
		String[] info = connectionInfo(ds);
		String database = info[0];
		String product = info[1];
		boolean mysql = isMySql(product);

		Set<String> ex = new HashSet<String>();
		if (excludes != null) {
			for (String s : excludes) {
				ex.add(s.toLowerCase(Locale.ROOT));
			}
		}

		// 1) テーブル一覧と推定件数
		final List<String[]> names = new ArrayList<String[]>(); // [schema, table]
		final Map<String, Long> estimates = new HashMap<String, Long>(); // "schema.table" → 推定件数

		if (mysql) {
			jdbc.query("SELECT table_schema, table_name, COALESCE(table_rows, 0) FROM information_schema.tables "
					+ "WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'",
					new RowCallbackHandler() {
						@Override
						public void processRow(ResultSet rs) throws SQLException {
							names.add(new String[] { rs.getString(1), rs.getString(2) });
							estimates.put(rs.getString(1) + "." + rs.getString(2), rs.getLong(3));
						}
					});
		} else {
			jdbc.query("SELECT table_schema, table_name FROM information_schema.tables "
					+ "WHERE table_type = 'BASE TABLE' "
					+ "AND table_schema NOT IN ('pg_catalog', 'information_schema') "
					+ "AND table_schema NOT LIKE 'pg_toast%'",
					new RowCallbackHandler() {
						@Override
						public void processRow(ResultSet rs) throws SQLException {
							names.add(new String[] { rs.getString(1), rs.getString(2) });
						}
					});
			// n_live_tup（統計）と reltuples（ANALYZE 結果）の大きいほうを推定値にする
			jdbc.query("SELECT n.nspname, c.relname, "
					+ "GREATEST(COALESCE(s.n_live_tup, 0), COALESCE(c.reltuples, 0)::bigint) "
					+ "FROM pg_class c "
					+ "JOIN pg_namespace n ON n.oid = c.relnamespace "
					+ "LEFT JOIN pg_stat_user_tables s ON s.relid = c.oid "
					+ "WHERE c.relkind IN ('r', 'p')",
					new RowCallbackHandler() {
						@Override
						public void processRow(ResultSet rs) throws SQLException {
							estimates.put(rs.getString(1) + "." + rs.getString(2), rs.getLong(3));
						}
					});
		}

		// 2) 件数を決める（小さいテーブルは COUNT(*)、大きいテーブルは推定値）
		String q = mysql ? "`" : "\"";
		List<TableCount> tables = new ArrayList<TableCount>();
		Set<String> schemas = new TreeSet<String>();
		long total = 0;
		int estimatedCount = 0;

		for (String[] n : names) {
			String schema = n[0];
			String table = n[1];
			if (schema == null || table == null
					|| !SAFE_NAME.matcher(schema).matches() || !SAFE_NAME.matcher(table).matches()) {
				continue;
			}
			String lower = table.toLowerCase(Locale.ROOT);
			if (ex.contains(lower) || ex.contains((schema + "." + table).toLowerCase(Locale.ROOT))) {
				continue;
			}

			Long est = estimates.get(schema + "." + table);
			long estimate = est == null ? 0L : est.longValue();

			long rows;
			boolean estimated;
			if (exactEnabled && estimate <= maxExactRows) {
				String qualified = mysql ? q + table + q : q + schema + q + "." + q + table + q;
				Long cnt = jdbc.queryForObject("SELECT COUNT(*) FROM " + qualified, Long.class);
				rows = cnt == null ? 0L : cnt.longValue();
				estimated = false;
			} else {
				rows = estimate;
				estimated = true;
				estimatedCount++;
			}

			schemas.add(schema);
			total += rows;
			tables.add(new TableCount(schema, table, rows, estimated));
		}

		Collections.sort(tables, new Comparator<TableCount>() {
			@Override
			public int compare(TableCount a, TableCount b) {
				int c = Long.compare(b.getRows(), a.getRows());
				if (c != 0) {
					return c;
				}
				c = a.getSchema().compareTo(b.getSchema());
				return c != 0 ? c : a.getTable().compareTo(b.getTable());
			}
		});

		return new RdsTables(databaseKey, database, product, new ArrayList<String>(schemas), tables.size(), total,
				estimatedCount, exactEnabled, maxExactRows, tables);
	}

	// =====================================================================
	// helpers
	// =====================================================================

	private DataSource findDataSource(String databaseKey) {
		for (Map.Entry<String, DataSource> e : dataSources.entrySet()) {
			try {
				String[] info = connectionInfo(e.getValue());
				if (databaseKey.equals(info[0])) {
					return e.getValue();
				}
			} catch (Exception ignore) {
				// 接続できない DataSource は飛ばす
			}
			if (databaseKey.equals(e.getKey())) {
				return e.getValue();
			}
		}
		return null;
	}

	/** [DB 名, DB 製品名] */
	private static String[] connectionInfo(DataSource ds) {
		Connection con = null;
		try {
			con = ds.getConnection();
			DatabaseMetaData md = con.getMetaData();
			return new String[] { con.getCatalog(), md.getDatabaseProductName() };
		} catch (SQLException e) {
			throw new IllegalStateException(e.getMessage(), e);
		} finally {
			if (con != null) {
				try {
					con.close();
				} catch (SQLException ignore) {
					// 何もしない
				}
			}
		}
	}

	private static boolean isMySql(String product) {
		String p = product == null ? "" : product.toLowerCase(Locale.ROOT);
		return p.contains("mysql") || p.contains("mariadb");
	}
}
