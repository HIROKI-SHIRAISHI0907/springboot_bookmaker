package dev.web.repository.bm;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.common.constant.CsvUploadConst.Limit;
import dev.common.constant.CsvUploadConst.Status;
import dev.web.api.bm_a032.FileUploadStatusDTO;
import dev.web.api.bm_a032.FileUploadStatusSearchCondition;
import dev.web.api.bm_a032.FileUploadStatusSummaryDTO;

/**
 * ファイルアップロード状況管理（file_upload_status_manage・bm DB）
 *
 * <ul>
 *   <li>web: 受付（insertReceived → 受付 RECEIVED）→ バリデーション OK・S3 保管 OK（markUploaded → 判定前 PENDING。
 *       uploaded/ の場所を記録）または NG（markError → 異常終了 ERROR）。</li>
 *   <li>バッチサービス: 判定前（PENDING）だけを取り出して DB チェック・反映 → 正常終了 / 異常終了（バッチサービス側で更新する）。
 *       受付（RECEIVED）のまま残っているものは web の処理途中で止まったもので、バッチサービスは拾わない。</li>
 *   <li>それぞれ 1 文ずつすぐ確定する（マスタへの反映のトランザクションとは別）。</li>
 *   <li>一覧ではエラー行（error_detail）を返さない。詳細（findById）だけで返す。</li>
 *   <li>日時は日本時間の文字列（yyyy-MM-dd HH:mm:ss）で返す。検索条件は入力があるものだけ WHERE に足す。</li>
 *   <li>login_user（ログインユーザー）は register_id / update_id にも入れる。</li>
 * </ul>
 *
 * @author shiraishitoshio
 */
@Repository
public class FileUploadStatusRepository {

	private final NamedParameterJdbcTemplate bmJdbcTemplate;

	public FileUploadStatusRepository(
			@Qualifier("bmJdbcTemplate") NamedParameterJdbcTemplate bmJdbcTemplate) {
		this.bmJdbcTemplate = bmJdbcTemplate;
	}

	private static final String LIST_COLUMNS = """
			      id,
			      master_name,
			      file_name,
			      csv_file_name,
			      file_size,
			      status,
			      error_phase,
			      message,
			      total_count,
			      insert_count,
			      update_count,
			      delete_count,
			      hide_count,
			      show_count,
			      error_count,
			      login_user,
			      to_char(started_at  AT TIME ZONE 'Asia/Tokyo', 'YYYY-MM-DD HH24:MI:SS') AS started_at,
			      to_char(finished_at AT TIME ZONE 'Asia/Tokyo', 'YYYY-MM-DD HH24:MI:SS') AS finished_at,
			      EXTRACT(EPOCH FROM (finished_at - started_at))::bigint AS elapsed_sec,
			      s3_bucket,
			      s3_key
			""";

	/**
	 * 受付（status = RECEIVED。バリデーション・S3 保管の前。メッセージはまだ入れない）
	 * @param loginUser ログインユーザー（メールアドレス）。register_id / update_id にも入れる
	 * @return 採番した ID
	 */
	public long insertReceived(String masterName, String fileName, String csvFileName, long fileSize, String loginUser) {
		String sql = """
				INSERT INTO file_upload_status_manage (
				    master_name, file_name, csv_file_name, file_size, status, login_user, started_at,
				    register_id, register_time, update_id, update_time
				) VALUES (
				    :masterName, :fileName, NULLIF(:csvFileName, ''), :fileSize, :status, :loginUser, CURRENT_TIMESTAMP,
				    :loginUser, CURRENT_TIMESTAMP, :loginUser, CURRENT_TIMESTAMP
				)
				RETURNING id
				""";
		Long id = bmJdbcTemplate.queryForObject(sql, new MapSqlParameterSource()
				.addValue("masterName", masterName)
				.addValue("fileName", cut(fileName, Limit.FILE_NAME_LENGTH))
				.addValue("csvFileName", cut(csvFileName, Limit.FILE_NAME_LENGTH))
				.addValue("fileSize", fileSize)
				.addValue("status", Status.RECEIVED)
				.addValue("loginUser", loginUser), Long.class);
		if (id == null) {
			throw new IllegalStateException("file_upload_status_manage の ID を採番できませんでした。");
		}
		return id;
	}

	/** バリデーション OK・S3 保管 OK（判定前 PENDING にして、uploaded/ の場所を記録） */
	public int markUploaded(long id, String message, int totalCount, String s3Bucket, String s3Key, String loginUser) {
		String sql = """
				UPDATE file_upload_status_manage
				   SET status      = :status,
				       error_phase = NULL,
				       message     = :message,
				       total_count = :totalCount,
				       error_count = 0,
				       s3_bucket   = :s3Bucket,
				       s3_key      = :s3Key,
				       update_id   = :user,
				       update_time = CURRENT_TIMESTAMP
				 WHERE id = :id
				""";
		return bmJdbcTemplate.update(sql, new MapSqlParameterSource()
				.addValue("status", Status.PENDING)
				.addValue("message", message)
				.addValue("totalCount", totalCount)
				.addValue("s3Bucket", s3Bucket)
				.addValue("s3Key", s3Key)
				.addValue("user", loginUser)
				.addValue("id", id));
	}

	/** 異常終了（error/ の場所とエラー行を記録して終わりにする） */
	public int markError(long id, String errorPhase, String message, int totalCount, int errorCount,
			String errorDetailJson, String s3Bucket, String s3Key, String loginUser) {
		String sql = """
				UPDATE file_upload_status_manage
				   SET status       = :status,
				       error_phase  = :errorPhase,
				       message      = :message,
				       total_count  = :totalCount,
				       error_count  = :errorCount,
				       error_detail = CAST(:errorDetail AS jsonb),
				       s3_bucket    = :s3Bucket,
				       s3_key       = :s3Key,
				       finished_at  = CURRENT_TIMESTAMP,
				       update_id    = :user,
				       update_time  = CURRENT_TIMESTAMP
				 WHERE id = :id
				""";
		return bmJdbcTemplate.update(sql, new MapSqlParameterSource()
				.addValue("status", Status.ERROR)
				.addValue("errorPhase", errorPhase)
				.addValue("message", message)
				.addValue("totalCount", totalCount)
				.addValue("errorCount", errorCount)
				.addValue("errorDetail", errorDetailJson)
				.addValue("s3Bucket", s3Bucket)
				.addValue("s3Key", s3Key)
				.addValue("user", loginUser)
				.addValue("id", id));
	}

	/** 検索条件の WHERE（入力があるものだけ） */
	private static String where(FileUploadStatusSearchCondition c, MapSqlParameterSource p) {
		StringBuilder sql = new StringBuilder(" WHERE 1 = 1 ");
		if (notEmpty(c.getMasterName())) {
			sql.append(" AND master_name = :masterName ");
			p.addValue("masterName", c.getMasterName());
		}
		if (notEmpty(c.getStatus())) {
			sql.append(" AND status = :status ");
			p.addValue("status", c.getStatus());
		}
		if (notEmpty(c.getDateFrom())) {
			// 日本時間のその日の 0:00 から
			sql.append(" AND started_at >= (CAST(:dateFrom AS date)::timestamp AT TIME ZONE 'Asia/Tokyo') ");
			p.addValue("dateFrom", c.getDateFrom());
		}
		if (notEmpty(c.getDateTo())) {
			// 日本時間のその日の終わりまで（翌日 0:00 より前）
			sql.append(" AND started_at < ((CAST(:dateTo AS date) + 1)::timestamp AT TIME ZONE 'Asia/Tokyo') ");
			p.addValue("dateTo", c.getDateTo());
		}
		if (notEmpty(c.getLoginUser())) {
			sql.append(" AND login_user ILIKE :loginUser ");
			p.addValue("loginUser", "%" + likeEscape(c.getLoginUser()) + "%");
		}
		if (notEmpty(c.getFileName())) {
			sql.append(" AND file_name ILIKE :fileName ");
			p.addValue("fileName", "%" + likeEscape(c.getFileName()) + "%");
		}
		return sql.toString();
	}

	/** 一覧（新しい順・エラー行は返さない） */
	public List<FileUploadStatusDTO> search(FileUploadStatusSearchCondition c, int limit, int offset) {
		MapSqlParameterSource p = new MapSqlParameterSource();
		String sql = "SELECT " + LIST_COLUMNS + " FROM file_upload_status_manage" + where(c, p)
				+ " ORDER BY started_at DESC, id DESC LIMIT :limit OFFSET :offset";
		p.addValue("limit", limit).addValue("offset", offset);
		return bmJdbcTemplate.query(sql, p, (rs, i) -> map(rs, false));
	}

	/** 件数 */
	public int count(FileUploadStatusSearchCondition c) {
		MapSqlParameterSource p = new MapSqlParameterSource();
		String sql = "SELECT COUNT(*) FROM file_upload_status_manage" + where(c, p);
		Integer cnt = bmJdbcTemplate.queryForObject(sql, p, Integer.class);
		return cnt == null ? 0 : cnt;
	}

	/** 状態ごとの件数（同じ検索条件。状態の条件は外す） */
	public List<FileUploadStatusSummaryDTO> summary(FileUploadStatusSearchCondition c) {
		FileUploadStatusSearchCondition noStatus = c.copyWithoutStatus();
		MapSqlParameterSource p = new MapSqlParameterSource();
		String sql = "SELECT status, COUNT(*) AS cnt FROM file_upload_status_manage" + where(noStatus, p)
				+ " GROUP BY status ORDER BY status";
		return bmJdbcTemplate.query(sql, p,
				(rs, i) -> new FileUploadStatusSummaryDTO(rs.getString("status"), rs.getInt("cnt")));
	}

	/** 詳細（エラー行を含む） */
	public FileUploadStatusDTO findById(long id) {
		String sql = "SELECT " + LIST_COLUMNS
				+ ", error_detail::text AS error_detail FROM file_upload_status_manage WHERE id = :id";
		List<FileUploadStatusDTO> list = bmJdbcTemplate.query(sql, new MapSqlParameterSource("id", id),
				(rs, i) -> map(rs, true));
		return list.isEmpty() ? null : list.get(0);
	}

	private static FileUploadStatusDTO map(ResultSet rs, boolean withDetail) throws SQLException {
		FileUploadStatusDTO d = new FileUploadStatusDTO();
		d.setId(rs.getLong("id"));
		d.setMasterName(rs.getString("master_name"));
		d.setFileName(rs.getString("file_name"));
		d.setCsvFileName(rs.getString("csv_file_name"));
		d.setFileSize(rs.getLong("file_size"));
		d.setStatus(rs.getString("status"));
		d.setErrorPhase(rs.getString("error_phase"));
		d.setMessage(rs.getString("message"));
		d.setTotalCount(rs.getInt("total_count"));
		d.setInsertCount(rs.getInt("insert_count"));
		d.setUpdateCount(rs.getInt("update_count"));
		d.setDeleteCount(rs.getInt("delete_count"));
		d.setHideCount(rs.getInt("hide_count"));
		d.setShowCount(rs.getInt("show_count"));
		d.setErrorCount(rs.getInt("error_count"));
		d.setLoginUser(rs.getString("login_user"));
		d.setStartedAt(rs.getString("started_at"));
		d.setFinishedAt(rs.getString("finished_at"));
		long elapsed = rs.getLong("elapsed_sec");
		d.setElapsedSec(rs.wasNull() ? null : elapsed);
		d.setS3Bucket(rs.getString("s3_bucket"));
		d.setS3Key(rs.getString("s3_key"));
		if (withDetail) {
			d.setErrorDetailJson(rs.getString("error_detail"));
		}
		return d;
	}

	/** LIKE の特殊文字（\ % _）をそのままの文字として探す */
	private static String likeEscape(String s) {
		return s.strip().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

	private static String cut(String s, int max) {
		return s == null ? "" : (s.length() > max ? s.substring(0, max) : s);
	}

	private static boolean notEmpty(String s) {
		return s != null && !s.isBlank();
	}
}