package dev.batch.bm_b096;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.batch.repository.bm.MailSendBatchRepository;
import dev.batch.repository.master.MailInfoMasterBatchRepository;
import dev.common.constant.MessageCdConst;
import dev.common.entity.MailInfoMasterEntity;
import dev.common.entity.MailSendManagementEntity;
import dev.common.enums.MailNoticeEnum;
import dev.common.logger.ManageLoggerComponent;

/**
 * MailLaunchServiceロジック
 * @author shiraishitoshio
 *
 */
@Component
public class MailLaunchService {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = MailLaunchService.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();
	/** クラス名 */
	private static final String CLASS_NAME = MailLaunchService.class.getName();

	/** 実行モード */
	private static final String EXEC_MODE = "MAIL_LAUNCH";

	private static final String ROUND = "ラウンド";

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

		// 現在メール送信管理に登録されている通知ステータスが0のものを取得
		List<MailSendManagementEntity> noticeStatusPendingList
			= mailSendBatchRepository.findPendingNoticeStatus();

		this.manageLoggerComponent.debugInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME
			    ,MessageCdConst.MCD00099I_LOG, "メール送信管理: " + noticeStatusPendingList);

		// メール送信して、ステータスをupdate
		for (MailSendManagementEntity entity : noticeStatusPendingList) {
			String mailSendKey = entity.getMailSendKey();
		    String mailId = entity.getMailId();
		    String toAddress = entity.getToAddress();
		    int failSendCount = entity.getFailSendCount();

		    MailInfoMasterEntity mailIdKeyDTO
		    	= mailInfoMasterBatchRepository.findMailByMailIdInfo(mailId);
		    if (mailIdKeyDTO == null) {
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

}
