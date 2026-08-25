package dev.common.enums;

/**
 * メール通知定数
 * @author shiraishitoshio
 *
 */
public enum MailNoticeEnum {

	/** 通知前 */
	NOTIFY_STATUS_PENDING("通知前", "0"),

	/** 通知後 */
	NOTIFY_STATUS_SENDED("通知後", "1");

	/** 日本語の意味 */
	private final String noticeName;

	/** ステータス */
	private final String noticeStatus;

	MailNoticeEnum(String noticeName, String noticeStatus) {
		this.noticeName = noticeName;
		this.noticeStatus = noticeStatus;
	}

	/**
	 * 日本語の意味を取得
	 */
	public String getNoticeName() {
		return noticeName;
	}

	/**
	 * ステータスを取得
	 */
	public String getNoticeStatus() {
		return noticeStatus;
	}

}
