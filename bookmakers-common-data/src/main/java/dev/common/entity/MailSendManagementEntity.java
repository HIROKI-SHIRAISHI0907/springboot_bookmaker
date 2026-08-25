package dev.common.entity;

import lombok.Data;

/**
 * MailSendManagementEntityクラス
 * メール送信管理（mail_send_management）1レコード分の情報を保持する。
 *
 * @author shiraishitoshio
 */
@Data
public class MailSendManagementEntity {

	/** メール送信キー */
	private String mailSendKey;

	/** メッセージID */
	private String messageId;

	/** 送信先メールアドレス */
	private String toAddress;

	/** メールID（mail_info_masterへのFK） */
	private String mailId;

	/** エンベロープフロム */
	private String envelopeFrom;

	/** 通知ステータス */
	private String notifyStatus;

}