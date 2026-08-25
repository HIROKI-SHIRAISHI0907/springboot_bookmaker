package dev.web.mail;

import java.util.Properties;

import org.springframework.stereotype.Component;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

@Component
public class MailSendService {

    private static final String SMTP_HOST = "smtp.gmail.com";
    private static final String SMTP_PORT = "587";
    private static final String FROM_ADDRESS = "pepw9cj8@gmail.com";
    private static final String APP_PASSWORD = "sonic3717"; // Googleアプリパスワード

    /**
     * Gmailを使ってメールを送信する
     *
     * @param toAddress 宛先メールアドレス（引数で渡す）
     * @param subject   件名
     * @param body      本文
     */
    public void send(String toAddress, String subject, String body) {
        Properties props = new Properties();
        props.put("mail.smtp.host", SMTP_HOST);
        props.put("mail.smtp.port", SMTP_PORT);
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.ssl.trust", SMTP_HOST);

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(FROM_ADDRESS, APP_PASSWORD);
            }
        });

        try {
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(FROM_ADDRESS));
            message.addRecipient(Message.RecipientType.TO, new InternetAddress(toAddress));
            message.setSubject(subject, "UTF-8");
            message.setText(body, "UTF-8");

            Transport.send(message);
            System.out.println("メール送信成功: " + toAddress);
        } catch (MessagingException e) {
            throw new RuntimeException("メール送信に失敗しました", e);
        }
    }

    // 動作確認用
    public static void main(String[] args) {
        MailSendService service = new MailSendService();
        service.send("recipient@example.com", "テスト件名", "テスト本文です。");
    }
}
