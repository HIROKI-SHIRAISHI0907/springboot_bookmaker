package dev.web.mail;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import dev.common.entity.MailInfoMasterEntity;
import dev.common.entity.MailSendManagementEntity;
import dev.common.enums.MailNoticeEnum;
import dev.web.jwt.JwtCurrentUserService;
import dev.web.jwt.JwtCurrentUserService.CurrentUser;
import dev.web.repository.bm.MailSendManagementRepository;
import dev.web.repository.master.MailInfoMasterRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * MailSendServiceクラス
 *
 * メールID（mail_info_master.mail_id）をキーに件名・本文を取得し、
 * メール送信管理テーブル（mail_send_management）へ送信予定として登録する。
 *
 * 実際のSMTP送信（Transport.send）は別バッチで行う想定のため、
 * このクラスは「送信内容の確定 ＋ 送信管理テーブルへのinsert」までを担う。
 *
 * @author shiraishitoshio
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailSendService {

	/** メール情報マスタが見つからない等、システム都合で送信できない場合のメッセージ */
	private static final String SYSTEM_ERROR_MESSAGE = "システムエラーが起きました。システム管理者に連絡してください。";

	private final MailInfoMasterRepository mailInfoMasterWebRepository;
	private final MailSendManagementRepository mailSendManagementRepository;
	private final JwtCurrentUserService jwtCurrentUserService;

	/**
	 * ログイン中ユーザー宛にメール送信を登録する。
	 * 送信先メールアドレスは、現在のHTTPリクエストのAuthorizationヘッダーをJWTとして
	 * 検証し、JwtCurrentUserService経由で解決する。
	 *
	 * @param mailId メール情報マスタのメールID
	 * @return 発行したメール送信キー（mail_send_management.mail_send_key）
	 */
	public String send(String mailId) {
		String toAddress = resolveCurrentUserEmail();
		return send(mailId, toAddress);
	}

	/**
	 * 送信先メールアドレスを直接指定してメール送信を登録する。
	 * パスワード再発行など、未ログイン状態で画面から入力されたメールアドレスを使うケースで使用する。
	 *
	 * @param mailId    メール情報マスタのメールID
	 * @param toAddress 送信先メールアドレス（画面入力値など、呼び出し元で確定済みのもの）
	 * @return 発行したメール送信キー（mail_send_management.mail_send_key）
	 */
	public String send(String mailId, String toAddress) {
		MailInfoMasterEntity mailInfo = mailInfoMasterWebRepository.findById(mailId)
				.orElseThrow(() -> {
					log.error("メール情報マスタに該当データがありません。mailId={}", mailId);
					return new RuntimeException(SYSTEM_ERROR_MESSAGE);
				});

		// メール送信キーを取得
		String mailSendKey = getMailSendKey();

		MailSendManagementEntity management = new MailSendManagementEntity();
		management.setMailSendKey(mailSendKey);
		management.setMessageId(null); // 実際の送信は別バッチのため、message_idはその際に更新する想定
		management.setToAddress(toAddress);
		management.setMailId(mailInfo.getMailId());
		management.setEnvelopeFrom(mailInfo.getFromAddress()); // ※エンベロープFromの決め方が別途あれば変更してください
		management.setNotifyStatus(MailNoticeEnum.NOTIFY_STATUS_PENDING.getNoticeStatus());
		management.setFailSendCount(0);
		mailSendManagementRepository.insert(management);
		return mailSendKey;
	}

	/**
	 * メール送信キーを定義する
	 * すでに登録済のキーであれば再帰的に取得する
	 *
	 */
	private String getMailSendKey() {
		String mailSendKey = UUID.randomUUID().toString();
		return (!mailSendManagementRepository.findByMailSendKey(mailSendKey).isEmpty())
				? getMailSendKey() : mailSendKey;
	}

	/**
	 * 現在のHTTPリクエストのAuthorizationヘッダーからJWTを検証し、
	 * ログイン中ユーザーのメールアドレスを取得する。
	 *
	 * JWTが無効・未ログインの場合はJwtCurrentUserService側でResponseStatusException
	 * （401 Unauthorized）が投げられるので、ここでは特にcatchしていません。
	 */
	private String resolveCurrentUserEmail() {
		HttpServletRequest request = currentHttpRequest();
		String authorizationHeader = request.getHeader("Authorization");
		CurrentUser currentUser = jwtCurrentUserService.resolve(authorizationHeader);
		return currentUser.getEmail();
	}

	private HttpServletRequest currentHttpRequest() {
		var attributes = RequestContextHolder.getRequestAttributes();
		if (!(attributes instanceof ServletRequestAttributes)) {
			log.error("HTTPリクエストコンテキスト外からsend(mailId)が呼び出されました。");
			throw new RuntimeException(SYSTEM_ERROR_MESSAGE);
		}
		ServletRequestAttributes servletAttributes = (ServletRequestAttributes) attributes;
		return servletAttributes.getRequest();
	}
}