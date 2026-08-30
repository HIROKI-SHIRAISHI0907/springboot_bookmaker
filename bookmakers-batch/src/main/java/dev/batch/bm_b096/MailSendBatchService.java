package dev.batch.bm_b096;

import java.util.UUID;

import org.springframework.stereotype.Service;

import dev.batch.repository.bm.MailSendBatchRepository;
import dev.batch.repository.master.MailInfoMasterBatchRepository;
import dev.common.entity.MailInfoMasterEntity;
import dev.common.entity.MailSendManagementEntity;
import dev.common.enums.MailNoticeEnum;
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
public class MailSendBatchService {

	/** メール情報マスタが見つからない等、システム都合で送信できない場合のメッセージ */
	private static final String SYSTEM_ERROR_MESSAGE = "システムエラーが起きました。システム管理者に連絡してください。";

	private final MailInfoMasterBatchRepository mailInfoMasterBatchRepository;
	private final MailSendBatchRepository mailSendBatchRepository;

	private static final String ENVELOPE_ADDRESS = "no-reply@sample.com";

	/**
	 * 送信先メールアドレスを直接指定してメール送信を登録する。
	 *
	 * @param mailId    メール情報マスタのメールID
	 * @param toAddress 送信先メールアドレス（画面入力値など、呼び出し元で確定済みのもの）
	 * @param bikou バッチ実行時の件数情報など
	 * @return 発行したメール送信キー（mail_send_management.mail_send_key）
	 */
	public void send(String mailId, String toAddress, String bikou) {
		// メール情報マスタに存在するか
		MailInfoMasterEntity mailInfo = mailInfoMasterBatchRepository.findMailByMailIdInfo(mailId);
		if (mailInfo == null) {
			log.error("メール情報マスタに該当データがありません。mailId={}", mailId);
			new RuntimeException(SYSTEM_ERROR_MESSAGE);
		};

		// メール送信キーを取得
		String mailSendKey = getMailSendKey();

		MailSendManagementEntity management = new MailSendManagementEntity();
		management.setMailSendKey(mailSendKey);
		management.setMessageId(null); // 実際の送信は別バッチのため、message_idはその際に更新する想定
		management.setToAddress(toAddress);
		management.setMailId(mailInfo.getMailId());
		management.setEnvelopeFrom(ENVELOPE_ADDRESS);
		management.setNotifyStatus(MailNoticeEnum.NOTIFY_STATUS_PENDING.getNoticeStatus());
		management.setBikou(bikou);
		management.setFailSendCount(0);

		try {
			mailSendBatchRepository.insert(management);
		} catch (Exception e) {
		}
	}

	/**
	 * メール送信キーを定義する
	 * すでに登録済のキーであれば再帰的に取得する
	 *
	 */
	private String getMailSendKey() {
		String mailSendKey = UUID.randomUUID().toString();
		return (!mailSendBatchRepository.findByMailSendKey(mailSendKey).isEmpty())
				? getMailSendKey()
				: mailSendKey;
	}

}