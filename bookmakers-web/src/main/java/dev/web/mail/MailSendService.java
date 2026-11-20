package dev.web.mail;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import dev.common.entity.MailInfoMasterEntity;
import dev.common.entity.MailSendManagementEntity;
import dev.common.enums.MailNoticeEnum;
import dev.common.mail.SmtpMailSender;
import dev.common.util.ProcessKeyUtil;
import dev.web.repository.bm.MailSendManagementRepository;
import dev.web.repository.master.MailInfoMasterRepository;
import dev.web.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * MailSendServiceクラス（2026-11-21 作り直し）
 *
 * メールID（mail_info_master.mail_id）をキーに件名・本文テンプレートを取得し、
 * プレースホルダーを置換して <b>その場で SMTP 送信</b> する。
 *
 * 旧方式（mail_send_manage へ「送信予定」登録 → S3 通知JSON → B096 が送信）は廃止。
 * mail_send_manage は以下の用途だけに残す。
 * <ul>
 *   <li>送信ログ（成功=1 / 失敗=9）</li>
 *   <li>パスワード再設定リンクのキー保管（PasswordResetService が 1→2 に更新して使い切る）</li>
 * </ul>
 * 古い行は B095 が1日で削除する。
 *
 * @author shiraishitoshio
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailSendService {

	/** メール情報マスタが見つからない等、システム都合で送信できない場合のメッセージ */
	private static final String SYSTEM_ERROR_MESSAGE = "システムエラーが起きました。システム管理者に連絡してください。";

	private static final String DUPLICATE_MESSAGE = "登録されているメールIDです。";

	private static final String NOT_FOUND_MESSAGE = "対象のメール情報が見つかりません。";

	private static final String IN_USE_MESSAGE = "このメール情報は送信管理で使用されているため削除できません。";

	private static final String WEB = "WEB";

	/** パスワード再設定URLのプレースホルダー（本文に含まれていれば送信キー付きURLに置換する） */
	private static final String PASSWORD_RESET_URL_PLACEHOLDER = "{{PASSWORD_RESET_URL}}";

	private final MailInfoMasterRepository mailInfoMasterRepository;
	private final MailSendManagementRepository mailSendManagementRepository;
	private final UserRepository userRepository;
	private final SmtpMailSender smtpMailSender;

	/** パスワード再設定画面のURL（?key=送信キー を付けて本文に埋め込む） */
	@Value("${app.password-reset.base-url:https://bm-stats-real.com/reset-password}")
	private String passwordResetBaseUrl;

	/**
	 * 登録済みユーザー宛にメールを送信する（パスワード再設定など）。
	 * 送信先は users テーブルに登録済みのメールアドレスである必要がある。
	 *
	 * @param mailId    メール情報マスタのメールID
	 * @param toAddress 送信先メールアドレス
	 * @return 結果（成功時は mailSendKey を返す）
	 */
	public MailSendResponse send(String mailId, String toAddress) {
		MailSendResponse response = new MailSendResponse();
		if (toAddress == null || toAddress.isBlank() || userRepository.findEmail(toAddress) == 0) {
			response.setResponseCode("404");
			response.setMessage("使用されていないメールアドレスです。");
			return response;
		}
		return sendNow(mailId, toAddress, null);
	}

	/**
	 * システム通知（承認フロー通知、取るべきリーグ候補通知など）を送信する。
	 * send() と違い、送信先が users に登録済みかどうかはチェックしない。
	 *
	 * @param mailId       メール情報マスタのメールID
	 * @param toAddress    送信先
	 * @param placeholders 件名・本文中の {{KEY}} を置換する値（null 可）
	 * @return 結果
	 */
	public MailSendResponse sendSystemNotification(String mailId, String toAddress,
			Map<String, String> placeholders) {
		return sendNow(mailId, toAddress, placeholders);
	}

	/**
	 * テンプレート取得 → 置換 → ログ行登録(0) → SMTP送信 → ログ更新(1 or 9)。
	 */
	private MailSendResponse sendNow(String mailId, String toAddress, Map<String, String> placeholders) {
		MailSendResponse response = new MailSendResponse();

		Optional<MailInfoMasterEntity> mailInfoOpt = mailInfoMasterRepository.findById(mailId);
		if (mailInfoOpt.isEmpty()) {
			log.error("メール情報マスタに該当データがありません。mailId={}", mailId);
			response.setResponseCode("500");
			response.setMessage(SYSTEM_ERROR_MESSAGE);
			return response;
		}
		MailInfoMasterEntity mailInfo = mailInfoOpt.get();

		String mailSendKey = ProcessKeyUtil.getMailSendKey();
		String subject = applyPlaceholders(mailInfo.getMailSubject(), placeholders);
		String body = mailInfo.getMailBody();
		if (body != null && body.contains(PASSWORD_RESET_URL_PLACEHOLDER)) {
			String url = passwordResetBaseUrl + "?key="
					+ URLEncoder.encode(mailSendKey, StandardCharsets.UTF_8);
			body = body.replace(PASSWORD_RESET_URL_PLACEHOLDER, url);
		}
		body = applyPlaceholders(body, placeholders);

		// 送信前にログ行(通知前=0)を登録（パスワード再設定のキーとしても使う）
		MailSendManagementEntity management = new MailSendManagementEntity();
		management.setMailSendKey(mailSendKey);
		management.setToAddress(toAddress);
		management.setMailId(mailId);
		management.setEnvelopeFrom(smtpMailSender.fromAddress());
		management.setNotifyStatus(MailNoticeEnum.NOTIFY_STATUS_PENDING.getNoticeStatus());
		management.setFailSendCount(0);
		management.setSourceInfo(WEB);
		management.setBikou(toBikou(placeholders));
		try {
			if (mailSendManagementRepository.insert(management) != 1) {
				response.setResponseCode("500");
				response.setMessage(SYSTEM_ERROR_MESSAGE);
				return response;
			}
		} catch (Exception e) {
			log.error("メール送信ログの登録に失敗しました。mailId={}", mailId, e);
			response.setResponseCode("500");
			response.setMessage(SYSTEM_ERROR_MESSAGE);
			return response;
		}

		try {
			String messageId = smtpMailSender.send(toAddress, subject, body);
			mailSendManagementRepository.updateMessageId(mailSendKey, messageId);
			mailSendManagementRepository.updateNotifyStatus(mailSendKey,
					MailNoticeEnum.NOTIFY_STATUS_SENDED.getNoticeStatus());
		} catch (Exception e) {
			log.error("メール送信に失敗しました。mailId={}, to={}", mailId, toAddress, e);
			try {
				mailSendManagementRepository.updateNotifyStatus(mailSendKey,
						MailNoticeEnum.NOTIFY_STATUS_FAILED.getNoticeStatus());
			} catch (Exception ignore) {
				// ログ更新失敗は無視
			}
			response.setResponseCode("500");
			response.setMessage("メールを送信できませんでした。時間をおいて再度お試しください。");
			return response;
		}

		response.setResponseCode("200");
		response.setMessage("メールを送信しました。");
		response.setMailSendKey(mailSendKey);
		return response;
	}

	/**
	 * text 中の {{KEY}}（旧テンプレート互換で （KEY） も）を値に置換し、
	 * 値が無いまま残った {{...}} は空文字にする。
	 */
	static String applyPlaceholders(String text, Map<String, String> placeholders) {
		if (text == null) {
			return "";
		}
		String result = text;
		if (placeholders != null) {
			for (Map.Entry<String, String> e : placeholders.entrySet()) {
				if (e.getKey() == null || e.getValue() == null) {
					continue;
				}
				result = result.replace("{{" + e.getKey() + "}}", e.getValue());
				result = result.replace("（" + e.getKey() + "）", e.getValue());
			}
		}
		return result.replaceAll("\\{\\{\\s*[^{}]*?\\s*\\}\\}", "");
	}

	/** placeholders を mail_send_manage.bikou 保存用の "KEY=VALUE,..." にする（ログ用） */
	private String toBikou(Map<String, String> placeholders) {
		if (placeholders == null || placeholders.isEmpty()) {
			return null;
		}
		String s = placeholders.entrySet().stream()
				.filter(e -> e.getKey() != null && e.getValue() != null)
				.map(e -> e.getKey() + "=" + e.getValue())
				.collect(Collectors.joining(","));
		return s.length() > 1000 ? s.substring(0, 1000) : s;
	}

	/**
	 * メール情報マスタにデータを登録する。
	 * メール送信管理のテーブルのメールIDに対応するマスタ情報である
	 *
	 * @param mailInfoMasterEntity メール情報マスタのEntity
	 * @return 登録結果
	 */
	public MailSendResponse regMailMaster(MailInfoMasterEntity mailInfoMasterEntity) {
		MailSendResponse response = new MailSendResponse();

		if (!mailInfoMasterRepository.findById(mailInfoMasterEntity.getMailId()).isEmpty()) {
			response.setResponseCode("404");
			response.setMessage(DUPLICATE_MESSAGE);
			return response;
		}

		try {
			int result = mailInfoMasterRepository.insert(mailInfoMasterEntity);
			if (result != 1) {
				response.setResponseCode("500");
				response.setMessage("メール送信管理に登録できませんでした。");
				return response;
			}
		} catch (Exception e) {
			response.setResponseCode("500");
			response.setMessage(SYSTEM_ERROR_MESSAGE);
			return response;
		}

		response.setResponseCode("200");
		response.setMessage("登録成功しました。");
		return response;
	}

	/**
	 * メール情報マスタにデータを更新する。
	 *
	 * @param mailInfoMasterEntity メール情報マスタのEntity
	 * @return 更新結果
	 */
	public MailSendResponse updMailMaster(MailInfoMasterEntity mailInfoMasterEntity) {
		MailSendResponse response = new MailSendResponse();

		try {
			int result = mailInfoMasterRepository.update(mailInfoMasterEntity);
			if (result != 1) {
				response.setResponseCode("500");
				response.setMessage("メール送信管理を更新できませんでした。");
				return response;
			}
		} catch (Exception e) {
			response.setResponseCode("500");
			response.setMessage(SYSTEM_ERROR_MESSAGE);
			return response;
		}

		response.setResponseCode("200");
		response.setMessage("更新成功しました。");
		return response;
	}

	/**
	 * メール情報マスタのデータを削除する。
	 * 既にメール送信管理（mail_send_management）で使用されているメールIDの場合、
	 * DB制約（外部キー等）によりDataIntegrityViolationExceptionが発生する構成であれば
	 * 409として扱い、削除不可であることを呼び出し元に伝える。
	 *
	 * @param mailId メールID
	 * @return 削除結果
	 */
	public MailSendResponse delMailMaster(String mailId) {
		MailSendResponse response = new MailSendResponse();

		if (mailInfoMasterRepository.findById(mailId).isEmpty()) {
			response.setResponseCode("404");
			response.setMessage(NOT_FOUND_MESSAGE);
			return response;
		}

		try {
			int result = mailInfoMasterRepository.delete(mailId);
			if (result != 1) {
				response.setResponseCode("500");
				response.setMessage("メール情報マスタを削除できませんでした。");
				return response;
			}
		} catch (DataIntegrityViolationException e) {
			response.setResponseCode("409");
			response.setMessage(IN_USE_MESSAGE);
			return response;
		} catch (Exception e) {
			response.setResponseCode("500");
			response.setMessage(SYSTEM_ERROR_MESSAGE);
			return response;
		}

		response.setResponseCode("200");
		response.setMessage("削除成功しました。");
		return response;
	}

	/**
	 * メール情報マスタデータを全件取得する。
	 * メール送信管理のテーブルのメールIDに対応するマスタ情報である
	 *
	 * @return メール情報マスタの全件リスト。取得失敗時はnull
	 */
	public List<MailInfoMasterEntity> getMailMaster() {
		try {
			return mailInfoMasterRepository.findAll();
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * メール情報マスタデータを1件取得する。
	 *
	 * @param mailId メールID
	 * @return メール情報マスタのEntity
	 */
	public MailInfoMasterEntity getMailMasterByMailId(String mailId) {
		if (mailId == null || mailId.isEmpty()) {
			log.error("mailIdがnullです。");
			new RuntimeException(SYSTEM_ERROR_MESSAGE);
		}

		MailInfoMasterEntity mailInfo = mailInfoMasterRepository.findById(mailId)
				.orElseThrow(() -> {
					log.error("メール情報マスタに該当データがありません。mailId={}", mailId);
					return new RuntimeException(SYSTEM_ERROR_MESSAGE);
				});
		return mailInfo;
	}
}
