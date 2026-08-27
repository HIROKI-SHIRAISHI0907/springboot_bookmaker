package dev.common.mail;

import java.util.Properties;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.sun.mail.smtp.SMTPMessage;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;

/**
 * GmailMailSenderComponentクラス
 * Gmail SMTP経由で実際にメールを送信する。
 *
 * 認証情報はソースコードに直接書かず、application.properties/application.yml側の
 * mail.smtp.username / mail.smtp.password に外出しする想定。
 *
 * ※GmailのSMTPリレーは、認証しているアカウント本人か、Gmail側で
 *   検証済みの「メール送信のエイリアス」でない限り、fromAddress/envelopeFromに
 *   別のアドレスを使わせてくれません。mail_info_master.from_address や
 *   mail_send_manage.envelope_from に、認証アカウント（mail.smtp.username）と
 *   異なるアドレスを入れていると、送信時にGmail側から拒否される可能性が高いです。
 *
 * @author shiraishitoshio
 */
@Component
public class MailSendComponent {

	@Value("${mail.smtp.host:smtp.gmail.com}")
	private String smtpHost;

	@Value("${mail.smtp.port:587}")
	private String smtpPort;

	/** Gmailの認証アカウント（SMTP AUTHで使うメールアドレス） */
	@Value("${mail.smtp.username}")
	private String smtpUsername;

	/** Googleアプリパスワード */
	@Value("${mail.smtp.password}")
	private String smtpPassword;

	/**
	 * メールを送信する。
	 *
	 * @param fromAddress   ヘッダーFrom（表示上の差出人。mail_info_master.from_address）
	 * @param envelopeFrom  エンベロープフロム（配送エラーの返送先。mail_send_manage.envelope_from）
	 * @param toAddress     送信先メールアドレス
	 * @param subject       件名
	 * @param body          本文
	 * @return 送信したメールのMessage-ID（mail_send_manage.message_id反映用）
	 * @throws MessagingException 送信に失敗した場合（呼び出し元でcatchしてfail_send_countを更新する想定）
	 */
	public String send(String fromAddress, String envelopeFrom, String toAddress, String subject, String body)
			throws MessagingException {
		Properties props = new Properties();
		props.put("mail.smtp.host", smtpHost);
		props.put("mail.smtp.port", smtpPort);
		props.put("mail.smtp.auth", "true");
		props.put("mail.smtp.starttls.enable", "true");
		props.put("mail.smtp.ssl.trust", smtpHost);

		Session session = Session.getInstance(props, new Authenticator() {
			@Override
			protected PasswordAuthentication getPasswordAuthentication() {
				return new PasswordAuthentication(smtpUsername, smtpPassword);
			}
		});

		SMTPMessage message = new SMTPMessage(session);
		message.setFrom(new InternetAddress(fromAddress));
		message.addRecipient(Message.RecipientType.TO, new InternetAddress(toAddress));
		message.setSubject(subject, "UTF-8");
		message.setText(body, "UTF-8");
		message.setEnvelopeFrom(envelopeFrom);

		Transport.send(message);

		return message.getMessageID();
	}
}