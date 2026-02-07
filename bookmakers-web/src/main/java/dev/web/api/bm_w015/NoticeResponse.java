package dev.web.api.bm_w015;

import java.time.OffsetDateTime;

import dev.web.repository.user.NoticeRepository.NoticeRow;
import lombok.Data;

@Data
public class NoticeResponse {

	/** ID */
	private Long id;

	/** タイトル */
	private String title;

	/** 通知内容 */
	private String body;

	/** ステータス */
	private String status;

	/** 通知時間(FROM) */
	private OffsetDateTime displayFrom;

	/** 通知時間(TO) */
	private OffsetDateTime displayTo;

	/** 発行時間 */
    private OffsetDateTime publishedAt;

    public static NoticeResponse from(NoticeRow r) {
        NoticeResponse res = new NoticeResponse();
        res.setId(r.noticeId);
        res.setTitle(r.title);
        res.setBody(r.body);
        res.setStatus(r.status);
        res.setDisplayFrom(r.displayFrom);
        res.setDisplayTo(r.displayTo);
        res.setPublishedAt(r.publishedAt);
        return res;
    }
}
