package dev.web.repository.bm;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.sql.DataSource;

import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

import dev.web.config.AwsDashboardPropertiesConfig;

/**
 * 月次レポート用の日次スナップショット（aws_daily_stats テーブル）の読み書き。
 *
 * 保存先 DB は aws.dashboard.stats-database（既定: soccer_bm）。
 * アプリに登録されている DataSource の中から、接続先 DB 名が一致するものを使う。
 */
@Repository
public class AwsDailyStatsRepository {

	public static final String ECS_TOTAL = "ECS_TOTAL";
	public static final String ECS_TASKDEF = "ECS_TASKDEF";
	public static final String LAMBDA = "LAMBDA";
	public static final String RDS_TABLE = "RDS_TABLE";

	/** 1 行分 */
	public static final class Row {
		public final LocalDate date;
		public final String category;
		public final String key;
		public final long value;
		public final Long value2;
		public final boolean estimated;

		public Row(LocalDate date, String category, String key, long value, Long value2, boolean estimated) {
			this.date = date;
			this.category = category;
			this.key = key;
			this.value = value;
			this.value2 = value2;
			this.estimated = estimated;
		}
	}

	/** 月ごとの合計 */
	public static final class MonthSum {
		public final String month; // YYYY-MM
		public final long total;
		public final int days;

		public MonthSum(String month, long total, int days) {
			this.month = month;
			this.total = total;
			this.days = days;
		}
	}

	private final Map<String, DataSource> dataSources;
	private final AwsDashboardPropertiesConfig props;
	private volatile JdbcTemplate jdbc;

	public AwsDailyStatsRepository(Map<String, DataSource> dataSources, AwsDashboardPropertiesConfig props) {
		this.dataSources = new TreeMap<String, DataSource>(dataSources);
		this.props = props;
	}

	// =====================================================================
	// 書き込み
	// =====================================================================

	/**
	 * 指定日・指定カテゴリの行を入れ替える（削除 → まとめて登録）。
	 * 同じ日を何度集計しても結果は同じ（ECS タスクが複数台あっても安全）。
	 */
	public void replaceDay(final LocalDate date, final String category, final List<Row> rows) {
		JdbcTemplate t = jdbc();
		t.update("DELETE FROM aws_daily_stats WHERE stat_date = ? AND category = ?",
				java.sql.Date.valueOf(date), category);
		if (rows.isEmpty()) {
			return;
		}
		t.batchUpdate("INSERT INTO aws_daily_stats "
				+ "(stat_date, category, item_key, value, value2, estimated, "
				+ " register_id, register_time, update_id, update_time) "
				+ "VALUES (?, ?, ?, ?, ?, ?, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP) "
				+ "ON CONFLICT (stat_date, category, item_key) DO UPDATE SET "
				+ "value = EXCLUDED.value, value2 = EXCLUDED.value2, estimated = EXCLUDED.estimated, "
				+ "update_id = 'SYSTEM', update_time = CURRENT_TIMESTAMP",
				new BatchPreparedStatementSetter() {
					@Override
					public void setValues(PreparedStatement ps, int i) throws SQLException {
						Row r = rows.get(i);
						ps.setDate(1, java.sql.Date.valueOf(date));
						ps.setString(2, category);
						ps.setString(3, r.key == null ? "" : r.key);
						ps.setLong(4, r.value);
						if (r.value2 == null) {
							ps.setNull(5, java.sql.Types.BIGINT);
						} else {
							ps.setLong(5, r.value2.longValue());
						}
						ps.setBoolean(6, r.estimated);
					}

					@Override
					public int getBatchSize() {
						return rows.size();
					}
				});
	}

	// =====================================================================
	// 読み込み
	// =====================================================================

	/** 期間内の全行 */
	public List<Row> find(LocalDate from, LocalDate to) {
		final List<Row> out = new ArrayList<Row>();
		jdbc().query("SELECT stat_date, category, item_key, value, value2, estimated FROM aws_daily_stats "
				+ "WHERE stat_date BETWEEN ? AND ? ORDER BY stat_date, category, item_key",
				new RowCallbackHandler() {
					@Override
					public void processRow(ResultSet rs) throws SQLException {
						long v2 = rs.getLong(5);
						Long value2 = rs.wasNull() ? null : Long.valueOf(v2);
						out.add(new Row(rs.getDate(1).toLocalDate(), rs.getString(2), rs.getString(3), rs.getLong(4),
								value2, rs.getBoolean(6)));
					}
				},
				java.sql.Date.valueOf(from), java.sql.Date.valueOf(to));
		return out;
	}

	/** 期間内でデータがある日 */
	public Set<LocalDate> datesWith(String category, LocalDate from, LocalDate to) {
		final Set<LocalDate> out = new HashSet<LocalDate>();
		jdbc().query("SELECT DISTINCT stat_date FROM aws_daily_stats WHERE category = ? AND stat_date BETWEEN ? AND ?",
				new RowCallbackHandler() {
					@Override
					public void processRow(ResultSet rs) throws SQLException {
						out.add(rs.getDate(1).toLocalDate());
					}
				},
				category, java.sql.Date.valueOf(from), java.sql.Date.valueOf(to));
		return out;
	}

	/** 月ごとの合計（ECS_TOTAL の月別合計など） */
	public List<MonthSum> monthlySums(String category, LocalDate from, LocalDate to) {
		final List<MonthSum> out = new ArrayList<MonthSum>();
		jdbc().query("SELECT to_char(stat_date, 'YYYY-MM') AS ym, SUM(value), COUNT(DISTINCT stat_date) "
				+ "FROM aws_daily_stats WHERE category = ? AND stat_date BETWEEN ? AND ? "
				+ "GROUP BY ym ORDER BY ym",
				new RowCallbackHandler() {
					@Override
					public void processRow(ResultSet rs) throws SQLException {
						out.add(new MonthSum(rs.getString(1), rs.getLong(2), rs.getInt(3)));
					}
				},
				category, java.sql.Date.valueOf(from), java.sql.Date.valueOf(to));
		return out;
	}

	/** カテゴリごとの [最古日, 最新日, 日数] */
	public Map<String, Object[]> coverage() {
		final Map<String, Object[]> out = new TreeMap<String, Object[]>();
		jdbc().query("SELECT category, MIN(stat_date), MAX(stat_date), COUNT(DISTINCT stat_date) "
				+ "FROM aws_daily_stats GROUP BY category",
				new RowCallbackHandler() {
					@Override
					public void processRow(ResultSet rs) throws SQLException {
						out.put(rs.getString(1), new Object[] { rs.getDate(2).toLocalDate(),
								rs.getDate(3).toLocalDate(), Integer.valueOf(rs.getInt(4)) });
					}
				});
		return out;
	}

	// =====================================================================
	// helpers
	// =====================================================================

	/** 保存先 DB（stats-database）の JdbcTemplate */
	private JdbcTemplate jdbc() {
		JdbcTemplate t = jdbc;
		if (t != null) {
			return t;
		}
		synchronized (this) {
			if (jdbc != null) {
				return jdbc;
			}
			String target = props.getStatsDatabase();
			for (DataSource ds : dataSources.values()) {
				Connection con = null;
				try {
					con = ds.getConnection();
					if (target.equals(con.getCatalog())) {
						jdbc = new JdbcTemplate(ds);
						return jdbc;
					}
				} catch (SQLException ignore) {
					// 接続できない DataSource は飛ばす
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
			throw new IllegalStateException("日次集計の保存先 DB が見つかりません: " + target
					+ "（aws.dashboard.stats-database を確認してください）");
		}
	}
}
