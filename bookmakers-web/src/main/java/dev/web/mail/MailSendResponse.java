package dev.web.mail;

import lombok.Data;

/**
 * MailSendResponse
 * @author shiraishitoshio
 *
 */
@Data
public class MailSendResponse {

	/** レスポンスコード */
	private String responseCode; // "0"=成功, "9"=失敗 など運用に合わせて

	/** メッセージ */
    private String message;

    /** メール送信キー */
    private String mailSendKey; // 成功時に返す

}