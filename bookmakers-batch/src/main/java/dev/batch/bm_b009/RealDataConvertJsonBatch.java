package dev.batch.bm_b009;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dev.batch.common.AbstractJobBatchTemplate;

/**
 * JSON変換用
 * @author shiraishitoshio
 *
 */
@Service("B009")
public class RealDataConvertJsonBatch extends AbstractJobBatchTemplate {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = RealDataConvertJsonBatch.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = RealDataConvertJsonBatch.class.getName();

	/** エラーコード（運用ルールに合わせて変更） */
	private static final String ERROR_CODE = "BM_B009_ERROR";

	/** バッチコード */
	private static final String BATCH_CODE = "B009";

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

	/** RealDataConvertJsonStat */
	@Autowired
	private RealDataConvertJsonStat realDataConvertJsonStat;

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void doExecute(JobContext ctx) throws Exception {
		this.realDataConvertJsonStat.execute();
	}

}
