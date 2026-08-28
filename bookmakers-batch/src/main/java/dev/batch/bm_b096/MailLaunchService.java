package dev.batch.bm_b096;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import dev.batch.repository.bm.MailSendBatchRepository;
import dev.batch.repository.master.MailInfoMasterBatchRepository;
import dev.common.constant.MessageCdConst;
import dev.common.entity.MailInfoMasterEntity;
import dev.common.entity.MailSendManagementEntity;
import dev.common.enums.MailNoticeEnum;
import dev.common.logger.ManageLoggerComponent;
import dev.common.mail.MailSendComponent;
import lombok.extern.slf4j.Slf4j;

/**
 * MailLaunchServiceロジック
 * @author shiraishitoshio
 *
 */
@Component
@Slf4j
public class MailLaunchService {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = MailLaunchService.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();
	/** クラス名 */
	private static final String CLASS_NAME = MailLaunchService.class.getName();

	/** 実行モード */
	private static final String EXEC_MODE = "MAIL_LAUNCH";

	/** パスワード再設定URLのプレースホルダー */
	private static final String PASSWORD_RESET_URL_PLACEHOLDER = "{{PASSWORD_RESET_URL}}";

	/**
	 * パスワード再設定画面のベースURL（例: https://bm-stats-real.com/reset-password）。
	 * 環境ごとにapplication.properties/application.ymlで切り替える想定。
	 */
	@Value("${app.password-reset.base-url}")
	private String passwordResetBaseUrl;

	@Autowired
	private MailSendComponent mailSendComponent;

	@Autowired
	private MailSendBatchRepository mailSendBatchRepository;

	@Autowired
	private MailInfoMasterBatchRepository mailInfoMasterBatchRepository;

	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * メール送信バッチ実行
	 */
	public void execute() throws Exception {
		final String METHOD_NAME = "execute";
		this.manageLoggerComponent.init(EXEC_MODE, null);
		this.manageLoggerComponent.debugStartInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		log.info("passwordResetBaseUrl = {}", passwordResetBaseUrl);

		// 現在メール送信管理に登録されている通知ステータスが0のものを取得
		List<MailSendManagementEntity> noticeStatusPendingList = mailSendBatchRepository.findPendingNoticeStatus();

		this.manageLoggerComponent.debugInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG,
				"メール送信管理: " + noticeStatusPendingList);

		// メール送信して、ステータスをupdate
		for (MailSendManagementEntity entity : noticeStatusPendingList) {
			String mailSendKey = entity.getMailSendKey();
			String mailId = entity.getMailId();
			String envelopeFrom = entity.getEnvelopeFrom();
			String toAddress = entity.getToAddress();
			int failSendCount = entity.getFailSendCount();

			MailInfoMasterEntity mailIdKeyDTO = mailInfoMasterBatchRepository.findMailByMailIdInfo(mailId);
			if (mailIdKeyDTO == null) {
				// 送信失敗数をインクリメントして更新
				mailSendBatchRepository.updateFailSendCount(mailSendKey, failSendCount + 1);
				continue;
			}

			// Bodyに「{{PASSWORD_RESET_URL}}」が入っていれば文字列置き換え
			// URLに乗せるのはmailSendKeyのみ。「10分間有効」という期限そのものは
			// URLに埋め込まず、リンクを踏んだ側（/reset-password/validate）が
			// mail_send_manage.register_timeを基準にサーバー側で判定する想定。
			String mailBody = mailIdKeyDTO.getMailBody();
			if (mailBody != null && mailBody.contains(PASSWORD_RESET_URL_PLACEHOLDER)) {
				String encodedKey = URLEncoder.encode(mailSendKey, StandardCharsets.UTF_8);
				String passwordResetUrl = passwordResetBaseUrl + "?key=" + encodedKey;
				mailBody = mailBody.replace(PASSWORD_RESET_URL_PLACEHOLDER, passwordResetUrl);
			}

			// メール送信
			mailSendComponent.send(mailIdKeyDTO.getFromAddress(), envelopeFrom, toAddress,
					mailIdKeyDTO.getMailSubject(), mailIdKeyDTO.getMailBody());

			// 通知ステータスを1に更新
			mailSendBatchRepository.updateFromPendingToSendedStatus(
					mailSendKey, MailNoticeEnum.NOTIFY_STATUS_SENDED.getNoticeStatus());
		}

		// endLog
		this.manageLoggerComponent.debugEndInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		this.manageLoggerComponent.clear();
	}

}
