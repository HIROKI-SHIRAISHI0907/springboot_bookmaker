package dev.batch.bm_b095;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.batch.constant.BatchStatusConstant;
import dev.batch.repository.bm.MailSendBatchRepository;
import dev.batch.repository.master.BatchJobExecRepository;
import dev.common.constant.MessageCdConst;
import dev.common.logger.ManageLoggerComponent;

/**
 * 不要データ削除サービスクラス
 * @author shiraishitoshio
 *
 */
@Component
public class DeleteDataService {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = DeleteDataService.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = DeleteDataService.class.getName();

	/** 実行モード */
	private static final String EXEC_MODE = "DELETE_DATA";

	/** 失敗回数 */
	private static final int FAIL_SEND_COUNT = 3;

	@Autowired
	private BatchJobExecRepository batchJobExecRepository;
	@Autowired
	private MailSendBatchRepository mailSendBatchRepository;
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * 不要データ削除処理を行う。以下のデータを削除する
	 * <p>
	 * 1. メール送信管理(mail_send_manage)のメール送信済もしくは送信失敗が3回以上
	 * (notify_status='1' or fail_send_count >= 3)
	 * 2. バッチ実行管理(batch_job_exec)のステータスがstatus=10 or 99
	 * 3. メール送信バッチECS制御用に格納したJSONファイル(aws-s3-mail) ある程度溜まっており通知済が全て入っていたら削除
	 * 4. aws-s3-delay-postpone-csvにある延期ファイルが3ヶ月以上経過していたら削除
	 * </p>
	 */
	public void execute() {
		final String METHOD_NAME = "execute";
		this.manageLoggerComponent.init(EXEC_MODE, null);
		this.manageLoggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		// 1. メール送信管理(mail_send_manage)のメール送信済もしくは送信失敗が3回以上
		deleteMailSendManageWithNoticeFinAndFailThreeTimesOver();
		// 2. バッチ実行管理(batch_job_exec)のステータスがstatus=10 or 99
		deleteBatchJobExecStatusSuccessAndFailed();
		// 3. メール送信バッチECS制御用に格納したJSONファイル(aws-s3-mail) 削除 TODO

		// endLog
		this.manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		this.manageLoggerComponent.clear();
	}

	/**
	 * メール送信管理(mail_send_manage)のメール送信済もしくは送信失敗が3回以上の削除を削除する
	 */
	private void deleteMailSendManageWithNoticeFinAndFailThreeTimesOver() {
		final String METHOD_NAME = "deleteMailSendManageWithNoticeFinAndFailThreeTimesOver";

		int result = 0;
		try {
			result = mailSendBatchRepository.deleteData(FAIL_SEND_COUNT);
		} catch (Exception e) {
			this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00099I_LOG, e,
					"エラー: " + e.getMessage());
		}

		this.manageLoggerComponent.debugInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME,
				MessageCdConst.MCD00099I_LOG,
				"削除件数 result=" + result);
	}

	/**
	 * バッチ実行管理(batch_job_exec)のステータスがstatus=10 or 99の削除を実行する
	 */
	private void deleteBatchJobExecStatusSuccessAndFailed() {
		final String METHOD_NAME = "deleteBatchJobExecStatusSuccessAndFailed";
		List<StatusPair> statusPairs = List.of(
		        new StatusPair(String.valueOf(BatchStatusConstant.STATUS_SUCCESS)),
		        new StatusPair(String.valueOf(BatchStatusConstant.STATUS_FAILED)));

		int result = 0;
		try {
			result = batchJobExecRepository.deleteData(statusPairs);
		} catch (Exception e) {
			this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00099I_LOG, e,
					"エラー: " + e.getMessage());
		}

		this.manageLoggerComponent.debugInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME,
				MessageCdConst.MCD00099I_LOG,
				"削除件数 result=" + result);
	}

}
