package dev.batch.bm_b097;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dev.batch.common.AbstractJobBatchTemplate;

/**
 * データカテゴリ自動付与バッチ
 * @author shiraishitoshio
 *
 */
@Service("B097")
public class AutoAssignmentDataCategoryBatch extends AbstractJobBatchTemplate {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = AutoAssignmentDataCategoryBatch.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = AutoAssignmentDataCategoryBatch.class.getName();

	/** エラーコード（運用ルールに合わせて変更） */
	private static final String ERROR_CODE = "BM_B097_ERROR";

	/** バッチコード */
	private static final String BATCH_CODE = "B097";

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
	private AutoAssignmentDataCategoryService assignmentDataCategoryService;

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void doExecute(JobContext ctx) throws Exception {
		this.assignmentDataCategoryService.execute();
	}

}
