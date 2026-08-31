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
import dev.common.enums.BatchCodeToMailEnum;
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

	/** バッチコードのプレースホルダー */
	private static final String BATCH_NAME_PLACEHOLDER = "BATCH_NAME";

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
		log.info("passwordResetBaseUrl: {}", passwordResetBaseUrl);

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
			String bikou = entity.getBikou();

			MailInfoMasterEntity mailIdKeyDTO = mailInfoMasterBatchRepository.findMailByMailIdInfo(mailId);
			if (mailIdKeyDTO == null) {
				// 送信失敗数をインクリメントして更新
				mailSendBatchRepository.updateFailSendCount(mailSendKey, failSendCount + 1);
				continue;
			}

			this.manageLoggerComponent.debugInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG,
					"メール送信キー: " + mailSendKey);

			String mailSubject = applyBikouPlaceholders(mailIdKeyDTO.getMailSubject(), bikou);
			String mailBody = mailIdKeyDTO.getMailBody();

			if (mailBody != null && mailBody.contains(PASSWORD_RESET_URL_PLACEHOLDER)) {
			    String encodedKey = URLEncoder.encode(mailSendKey, StandardCharsets.UTF_8);
			    String passwordResetUrl = passwordResetBaseUrl + "?key=" + encodedKey;
			    mailBody = mailBody.replace(PASSWORD_RESET_URL_PLACEHOLDER, passwordResetUrl);
			}

			mailBody = applyBikouPlaceholders(mailBody, bikou);

			// メール送信
			try {
				mailSendComponent.send(mailIdKeyDTO.getFromAddress(), envelopeFrom, toAddress,
						mailSubject, mailBody);
			} catch (Exception e) {
				// 送信失敗数をインクリメントして更新
				mailSendBatchRepository.updateFailSendCount(mailSendKey, failSendCount + 1);
				continue;
			}

			// 通知ステータスを1に更新
			mailSendBatchRepository.updateFromPendingToSendedStatus(
					mailSendKey, MailNoticeEnum.NOTIFY_STATUS_SENDED.getNoticeStatus());
		}

		// endLog
		this.manageLoggerComponent.debugEndInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		this.manageLoggerComponent.clear();
	}

	/**
	 * mail_send_manage.bikouを "KEY1=VALUE1,KEY2=VALUE2" 形式のkey=valueペアとして解釈し、
	 * text中に含まれる "{{KEY1}}" のようなプレースホルダーをVALUE1に置換する。
	 * 件名・本文どちらに対しても同じロジックで使える汎用メソッド。
	 */
	private String applyBikouPlaceholders(String text, String bikou) {
	    if (text == null || bikou == null || bikou.isBlank()) {
	        return text;
	    }
	    String result = text;
	    for (String pair : bikou.split(",")) {
	        String[] kv = pair.split("=", 2);
	        if (kv.length != 2) {
	            continue;
	        }
	        String key = kv[0].trim();
	        String value = kv[1].trim();
	        if (key.isEmpty()) {
	            continue;
	        }
	        // valueを解決
	        if (BATCH_NAME_PLACEHOLDER.equals(key))
	        	value = BatchCodeToMailEnum.resolveBatchName(value);
	        result = result.replace("（" + key + "）", value);
	        result = result.replace("{{" + key + "}}", value);
	    }
	    return result;
	}
}
