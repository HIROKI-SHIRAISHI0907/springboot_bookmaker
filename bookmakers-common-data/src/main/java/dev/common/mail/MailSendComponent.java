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
import lombok.extern.slf4j.Slf4j;

/**
 * GmailMailSenderComponentクラス
 * Gmail SMTP経由で実際にメールを送信する。
 *
 * 認証情報はソースコードに直接書かず、application.properties/application.yml側の
 * mail.accounts.<fromAddress>.username / mail.accounts.<fromAddress>.password に
 * fromAddressごとに外出しする想定。
 *
 * ※GmailのSMTPリレーは、認証しているアカウント本人か、Gmail側で
 *   検証済みの「メール送信のエイリアス」でない限り、fromAddress/envelopeFromに
 *   別のアドレスを使わせてくれません。そのためfromAddressごとに対応する
 *   SMTP認証情報（username/password）を切り替えて使う。
 *
 * @author shiraishitoshio
 */
@Component
@Slf4j
public class MailSendComponent {

	@Value("${mail.smtp.host:smtp.gmail.com}")
	private String smtpHost;
	@Value("${mail.smtp.port:587}")
	private String smtpPort;

	private final MailAccountsProperties mailAccountsProperties;

	public MailSendComponent(MailAccountsProperties mailAccountsProperties) {
		this.mailAccountsProperties = mailAccountsProperties;
	}

	/**
	 * メールを送信する。
	 *
	 * @param fromAddress   ヘッダーFrom（表示上の差出人。mail_info_master.from_address）。
	 *                      mail.accounts.<fromAddress>.username/password の検索キーにもなる。
	 * @param envelopeFrom  エンベロープフロム（配送エラーの返送先。mail_send_manage.envelope_from）
	 * @param toAddress     送信先メールアドレス
	 * @param subject       件名
	 * @param body          本文
	 * @return 送信したメールのMessage-ID（mail_send_manage.message_id反映用）
	 * @throws MessagingException 送信に失敗した場合（呼び出し元でcatchしてfail_send_countを更新する想定）
	 */
	public String send(String fromAddress, String envelopeFrom, String toAddress, String subject, String body)
			throws MessagingException {

		MailAccountsProperties.Account account;
		try {
			account = mailAccountsProperties.require(fromAddress);
		} catch (IllegalArgumentException e) {
			log.error("mail.accounts keys = {}", mailAccountsProperties.getAccounts().keySet());
			throw new MessagingException(
					"fromAddress=" + fromAddress
							+ " に対応するSMTP認証情報が未設定です（mail.accounts." + fromAddress + ".username / password）",
					e);
		}
		if (account.getUsername() == null || account.getPassword() == null) {
			throw new MessagingException(
					"fromAddress=" + fromAddress
							+ " のusername/passwordが未設定です（mail.accounts." + fromAddress + ".username / password）");
		}
		String smtpUsername = account.getUsername();
		String smtpPassword = account.getPassword();

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