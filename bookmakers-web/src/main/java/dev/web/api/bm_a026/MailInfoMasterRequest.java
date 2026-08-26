package dev.web.api.bm_a026;

import lombok.Data;

@Data
public class MailInfoMasterRequest {

	/** メールID */
	private String mailId;

	/** メール件名 */
	private String mailSubject;

	/** メール本文 */
	private String mailBody;

	/** 送信元メールアドレス */
	private String fromAddress;

}
