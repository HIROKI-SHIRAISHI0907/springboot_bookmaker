package dev.web.api.bm_w015;

import java.time.OffsetDateTime;

import lombok.Data;

@Data
public class NoticeRequest {

	/** ID */
	private Long id;

	/** タイトル */
	private String title;

	/** 通知内容 */
	private String body;

	/** 通知時間(FROM) */
	private OffsetDateTime displayFrom;

	/** 通知時間(TO) */
	private OffsetDateTime displayTo;

}
