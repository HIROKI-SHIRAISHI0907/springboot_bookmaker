package dev.batch.bm_b094;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dev.batch.common.AbstractJobBatchTemplate;

/**
 * 特定の資材や時間になったらメール送信管理に情報を飛ばすバッチ
 * @author shiraishitoshio
 *
 */
@Service("B094")
public class MailSendSomethingBatch extends AbstractJobBatchTemplate {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = MailSendSomethingBatch.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = MailSendSomethingBatch.class.getName();

	/** エラーコード（運用ルールに合わせて変更） */
	private static final String ERROR_CODE = "BM_B094_ERROR";

	/** バッチコード */
	private static final String BATCH_CODE = "B094";

	/** オーバーライド */
	@Override
	protected String batchCode() {
		return BATCH_CODE;
	}

	@Override
	protected String errorCode() {
		return ERROR_CODE;
	}

	@Override
	protected String projectName() {
		return PROJECT_NAME;
	}

	@Override
	protected String className() {
		return CLASS_NAME;
	}

	/** MailSendSomethingService */
	@Autowired
	private MailSendSomethingService mailSendSomethingService;

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void doExecute(JobContext ctx) throws Exception {
		this.mailSendSomethingService.execute();
	}

}
