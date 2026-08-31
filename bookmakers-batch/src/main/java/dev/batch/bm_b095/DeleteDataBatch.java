package dev.batch.bm_b095;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dev.batch.bm_b096.MailLaunchService;
import dev.batch.common.AbstractJobBatchTemplate;

/**
 * 不要データ削除バッチ
 * @author shiraishitoshio
 *
 */
@Service("B095")
public class DeleteDataBatch extends AbstractJobBatchTemplate {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = DeleteDataBatch.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = DeleteDataBatch.class.getName();

	/** エラーコード（運用ルールに合わせて変更） */
	private static final String ERROR_CODE = "BM_B096_ERROR";

	/** バッチコード */
	private static final String BATCH_CODE = "B096";

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

	/** MailLaunchService */
	@Autowired
	private MailLaunchService mailLaunchService;

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void doExecute(JobContext ctx) throws Exception {
		this.mailLaunchService.execute();
	}

}
