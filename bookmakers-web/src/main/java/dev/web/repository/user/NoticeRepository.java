package dev.web.repository.user;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class NoticeRepository {

	private final @Qualifier("webUserJdbcTemplate") NamedParameterJdbcTemplate jdbc;

	public Optional<NoticeRow> findById(Long noticeId) {
		String sql = """
				    SELECT
				      notice_id,
				      title,
				      body,
				      status,
				      display_from,
				      display_to,
				      published_at
				    FROM notices
				    WHERE id = :id
				""";

		var list = jdbc.query(sql, Map.of("id", noticeId), (rs, rowNum) -> {
			NoticeRow n = new NoticeRow();
			n.noticeId = rs.getLong("notice_id");
			n.title = rs.getString("title");
			n.body = rs.getString("body");
			n.status = rs.getString("status");
			n.displayFrom = (OffsetDateTime) rs.getObject("display_from");
			n.displayTo = (OffsetDateTime) rs.getObject("display_to");
			n.publishedAt = (OffsetDateTime) rs.getObject("published_at");
			return n;
		});

		return list.stream().findFirst();
	}

	public Optional<NoticeRow> findPublishedById(Long noticeId) {
		String sql = """
				    SELECT
				      id, title, body, status, display_from, display_to, published_at
				    FROM notices
				    WHERE id = :id
				      AND status = 'PUBLISHED'
				""";

		var list = jdbc.query(sql, Map.of("id", noticeId), (rs, rowNum) -> {
			NoticeRow n = new NoticeRow();
			n.noticeId = rs.getLong("notice_id");
			n.title = rs.getString("title");
			n.body = rs.getString("body");
			n.status = rs.getString("status");
			n.displayFrom = (OffsetDateTime) rs.getObject("display_from");
			n.displayTo = (OffsetDateTime) rs.getObject("display_to");
			n.publishedAt = (OffsetDateTime) rs.getObject("published_at");
			return n;
		});

		return list.stream().findFirst();
	}

	public List<NoticeRow> findAllOrderByUpdateTimeDesc() {
		String sql = """
				    SELECT
				      id, title, body, status, display_from, display_to, published_at
				    FROM notices
				    ORDER BY update_time DESC
				""";

		return jdbc.query(sql, Map.of(), (rs, rowNum) -> {
			NoticeRow n = new NoticeRow();
			n.noticeId = rs.getLong("notice_id");
			n.title = rs.getString("title");
			n.body = rs.getString("body");
			n.status = rs.getString("status");
			n.displayFrom = (OffsetDateTime) rs.getObject("display_from");
			n.displayTo = (OffsetDateTime) rs.getObject("display_to");
			n.publishedAt = (OffsetDateTime) rs.getObject("published_at");
			return n;
		});
	}

	public List<NoticeRow> findPublishedOrderByPublishedAtDesc() {
		String sql = """
				    SELECT
				      id, title, body, status, display_from, display_to, published_at
				    FROM notices
				    WHERE status = 'PUBLISHED'
				    ORDER BY published_at DESC NULLS LAST, notice_id DESC
				""";

		return jdbc.query(sql, Map.of(), (rs, rowNum) -> {
			NoticeRow n = new NoticeRow();
			n.noticeId = rs.getLong("notice_id");
			n.title = rs.getString("title");
			n.body = rs.getString("body");
			n.status = rs.getString("status");
			n.displayFrom = (OffsetDateTime) rs.getObject("display_from");
			n.displayTo = (OffsetDateTime) rs.getObject("display_to");
			n.publishedAt = (OffsetDateTime) rs.getObject("published_at");
			return n;
		});
	}

	public List<NoticeRow> findActiveForFront(OffsetDateTime now) {
		String sql = """
				    SELECT
				      id, title, body, status, display_from, display_to, published_at
				    FROM notices
				    WHERE status = 'PUBLISHED'
				      AND (display_from IS NULL OR display_from <= :now)
				      AND (display_to   IS NULL OR display_to   >= :now)
				    ORDER BY published_at DESC NULLS LAST, id DESC
				""";

		return jdbc.query(sql, Map.of("now", now), (rs, rowNum) -> {
			NoticeRow n = new NoticeRow();
			n.noticeId = rs.getLong("notice_id");
			n.title = rs.getString("title");
			n.body = rs.getString("body");
			n.status = rs.getString("status");
			n.displayFrom = (OffsetDateTime) rs.getObject("display_from");
			n.displayTo = (OffsetDateTime) rs.getObject("display_to");
			n.publishedAt = (OffsetDateTime) rs.getObject("published_at");
			return n;
		});
	}

	/**
	 * 登録処理
	 * @param title
	 * @param body
	 * @param displayFrom
	 * @param displayTo
	 * @param operatorId
	 * @return
	 */
	public Long insert(String title, String body, OffsetDateTime displayFrom, OffsetDateTime displayTo,
			String operatorId) {
		String sql = """
				    INSERT INTO notices
				      (title, body, status, display_from, display_to, published_at,
				       register_id, register_time, update_id, update_time)
				    VALUES
				      (:title, :body, 'DRAFT', :displayFrom, :displayTo, NULL,
				       :op, now(), :op, now())
				    RETURNING notice_id
				""";

		return jdbc.queryForObject(sql, Map.of(
				"title", title,
				"body", body,
				"displayFrom", displayFrom,
				"displayTo", displayTo,
				"op", operatorId), Long.class);
	}

	/**
	 * 更新処理
	 * @param noticeId
	 * @param title
	 * @param body
	 * @param displayFrom
	 * @param displayTo
	 * @param operatorId
	 * @return
	 */
	public int update(Long noticeId, String title, String body,
			OffsetDateTime displayFrom, OffsetDateTime displayTo,
			String operatorId) {
		String sql = """
				  UPDATE notices
				  SET
				    title        = COALESCE(:title, title),
				    body         = COALESCE(:body, body),
				    display_from = COALESCE(:displayFrom, display_from),
				    display_to   = COALESCE(:displayTo, display_to),
				    update_id    = :op,
				    update_time  = now()
				  WHERE notice_id = :id
				""";

		return jdbc.update(sql, Map.of(
				"id", noticeId,
				"title", title,
				"body", body,
				"displayFrom", displayFrom,
				"displayTo", displayTo,
				"op", operatorId));
	}

	public int publish(Long noticeId, String operatorId) {
		String sql = """
				    UPDATE notices
				    SET
				      status       = 'PUBLISHED',
				      published_at = COALESCE(published_at, now()),
				      update_id    = :op,
				      update_time  = now()
				    WHERE notice_id = :id
				""";

		return jdbc.update(sql, Map.of("id", noticeId, "op", operatorId));
	}

	public int archive(Long noticeId, String operatorId) {
		String sql = """
				    UPDATE notices
				    SET
				      status      = 'ARCHIVED',
				      update_id   = :op,
				      update_time = now()
				    WHERE notice_id = :id
				""";

		return jdbc.update(sql, Map.of("id", noticeId, "op", operatorId));
	}

	public static class NoticeRow {
		public Long noticeId;
		public String title;
		public String body;
		public String status; // DRAFT/PUBLISHED/ARCHIVED
		public OffsetDateTime displayFrom;
		public OffsetDateTime displayTo;
		public OffsetDateTime publishedAt;
		public String registerId;
		public OffsetDateTime registerTime;
		public String updateId;
		public OffsetDateTime updateTime;
	}
}
