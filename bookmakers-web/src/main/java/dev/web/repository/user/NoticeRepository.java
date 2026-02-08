package dev.web.repository.user;

import java.time.OffsetDateTime;
import java.util.HashMap;
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
				      id,
				      feature_match_id,
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
			n.noticeId = rs.getLong("id");
			n.featureMatchId = (Long) rs.getObject("feature_match_id");
			n.title = rs.getString("title");
			n.body = rs.getString("body");
			n.status = rs.getString("status");
			n.displayFrom = rs.getObject("display_from", OffsetDateTime.class);
			n.displayTo   = rs.getObject("display_to", OffsetDateTime.class);
			n.publishedAt = rs.getObject("published_at", OffsetDateTime.class);
			return n;
		});

		return list.stream().findFirst();
	}

	public Optional<NoticeRow> findPublishedById(Long noticeId) {
		String sql = """
				    SELECT
				      id, feature_match_id, title, body, status, display_from, display_to, published_at
				    FROM notices
				    WHERE id = :id
				      AND status = 'PUBLISHED'
				""";

		var list = jdbc.query(sql, Map.of("id", noticeId), (rs, rowNum) -> {
			NoticeRow n = new NoticeRow();
			n.noticeId = rs.getLong("id");
			n.featureMatchId = (Long) rs.getObject("feature_match_id");
			n.title = rs.getString("title");
			n.body = rs.getString("body");
			n.status = rs.getString("status");
			n.displayFrom = rs.getObject("display_from", OffsetDateTime.class);
			n.displayTo   = rs.getObject("display_to", OffsetDateTime.class);
			n.publishedAt = rs.getObject("published_at", OffsetDateTime.class);
			return n;
		});

		return list.stream().findFirst();
	}

	public List<NoticeRow> findAllOrderByUpdateTimeDesc() {
		String sql = """
				    SELECT
				      id, feature_match_id, title, body, status, display_from, display_to, published_at
				    FROM notices
				    ORDER BY update_time DESC
				""";

		return jdbc.query(sql, Map.of(), (rs, rowNum) -> {
			NoticeRow n = new NoticeRow();
			n.noticeId = rs.getLong("id");
			n.featureMatchId = (Long) rs.getObject("feature_match_id");
			n.title = rs.getString("title");
			n.body = rs.getString("body");
			n.status = rs.getString("status");
			n.displayFrom = rs.getObject("display_from", OffsetDateTime.class);
			n.displayTo   = rs.getObject("display_to", OffsetDateTime.class);
			n.publishedAt = rs.getObject("published_at", OffsetDateTime.class);
			return n;
		});
	}

	public List<NoticeRow> findPublishedOrderByPublishedAtDesc() {
		String sql = """
				    SELECT
				      id, feature_match_id, title, body, status, display_from, display_to, published_at
				    FROM notices
				    WHERE status = 'PUBLISHED'
				    ORDER BY published_at DESC NULLS LAST, id DESC
				""";

		return jdbc.query(sql, Map.of(), (rs, rowNum) -> {
			NoticeRow n = new NoticeRow();
			n.noticeId = rs.getLong("id");
			n.featureMatchId = (Long) rs.getObject("feature_match_id");
			n.title = rs.getString("title");
			n.body = rs.getString("body");
			n.status = rs.getString("status");
			n.displayFrom = rs.getObject("display_from", OffsetDateTime.class);
			n.displayTo   = rs.getObject("display_to", OffsetDateTime.class);
			n.publishedAt = rs.getObject("published_at", OffsetDateTime.class);
			return n;
		});
	}

	public List<NoticeRow> findActiveForFront(OffsetDateTime now) {
		String sql = """
				    SELECT
				      id, notice_type, feature_match_id, title, body, status,
				      display_from, display_to, published_at
				    FROM notices
				    WHERE status = 'PUBLISHED'
				      AND (display_from IS NULL OR display_from <= :now)
				      AND (display_to   IS NULL OR display_to   >= :now)
				    ORDER BY published_at DESC NULLS LAST, id DESC
				""";

		return jdbc.query(sql, Map.of("now", now), (rs, rowNum) -> {
			NoticeRow n = new NoticeRow();
			n.noticeId = rs.getLong("id");
			n.noticeType = rs.getString("notice_type");
			n.featureMatchId = (Long) rs.getObject("feature_match_id");
			n.title = rs.getString("title");
			n.body = rs.getString("body");
			n.status = rs.getString("status");
			n.displayFrom = rs.getObject("display_from", OffsetDateTime.class);
			n.displayTo   = rs.getObject("display_to", OffsetDateTime.class);
			n.publishedAt = rs.getObject("published_at", OffsetDateTime.class);
			return n;
		});
	}

	// ----------------------------
    // INSERT（DRAFTで登録）
    // ----------------------------
    public Long insert(Long featureMatchId, String title, String body,
                       OffsetDateTime displayFrom, OffsetDateTime displayTo,
                       String operatorId) {

        String noticeType = (featureMatchId != null) ? "FEATURED_MATCH" : "NORMAL";

        String sql = """
            INSERT INTO notices (
              notice_type,
              title,
              body,
              feature_match_id,
              display_from,
              display_to,
              status,
              published_at,
              register_id,
              register_time,
              update_id,
              update_time
            )
            VALUES (
              :noticeType,
              :title,
              :body,
              :featureMatchId,
              :displayFrom,
              :displayTo,
              'DRAFT',
              NULL,
              :op,
              now(),
              :op,
              now()
            )
            RETURNING id
        """;

        Map<String, Object> params = new HashMap<>();
        params.put("noticeType", noticeType);
        params.put("title", title);
        params.put("body", body);
        params.put("featureMatchId", featureMatchId); // null OK
        params.put("displayFrom", displayFrom);       // null OK
        params.put("displayTo", displayTo);           // null OK
        params.put("op", operatorId);

        return jdbc.queryForObject(sql, params, Long.class);
    }

    // ----------------------------
    // UPDATE（部分更新：nullは維持）
    // ----------------------------
    public int update(Long id, String title, String body,
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
            WHERE id = :id
        """;

        Map<String, Object> params = new HashMap<>();
        params.put("id", id);
        params.put("title", title);
        params.put("body", body);
        params.put("displayFrom", displayFrom);       // null OK
        params.put("displayTo", displayTo);           // null OK
        params.put("op", operatorId);
        return jdbc.queryForObject(sql, params, Integer.class);
    }

    // ----------------------------
    // PUBLISH / ARCHIVE
    // ----------------------------
	public int publish(Long noticeId, String operatorId) {
		String sql = """
				    UPDATE notices
				    SET
				      status       = 'PUBLISHED',
				      published_at = COALESCE(published_at, now()),
				      update_id    = :op,
				      update_time  = now()
				    WHERE id = :id
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
				    WHERE id = :id
				""";

		return jdbc.update(sql, Map.of("id", noticeId, "op", operatorId));
	}

	public static class NoticeRow {
		public Long noticeId;
		public Long featureMatchId;
		public String title;
		public String body;
		public String status; // DRAFT/PUBLISHED/ARCHIVED
		public String noticeType;// 通知種
		public OffsetDateTime displayFrom;
		public OffsetDateTime displayTo;
		public OffsetDateTime publishedAt;
		public String registerId;
		public OffsetDateTime registerTime;
		public String updateId;
		public OffsetDateTime updateTime;
	}
}
