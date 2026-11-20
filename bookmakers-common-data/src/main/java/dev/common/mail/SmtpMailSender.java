package dev.common.mail;

import java.util.Properties;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;

/**
 * SmtpMailSenderクラス（2026-11-21 メール処理作り直し）
 *
 * Gmail SMTP へ「その場で」1通送るだけの最小の送信部品。
 * 旧方式（mail_send_manage へ登録 → S3 の通知JSON → B096 が送信）は廃止した。
 *
 * 認証情報は application.yml の mail.accounts.system.username / password
 * （実体は環境変数で渡すこと。例: ${MAIL_SYSTEM_USERNAME} / ${MAIL_SYSTEM_PASSWORD}）。
 * 送信元(From)も同じ system アカウントのアドレスを使う
 * （Gmail は認証アカウント以外の From を基本的に許可しないため）。
 *
 * @author shiraishitoshio
 */
@Slf4j
@Component
public class SmtpMailSender {

	@Value("${mail.smtp.host:smtp.gmail.com}")
	private String smtpHost;

	@Value("${mail.smtp.port:587}")
	private String smtpPort;

	@Value("${mail.smtp.timeout-ms:15000}")
	private String timeoutMs;

	private final MailAccountsProperties mailAccountsProperties;

	public SmtpMailSender(MailAccountsProperties mailAccountsProperties) {
		this.mailAccountsProperties = mailAccountsProperties;
	}

	/** 送信元（= SMTP 認証アカウント）のメールアドレス */
	public String fromAddress() {
		MailAccountsProperties.Account account = mailAccountsProperties.getAccounts().get("system");
		return account == null ? null : account.getUsername();
	}

	/**
	 * テキストメールを1通送信する。
	 *
	 * @param toAddress 送信先
	 * @param subject   件名
	 * @param body      本文（プレーンテキスト）
	 * @return Message-ID
	 * @throws MessagingException 送信失敗時（認証情報未設定を含む）
	 */
	public String send(String toAddress, String subject, String body) throws MessagingException {
		MailAccountsProperties.Account account = mailAccountsProperties.getAccounts().get("system");
		if (account == null || isBlank(account.getUsername()) || isBlank(account.getPassword())) {
			throw new MessagingException("SMTP認証情報(mail.accounts.system)が未設定です");
		}
		if (isBlank(toAddress)) {
			throw new MessagingException("送信先が未設定です");
		}
		final String username = account.getUsername();
		final String password = account.getPassword();

		Properties props = new Properties();
		props.put("mail.smtp.host", smtpHost);
		props.put("mail.smtp.port", smtpPort);
		props.put("mail.smtp.auth", "true");
		props.put("mail.smtp.starttls.enable", "true");
		props.put("mail.smtp.starttls.required", "true");
		props.put("mail.smtp.ssl.trust", smtpHost);
		props.put("mail.smtp.connectiontimeout", timeoutMs);
		props.put("mail.smtp.timeout", timeoutMs);
		props.put("mail.smtp.writetimeout", timeoutMs);

		Session session = Session.getInstance(props, new Authenticator() {
			@Override
			protected PasswordAuthentication getPasswordAuthentication() {
				return new PasswordAuthentication(username, password);
			}
		});

		MimeMessage message = new MimeMessage(session);
		message.setFrom(new InternetAddress(username));
		message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toAddress));
		message.setSubject(subject == null ? "" : subject, "UTF-8");
		message.setText(body == null ? "" : body, "UTF-8");
		Transport.send(message);
		String messageId = message.getMessageID();
		log.info("mail sent. to={}, subject={}, messageId={}", toAddress, subject, messageId);
		return messageId;
	}

	private static boolean isBlank(String s) {
		return s == null || s.isBlank();
	}
}
