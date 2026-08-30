package dev.common.entity;

import java.sql.Timestamp;

import lombok.Data;

/**
 * MailSendManagementEntityクラス
 * メール送信管理（mail_send_management）1レコード分の情報を保持する。
 *
 * @author shiraishitoshio
 */
@Data
public class MailSendManagementEntity {

	/** メール送信キー（一意のID） */
	private String mailSendKey;

	/** メッセージID（） */
	private String messageId;

	/** 送信先メールアドレス */
	private String toAddress;

	/** メールID（mail_info_masterへのFK） */
	private String mailId;

	/** エンベロープフロム */
	private String envelopeFrom;

	/** 通知ステータス（通知前:0, 通知後:1） */
	private String notifyStatus;

	/** 送信失敗カウント */
	private int failSendCount;

	/** 備考 */
	private String bikou;

	/** 登録時間 */
	private Timestamp registerTime;

}