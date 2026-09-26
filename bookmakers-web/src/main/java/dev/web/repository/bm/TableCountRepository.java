package dev.web.repository.bm;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

import dev.web.api.bm_a013.DashboardDtos.TableCount;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 既存 Spring Boot の DataSource（= RDS）を使ってテーブル一覧と件数を取得する。
 * PostgreSQL / MySQL(MariaDB) に対応。
 */
@Repository
public class TableCountRepository {

	/** テーブル名はこのパターンのみ許可（SQL インジェクション対策） */
	private static final Pattern SAFE_NAME = Pattern.compile("^[A-Za-z0-9_$]+$");

	private final JdbcTemplate jdbcTemplate;

	public TableCountRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	/** 接続情報（DB製品名・DB名・スキーマ） */
	@Getter
	@AllArgsConstructor
	public static class DbInfo {
		private final String product;
		private final String database;
		private final String schema;
	}

	public DbInfo dbInfo(final String schemaOverride) {
		return jdbcTemplate.execute(new ConnectionCallback<DbInfo>() {
			@Override
			public DbInfo doInConnection(Connection con) throws SQLException {
				DatabaseMetaData md = con.getMetaData();
				String schema = isBlank(schemaOverride) ? con.getSchema() : schemaOverride;
				return new DbInfo(md.getDatabaseProductName(), con.getCatalog(), schema);
			}
		});
	}

	/** 対象スキーマの通常テーブル一覧 */
	public List<String> findTableNames(final String schemaOverride, List<String> excludes) {
		final Set<String> ex = new HashSet<String>();
		if (excludes != null) {
			for (String s : excludes) {
				ex.add(s.toLowerCase(Locale.ROOT));
			}
		}

		return jdbcTemplate.execute(new ConnectionCallback<List<String>>() {
			@Override
			public List<String> doInConnection(Connection con) throws SQLException {
				DatabaseMetaData md = con.getMetaData();
				boolean mysql = isMySql(md.getDatabaseProductName());
				// MySQL は catalog = DB名、PostgreSQL は schema で絞る
				String catalog = con.getCatalog();
				String schema = mysql ? null : (isBlank(schemaOverride) ? con.getSchema() : schemaOverride);

				List<String> names = new ArrayList<String>();
				ResultSet rs = md.getTables(catalog, schema, "%", new String[] { "TABLE" });
				try {
					while (rs.next()) {
						String name = rs.getString("TABLE_NAME");
						if (name != null && SAFE_NAME.matcher(name).matches()
								&& !ex.contains(name.toLowerCase(Locale.ROOT))) {
							names.add(name);
						}
					}
				} finally {
					rs.close();
				}
				Collections.sort(names);
				return names;
			}
		});
	}

	/** COUNT(*) で正確な件数を数える（大きいテーブルは時間がかかる） */
	public List<TableCount> countExact(String schemaOverride, List<String> tables) {
		DbInfo info = dbInfo(schemaOverride);
		boolean mysql = isMySql(info.getProduct());
		String q = mysql ? "`" : "\"";

		List<TableCount> out = new ArrayList<TableCount>();
		for (String t : tables) {
			if (!SAFE_NAME.matcher(t).matches()) {
				continue;
			}
			String qualified = (!mysql && !isBlank(info.getSchema()))
					? q + info.getSchema() + q + "." + q + t + q
					: q + t + q;
			Long cnt = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + qualified, Long.class);
			out.add(new TableCount(t, cnt == null ? 0L : cnt.longValue()));
		}
		return out;
	}

	/** DB の統計情報から推定件数を取る（高速・概算） */
	public List<TableCount> countEstimated(String schemaOverride, List<String> tables) {
		DbInfo info = dbInfo(schemaOverride);
		final Map<String, Long> est = new HashMap<String, Long>();
		RowCallbackHandler handler = new RowCallbackHandler() {
			@Override
			public void processRow(ResultSet rs) throws SQLException {
				est.put(rs.getString(1), rs.getLong(2));
			}
		};

		if (isMySql(info.getProduct())) {
			jdbcTemplate.query(
					"SELECT table_name, table_rows FROM information_schema.tables WHERE table_schema = ?",
					handler, info.getDatabase());
		} else {
			jdbcTemplate.query(
					"SELECT relname, n_live_tup FROM pg_stat_user_tables WHERE schemaname = ?",
					handler, isBlank(info.getSchema()) ? "public" : info.getSchema());
		}

		List<TableCount> out = new ArrayList<TableCount>();
		for (String t : tables) {
			Long v = est.get(t);
			out.add(new TableCount(t, v == null ? 0L : v.longValue()));
		}
		return out;
	}

	private static boolean isMySql(String product) {
		String p = product == null ? "" : product.toLowerCase(Locale.ROOT);
		return p.contains("mysql") || p.contains("mariadb");
	}

	static boolean isBlank(String s) {
		return s == null || s.trim().isEmpty();
	}
}
