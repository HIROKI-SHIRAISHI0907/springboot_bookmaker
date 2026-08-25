package dev.common.entity;

import lombok.Data;

/**
 * MailInfoEntityクラス
 * メール情報マスタ（mail_info_master）1レコード分の情報を保持する。
 *
 * @author shiraishitoshio
 */
@Data
public class MailInfoMasterEntity {

	/** メールID */
	private String mailId;

	/** メール件名 */
	private String mailSubject;

	/** メール本文 */
	private String mailBody;

	/** 送信元メールアドレス */
	private String fromAddress;

}